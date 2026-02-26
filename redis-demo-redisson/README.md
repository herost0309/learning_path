# Redis Redisson Demo with AWS Secrets Manager

AWS ElastiCache Redis 连接项目，使用 Redisson 客户端，支持通过 AWS Secrets Manager 进行动态密码轮换。

## 项目特性

- **多种部署模式支持**: Standalone / Cluster / Sentinel
- **动态密码轮换**: 支持 AWS Secrets Manager 密码轮换
- **自动重连**: 认证失败时自动获取新密码并重连
- **Secret 缓存**: 减少对 Secrets Manager 的调用
- **周期性检查**: 定期检查密码版本更新
- **分布式锁**: Redisson 提供的分布式锁支持
- **健康检查**: 集成 Spring Boot Actuator

---

## Redis 连接模式

### 1. Standalone 模式（单节点）

**适用场景:**
- 开发/测试环境
- 单个 Redis 节点
- Redis with Replicas (使用 Primary endpoint)

**Secret JSON 格式:**
```json
{
  "mode": "standalone",
  "host": "my-redis.xxxxx.use1.cache.amazonaws.com",
  "port": 6379,
  "username": "default",
  "password": "your-password",
  "clusterName": "my-redis"
}
```

**URL 格式:**
```
rediss://my-redis.xxxxx.use1.cache.amazonaws.com:6379
```

---

### 2. Cluster 模式（集群模式）

**适用场景:**
- AWS ElastiCache Cluster Mode Enabled
- 自建 Redis Cluster
- 生产环境需要水平扩展

#### 方式 A: 使用配置端点（推荐）

AWS ElastiCache 会自动发现所有集群节点。

**Secret JSON 格式:**
```json
{
  "mode": "cluster",
  "configurationEndpoint": "my-redis-cluster.xxxxx.clustercfg.use1.cache.amazonaws.com",
  "port": 6379,
  "username": "default",
  "password": "your-password",
  "clusterName": "my-redis-cluster"
}
```

**URL 格式:**
```
rediss://my-redis-cluster.xxxxx.clustercfg.use1.cache.amazonaws.com:6379
```

#### 方式 B: 显式指定所有节点

**Secret JSON 格式:**
```json
{
  "mode": "cluster",
  "nodes": [
    {"host": "my-redis-0001-001.xxxxx.use1.cache.amazonaws.com", "port": 6379},
    {"host": "my-redis-0001-002.xxxxx.use1.cache.amazonaws.com", "port": 6379},
    {"host": "my-redis-0002-001.xxxxx.use1.cache.amazonaws.com", "port": 6379},
    {"host": "my-redis-0002-002.xxxxx.use1.cache.amazonaws.com", "port": 6379},
    {"host": "my-redis-0003-001.xxxxx.use1.cache.amazonaws.com", "port": 6379},
    {"host": "my-redis-0003-002.xxxxx.use1.cache.amazonaws.com", "port": 6379}
  ],
  "username": "default",
  "password": "your-password",
  "clusterName": "my-redis-cluster"
}
```

**节点 URL 格式:**
```
rediss://my-redis-0001-001.xxxxx.use1.cache.amazonaws.com:6379
rediss://my-redis-0001-002.xxxxx.use1.cache.amazonaws.com:6379
...
```

---

### 3. Sentinel 模式（哨兵模式）

**适用场景:**
- 自建 Redis + Sentinel 高可用
- 本地数据中心部署

**Secret JSON 格式:**
```json
{
  "mode": "sentinel",
  "masterName": "mymaster",
  "sentinelAddresses": [
    "rediss://sentinel1.example.com:26379",
    "rediss://sentinel2.example.com:26379",
    "rediss://sentinel3.example.com:26379"
  ],
  "username": "default",
  "password": "your-password",
  "clusterName": "my-redis-sentinel"
}
```

---

## URL 协议说明

| 协议 | 说明 | 使用场景 |
|------|------|----------|
| `redis://` | 非加密连接 | 开发环境、内网 |
| `rediss://` | TLS/SSL 加密连接 | **AWS ElastiCache（推荐）** |

---

## 快速开始

### 1. 前置条件

- JDK 21+
- Maven 3.9+
- AWS 账户 (ElastiCache Redis + Secrets Manager)
- 已配置 AWS 凭证

### 2. 创建 AWS 资源

#### 创建 ElastiCache Redis 集群（Cluster Mode）

```bash
# 创建 Redis 集群
aws elasticache create-replication-group \
    --replication-group-id my-redis-cluster \
    --replication-group-description "Redis cluster with password rotation" \
    --engine redis \
    --engine-version 7.0 \
    --cache-node-type cache.r6g.large \
    --num-node-groups 3 \
    --replicas-per-node-group 1 \
    --automatic-failover-enabled \
    --multi-az-enabled \
    --at-rest-encryption-enabled \
    --transit-encryption-enabled \
    --auth-token "your-initial-password" \
    --cache-subnet-group-name my-subnet-group \
    --security-group-ids sg-xxxxxxxx

# 获取集群配置端点
aws elasticache describe-replication-groups \
    --replication-group-id my-redis-cluster \
    --query 'ReplicationGroups[0].ConfigurationEndpoint'
```

#### 创建 Secrets Manager Secret

**Standalone 模式:**
```bash
aws secretsmanager create-secret \
    --name prod/elasticache/redis/auth \
    --secret-string '{
        "mode": "standalone",
        "host": "my-redis.xxxxx.use1.cache.amazonaws.com",
        "port": 6379,
        "username": "default",
        "password": "your-password",
        "clusterName": "my-redis"
    }'
```

**Cluster 模式:**
```bash
aws secretsmanager create-secret \
    --name prod/elasticache/redis/auth \
    --secret-string '{
        "mode": "cluster",
        "configurationEndpoint": "my-redis-cluster.xxxxx.clustercfg.use1.cache.amazonaws.com",
        "port": 6379,
        "username": "default",
        "password": "your-password",
        "clusterName": "my-redis-cluster"
    }'
```

### 3. 配置环境变量

```bash
export AWS_REGION=us-east-1
export REDIS_SECRET_NAME=prod/elasticache/redis/auth
```

### 4. 构建和运行

```bash
# 构建
mvn clean package -DskipTests

# 运行
java -jar target/redis-redisson-demo-1.0.0.jar
```

### 5. 测试 API

访问 Swagger UI: http://localhost:8080/swagger-ui.html

```bash
# 健康检查
curl http://localhost:8080/api/cache/health

# 设置值
curl -X POST http://localhost:8080/api/cache/mykey \
    -H "Content-Type: application/json" \
    -d '{"value": "hello", "ttlMinutes": 30}'

# 获取值
curl http://localhost:8080/api/cache/mykey

# 哈希操作
curl -X POST http://localhost:8080/api/cache/hash/user:123 \
    -H "Content-Type: application/json" \
    -d '{"field": "name", "value": "John"}'

# 列表操作
curl -X POST http://localhost:8080/api/cache/list/mylist/push \
    -H "Content-Type: application/json" \
    -d '{"values": "item1,item2,item3"}'

# 强制重连（密码轮换后）
curl -X POST http://localhost:8080/api/cache/reconnect
```

---

## 密码轮换配置

### 部署 Lambda 轮换函数

```bash
# 打包 Lambda
cd lambda
zip -r rotation.zip rotation_function.py

# 创建 Lambda 函数
aws lambda create-function \
    --function-name redis-password-rotation \
    --runtime python3.11 \
    --role arn:aws:iam::123456789012:role/lambda-secrets-role \
    --handler rotation_function.lambda_handler \
    --zip-file fileb://rotation.zip \
    --environment Variables={REDIS_HOST=my-redis-cluster.xxxxx.use1.cache.amazonaws.com}

# 配置轮换（每30天）
aws secretsmanager rotate-secret \
    --secret-id prod/elasticache/redis/auth \
    --rotation-lambda-arn arn:aws:lambda:us-east-1:123456789012:function:redis-password-rotation \
    --rotation-rules AutomaticallyAfterDays=30
```

---

## 应用配置 (application.yml)

```yaml
# Redis 配置
redis:
  secret-name: ${REDIS_SECRET_NAME:prod/elasticache/redis/auth}
  cache-secret: true
  secret-cache-ttl-seconds: 60
  auto-reconnect: true
  max-retry-attempts: 3
  enable-secret-refresh-check: true
  secret-refresh-interval-seconds: 30
  # Redisson 连接池配置
  connection-pool-size: 64
  connection-minimum-idle-size: 10
  idle-connection-timeout: 10000
  connect-timeout: 10000
  timeout: 3000
  retry-attempts: 3
  retry-interval: 1500
  ping-connection-interval: 30000
  keep-alive: true
  tcp-no-delay: true

# AWS 配置
aws:
  region: ${AWS_REGION:us-east-1}

# Server 配置
server:
  port: 8080
```

---

## 代码使用示例

### 1. 使用 RedissonTemplate

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final RedissonTemplate redissonTemplate;

    // 简单的 key-value 操作
    public void cacheUser(String userId, User user) {
        redissonTemplate.setEx("user:" + userId, user, Duration.ofMinutes(30));
    }

    public User getCachedUser(String userId) {
        return redissonTemplate.get("user:" + userId);
    }

    // 哈希操作
    public void updateUserProfile(String userId, Map<String, Object> profile) {
        redissonTemplate.hSetAll("user:profile:" + userId, profile);
    }

    public Map<String, Object> getUserProfile(String userId) {
        return redissonTemplate.hGetAll("user:profile:" + userId);
    }

    // 分布式锁
    public void performWithLock(String userId) {
        redissonTemplate.executeWithLock(
            "lock:user:" + userId,
            Duration.ofSeconds(5),   // 等待时间
            Duration.ofSeconds(30),  // 锁持有时间
            () -> {
                // 临界区操作
                return null;
            }
        );
    }
}
```

### 2. 直接使用 RedissonClient

```java
@Service
@RequiredArgsConstructor
public class AdvancedRedisService {

    private final DynamicRedissonClient dynamicClient;

    // 使用 RMap
    public void updateMap(String key, String field, String value) {
        RMap<String, String> map = dynamicClient.getClient().getMap(key);
        map.put(field, value);
    }

    // 使用 RScoredSortedSet (排行榜)
    public void updateLeaderboard(String userId, double score) {
        RScoredSortedSet<String> leaderboard =
            dynamicClient.getClient().getScoredSortedSet("leaderboard");
        leaderboard.add(score, userId);
    }

    // 使用 RLock
    public boolean tryDistributedLock(String lockKey, Runnable task) {
        RLock lock = dynamicClient.getClient().getLock(lockKey);
        try {
            if (lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                task.run();
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return false;
    }
}
```

---

## 项目结构

```
redis-demo-redisson/
├── pom.xml
├── README.md
├── lambda/
│   └── rotation_function.py
└── src/main/
    ├── java/com/example/redis/
    │   ├── RedisRedissonDemoApplication.java
    │   ├── config/
    │   │   ├── GlobalExceptionHandler.java
    │   │   ├── RedisHealthIndicator.java
    │   │   ├── RedisProperties.java
    │   │   └── RedissonConfig.java
    │   ├── controller/
    │   │   └── CacheController.java
    │   ├── exception/
    │   │   ├── RedisConnectionException.java
    │   │   └── SecretRetrievalException.java
    │   ├── model/
    │   │   └── RedisSecret.java
    │   └── service/
    │       ├── CacheService.java
    │       ├── DynamicRedissonClient.java
    │       ├── RedissonTemplate.java
    │       └── SecretsManagerService.java
    └── resources/
        └── application.yml
```

---

## 监控指标

应用暴露以下 Micrometer 指标：

| 指标 | 说明 |
|------|------|
| `redis.reconnect.count` | 重连次数 |
| `redis.auth.failure.count` | 认证失败次数 |
| `redis.operation.timer` | 操作耗时 |

---

## 注意事项

1. **TLS/SSL**: AWS ElastiCache 必须启用传输加密，使用 `rediss://` 协议
2. **IAM 权限**: 应用需要 `secretsmanager:GetSecretValue` 权限
3. **密码轮换时机**: 建议在低流量时段执行
4. **Cluster Mode**: 建议使用配置端点自动发现节点
5. **测试**: 生产部署前充分测试密码轮换流程

---

## ⚠️ 重要：为什么不使用 redisson-spring-boot-starter

### 问题说明

**不要使用 `redisson-spring-boot-starter` 依赖！** 它会创建静态的 `RedissonClient` Bean，密码在应用启动时固定，无法支持动态密码轮换。

```xml
<!-- ❌ 不要使用这个依赖 -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
</dependency>
```

### 为什么会有问题？

| 特性 | redisson-spring-boot-starter | DynamicRedissonClient (本项目) |
|------|------------------------------|-------------------------------|
| 密码固定 | ✅ 可用 | ✅ 可用 |
| 密码轮换 | ❌ 连接失败，需要重启应用 | ✅ 自动检测并刷新 |
| Bean 类型 | Spring 单例，启动时创建 | 自定义管理，可动态替换 |
| 密码来源 | application.yml 静态配置 | AWS Secrets Manager 动态获取 |

### 密码轮换处理机制

本项目实现了**双层保护机制**：

#### 1. 主动检测（定时任务）

```java
@Scheduled(fixedDelayString = "${redis.secret-refresh-interval-seconds:30}000")
public void checkSecretRotation() {
    if (secretsManagerService.isNewSecretAvailable()) {
        // 检测到新密码版本，自动刷新客户端
        dynamicRedissonClient.refreshClient();
    }
}
```

#### 2. 被动重试（操作时检测）

```java
public <T> T executeWithRetry(RedissonOperation<T> operation, int attempt) {
    try {
        return operation.execute(getClient());
    } catch (Exception e) {
        if (isAuthenticationError(e)) {
            // 检测到认证错误（NOAUTH/WRONGPASS），自动刷新并重试
            refreshClient();
            return executeWithRetry(operation, attempt + 1);
        }
        throw e;
    }
}
```

### 密码轮换流程图

```
密码轮换触发 (AWS Lambda)
        │
        ▼
┌───────────────────────┐
│ Secrets Manager       │──────▶ ElastiCache Redis
│ password: YYY (新)    │       AUTH token: YYY
└───────────────────────┘
        │
        ▼
┌───────────────────────────────────────────────────────┐
│ 应用检测到新密码版本                                    │
│                                                       │
│  方式1: 定时任务检测 (每30秒)                           │
│  方式2: Redis操作失败时检测 (NOAUTH错误)                │
│                                                       │
│          ▼                                            │
│  DynamicRedissonClient.refreshClient()               │
│    1. 从 Secrets Manager 获取新密码                    │
│    2. 关闭旧的 RedissonClient                         │
│    3. 用新密码创建新的 RedissonClient                  │
│    4. 原子替换引用                                     │
│          ▼                                            │
│  ✅ 连接恢复，应用继续正常工作                          │
└───────────────────────────────────────────────────────┘
```

详细分析文档: [PASSWORD_ROTATION_ANALYSIS.md](./PASSWORD_ROTATION_ANALYSIS.md)

---

## License

MIT
