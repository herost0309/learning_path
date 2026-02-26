# Redis 密码轮换问题分析与解决方案

## 问题发现

### 1. 依赖冲突问题

在 `pom.xml` 中同时引入了两个依赖：

```xml
<!-- 问题依赖 -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>${redisson.version}</version>
</dependency>

<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson</artifactId>
    <version>${redisson.version}</version>
</dependency>
```

**`redisson-spring-boot-starter` 会：**
- 自动创建一个静态的 `RedissonClient` Bean
- 从 `application.yml` 的 `spring.redis.*` 或 `spring.data.redis.*` 读取配置
- 在应用启动时固定密码，**无法动态更新**

### 2. 密码轮换后会发生什么？

```
┌─────────────────────────────────────────────────────────────────────┐
│                    密码轮换时序图                                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  T0: 应用启动                                                        │
│      └── RedissonClient (password=XXX) 创建完成                      │
│                                                                     │
│  T1: AWS Secrets Manager 执行密码轮换                                 │
│      └── Secret 中的 password 更新为 YYY                             │
│      └── ElastiCache Redis 密码同步更新为 YYY                         │
│                                                                     │
│  T2: 应用继续使用旧客户端                                              │
│      └── RedissonClient 仍持有 password=XXX                         │
│      └── ❌ 所有 Redis 操作失败: NOAUTH / WRONGPASS                  │
│                                                                     │
│  T3: 定时任务检测到新密码版本                                          │
│      └── DynamicRedissonClient.refreshClient() 被调用               │
│      └── 但是 starter 创建的 Bean 无法被替换！                        │
│      └── ❌ 连接持续失败                                              │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 3. 根本原因

| 组件 | 问题 |
|------|------|
| `redisson-spring-boot-starter` | 创建的是单例 Bean，密码在启动时固定 |
| Spring Bean 生命周期 | Bean 一旦创建，无法动态替换内部配置 |
| Redisson 连接池 | 连接池中的连接使用旧密码，无法自动更新 |

## 解决方案

### 方案：移除 Starter，使用自定义 DynamicRedissonClient

```xml
<!-- 移除这个依赖 -->
<!-- <dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
</dependency> -->

<!-- 只保留核心依赖 -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson</artifactId>
    <version>${redisson.version}</version>
</dependency>
```

### 架构对比

#### ❌ 错误架构（使用 Starter）

```
┌─────────────────────────────────────────────────────────────┐
│                     Spring Container                         │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  RedissonClient (Singleton Bean)                       │  │
│  │  ┌─────────────────────────────────────────────────┐   │  │
│  │  │  Config: password=XXX (启动时固定，无法更改)      │   │  │
│  │  └─────────────────────────────────────────────────┘   │  │
│  └───────────────────────────────────────────────────────┘  │
│                          ▲                                   │
│                          │ @Autowired                        │
│                          │                                   │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  YourService                                           │  │
│  │  - 使用静态密码的客户端                                  │  │
│  │  - ❌ 密码轮换后连接失败                                 │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

#### ✅ 正确架构（使用 DynamicRedissonClient）

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Spring Container                              │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  DynamicRedissonClient (Singleton Bean)                         │ │
│  │  ┌──────────────────────────────────────────────────────────┐  │ │
│  │  │  AtomicReference<RedissonClient> currentClient            │  │ │
│  │  │  ┌────────────────────────────────────────────────────┐   │  │ │
│  │  │  │  RedissonClient v1 (password=XXX)  [SHUTDOWN]      │   │  │ │
│  │  │  ├────────────────────────────────────────────────────┤   │  │ │
│  │  │  │  RedissonClient v2 (password=YYY)  [ACTIVE] ✓      │   │  │ │
│  │  │  └────────────────────────────────────────────────────┘   │  │ │
│  │  └──────────────────────────────────────────────────────────┘  │ │
│  │                                                                │ │
│  │  Methods:                                                      │ │
│  │  - getClient() → 返回当前活跃的客户端                            │ │
│  │  - refreshClient() → 关闭旧客户端，创建新客户端                   │ │
│  │  - executeWithRetry() → 认证失败时自动刷新并重试                  │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                          ▲                                           │
│                          │ @Autowired                                │
│                          │                                           │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  RedissonTemplate                                               │ │
│  │  - 通过 dynamicClient.getClient() 获取当前客户端                  │ │
│  │  - ✅ 始终使用最新密码                                           │ │
│  └────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

## 密码轮换处理流程

### 1. 主动检测机制

```java
@Scheduled(fixedDelayString = "${redis.secret-refresh-interval-seconds:30}000")
public void checkSecretRotation() {
    if (secretsManagerService.isNewSecretAvailable()) {
        log.info("New secret version detected, refreshing Redisson client");
        dynamicRedissonClient.refreshClient();
    }
}
```

### 2. 被动重试机制

```java
public <T> T executeWithRetry(RedissonOperation<T> operation, int attempt) {
    try {
        return operation.execute(getClient());
    } catch (Exception e) {
        if (isAuthenticationError(e) && attempt < maxRetryAttempts) {
            // 检测到认证错误，自动刷新客户端
            refreshClient();
            // 重试操作
            return executeWithRetry(operation, attempt + 1);
        }
        throw new RedisConnectionException("Redis operation failed", e);
    }
}
```

### 3. 完整流程图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        密码轮换处理流程                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────────┐                                                   │
│  │ 密码轮换触发       │                                                   │
│  │ (AWS Lambda)      │                                                   │
│  └────────┬─────────┘                                                   │
│           │                                                             │
│           ▼                                                             │
│  ┌──────────────────┐     ┌──────────────────┐                         │
│  │ Secrets Manager  │────▶│ ElastiCache      │                         │
│  │ password: YYY    │     │ AUTH token: YYY  │                         │
│  └────────┬─────────┘     └──────────────────┘                         │
│           │                                                             │
│           ▼                                                             │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                    应用层处理                                      │  │
│  │                                                                  │  │
│  │  ┌─────────────────┐         ┌─────────────────┐                │  │
│  │  │ 定时检测        │──OR────▶│ 操作时检测       │                │  │
│  │  │ (每30秒)        │         │ (NOAUTH错误)     │                │  │
│  │  └────────┬────────┘         └────────┬────────┘                │  │
│  │           │                           │                          │  │
│  │           └───────────┬───────────────┘                          │  │
│  │                       ▼                                          │  │
│  │           ┌─────────────────────────────┐                        │  │
│  │           │ refreshClient()             │                        │  │
│  │           │ 1. 获取新密码 (YYY)          │                        │  │
│  │           │ 2. 关闭旧客户端              │                        │  │
│  │           │ 3. 创建新客户端              │                        │  │
│  │           └─────────────────────────────┘                        │  │
│  │                       │                                          │  │
│  │                       ▼                                          │  │
│  │           ┌─────────────────────────────┐                        │  │
│  │           │ ✅ 连接恢复                  │                        │  │
│  │           │ 使用新密码 YYY 成功连接      │                        │  │
│  │           └─────────────────────────────┘                        │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

## 关键设计要点

### 1. 为什么不能直接修改 RedissonClient 的密码？

```java
// ❌ 这是不可行的！
// RedissonClient 是基于 Config 创建的，Config 在创建后不可变
// 连接池中的所有连接都使用创建时的密码

RedissonClient client = Redisson.create(config);
// client 没有提供 setPassword() 方法
// 连接已经建立，无法动态更改密码
```

### 2. 为什么需要完全关闭旧客户端？

```java
// ✅ 正确做法
public void refreshClient() {
    // 1. 获取新密码
    RedisSecret newSecret = secretsManagerService.refreshSecret();

    // 2. 关闭旧客户端（释放连接池资源）
    closeCurrentClient();

    // 3. 用新密码创建新客户端
    Config newConfig = createConfigWithSecret(newSecret);
    RedissonClient newClient = Redisson.create(newConfig);

    // 4. 原子替换引用
    currentClient.set(newClient);
}
```

### 3. 线程安全保证

```java
// 使用 ReentrantLock 保证线程安全
private final ReentrantLock clientLock = new ReentrantLock();

public void refreshClient() {
    clientLock.lock();
    try {
        // 刷新操作是原子的
        closeCurrentClient();
        createClient(newSecret);
    } finally {
        clientLock.unlock();
    }
}
```

### 4. 优雅关闭

```java
private void closeCurrentClient() {
    RedissonClient client = currentClient.getAndSet(null);
    if (client != null && !client.isShutdown()) {
        client.shutdown();  // 优雅关闭，等待正在执行的操作完成
    }
}
```

## 总结

| 场景 | 使用 Starter | 使用 DynamicRedissonClient |
|------|-------------|---------------------------|
| 密码固定 | ✅ 可用 | ✅ 可用 |
| 密码轮换 | ❌ 连接失败 | ✅ 自动恢复 |
| 配置复杂度 | 简单 | 中等 |
| 运维成本 | 需要重启应用 | 无需重启 |

**结论：** 对于需要密码轮换的场景，**必须**使用自定义的 `DynamicRedissonClient`，并移除 `redisson-spring-boot-starter` 依赖。
