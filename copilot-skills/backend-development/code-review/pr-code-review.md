# PR 代码审查 Skill

> 自动审查 Pull Request 代码，检测安全、性能和代码质量问题

## 触发条件

- Pull Request: opened, synchronize, reopened

---

## 审查维度

### 1. 代码质量

| 检查项 | 说明 | 严重程度 |
|--------|------|----------|
| 命名规范 | 类、方法、变量命名是否清晰 | Minor |
| 代码复杂度 | 方法/类的圈复杂度 | Major |
| 代码重复 | 相似代码块检测 | Major |
| 注释完整性 | 关键逻辑是否有注释 | Minor |
| 错误处理 | 异常处理是否完善 | Major |

### 2. 安全性

| 检查项 | 说明 | 严重程度 |
|--------|------|----------|
| SQL 注入 | 参数化查询检查 | Critical |
| XSS | 输出编码检查 | Critical |
| 认证授权 | 权限检查完整性 | Critical |
| 敏感信息 | 密码、密钥泄露 | Critical |
| 输入验证 | 参数验证完整性 | Major |

### 3. 性能

| 检查项 | 说明 | 严重程度 |
|--------|------|----------|
| N+1 查询 | 关联查询问题 | Major |
| 内存泄漏 | 资源未释放 | Major |
| 循环中调用 | 循环内的数据库/HTTP 调用 | Major |
| 缓存使用 | 重复计算/查询 | Minor |

### 4. Spring Boot 最佳实践

| 检查项 | 说明 | 严重程度 |
|--------|------|----------|
| 事务管理 | @Transactional 正确使用 | Major |
| 依赖注入 | 避免使用 new 创建对象 | Minor |
| 配置管理 | 敏感配置外部化 | Major |
| API 设计 | RESTful 规范 | Minor |

---

## 分层审查规则

### Controller 层

```java
// 审查检查点

// 1. 注解使用
@RestController                    // 必须有
@RequestMapping("/api/v1/...")     // 版本化路径
@RequiredArgsConstructor           // 构造器注入
@Validated                         // 启用验证

// 2. 方法检查
@GetMapping("/{id}")
@Operation(summary = "...")        // API 文档
public ApiResponse<UserResponse> getById(
    @PathVariable @Min(1) Long id  // 参数验证
) {
    // 不应该包含业务逻辑
    // 应该调用 Service 层
    return ApiResponse.success(userService.getById(id));
}

// 3. 常见问题
// 问题: 直接返回 Entity
public User getUser(Long id) {  // 暴露内部结构
    return userRepository.findById(id);
}

// 修复: 返回 DTO
public UserResponse getUser(Long id) {
    return userService.getById(id);
}

// 问题: 异常处理不完整
public User getUser(Long id) {
    return userRepository.findById(id).orElse(null);  // 返回 null
}

// 修复: 抛出业务异常
public UserResponse getUser(Long id) {
    return userRepository.findById(id)
        .map(UserResponse::from)
        .orElseThrow(() -> new ResourceNotFoundException("User", id));
}
```

### Service 层

```java
// 审查检查点

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 默认只读事务
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    // 1. 只读操作
    public UserResponse getById(Long id) {
        return userRepository.findById(id)
            .map(UserResponse::from)
            .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    // 2. 写操作需要单独标记
    @Transactional  // 覆盖默认的 readOnly
    public UserResponse create(UserCreateRequest request) {
        // 检查唯一性
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        User user = User.builder()
            .name(request.getName())
            .email(request.getEmail())
            .status(UserStatus.ACTIVE)
            .build();

        user = userRepository.save(user);
        log.info("Created user with id: {}", user.getId());

        return UserResponse.from(user);
    }

    // 3. 避免 N+1 查询
    // 问题: 循环中调用 Repository
    public List<OrderResponse> getOrdersWithUser(List<Long> orderIds) {
        return orderIds.stream()
            .map(id -> {
                Order order = orderRepository.findById(id);  // N+1
                User user = userRepository.findById(order.getUserId());  // N+1
                return toResponse(order, user);
            })
            .toList();
    }

    // 修复: 使用 JOIN FETCH 或批量查询
    public List<OrderResponse> getOrdersWithUser(List<Long> orderIds) {
        List<Order> orders = orderRepository.findAllWithUser(orderIds);
        return orders.stream()
            .map(this::toResponse)
            .toList();
    }

    // 4. 避免在循环中保存
    // 问题
    public void batchCreate(List<UserCreateRequest> requests) {
        for (UserCreateRequest request : requests) {
            User user = toEntity(request);
            userRepository.save(user);  // N 次 SQL
        }
    }

    // 修复
    public void batchCreate(List<UserCreateRequest> requests) {
        List<User> users = requests.stream()
            .map(this::toEntity)
            .toList();
        userRepository.saveAll(users);  // 批量保存
    }
}
```

### Repository 层

```java
// 审查检查点

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // 1. 使用方法命名约定
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Page<User> findByStatus(Pageable pageable, UserStatus status);

    // 2. 复杂查询使用 @Query
    @Query("SELECT u FROM User u WHERE u.status = :status AND u.deletedAt IS NULL")
    List<User> findActiveUsers(@Param("status") UserStatus status);

    // 3. 避免 SQL 注入
    // 问题
    @Query(value = "SELECT * FROM users WHERE email = '" + "?1" + "'", nativeQuery = true)
    User findByEmailNative(String email);  // 字符串拼接

    // 修复
    @Query(value = "SELECT * FROM users WHERE email = :email", nativeQuery = true)
    Optional<User> findByEmailNative(@Param("email") String email);

    // 4. 更新操作需要 @Modifying
    @Modifying
    @Query("UPDATE User u SET u.deletedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    void softDelete(@Param("id") Long id);
}
```

### Entity 层

```java
// 审查检查点

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SQLDelete(sql = "UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 1. 字段约束
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // 2. 枚举使用 STRING
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    // 3. 关联关系
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Order> orders = new ArrayList<>();

    // 4. 时间戳
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 5. 软删除
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 6. 避免 Lombok 的问题
    // 问题: @Data 会生成 setter，可能破坏封装
    // 修复: 使用 @Getter 和单独的 setter 或业务方法

    // 7. 避免循环引用
    // 问题: @ToString 包含双向关联
    @ToString(exclude = {"orders"})  // 排除关联
    // 或使用 @ToString.Exclude
}
```

---

## 审查报告模板

```markdown
# Pull Request 代码审查报告

## 审查总结

| 维度 | 状态 | 问题数 |
|------|------|--------|
| 代码质量 | ✅ 通过 | 2 |
| 安全性 | ⚠️ 需注意 | 1 |
| 性能 | ❌ 需修改 | 3 |
| 最佳实践 | ✅ 通过 | 1 |

**总体评价**: 需要修改

---

## Critical 问题 (0)

无

---

## Major 问题 (4)

### 1. N+1 查询问题 🔴 Performance

**文件**: `src/main/java/com/example/service/OrderServiceImpl.java:45`

**问题描述**:
在循环中调用 `userRepository.findById()`，可能导致 N+1 查询问题。

**当前代码**:
```java
for (Order order : orders) {
    User user = userRepository.findById(order.getUserId()).orElseThrow();
    order.setUser(user);
}
```

**修复建议**:
```java
Set<Long> userIds = orders.stream()
    .map(Order::getUserId)
    .collect(Collectors.toSet());
Map<Long, User> userMap = userRepository.findAllById(userIds)
    .stream()
    .collect(Collectors.toMap(User::getId, Function.identity()));

for (Order order : orders) {
    order.setUser(userMap.get(order.getUserId()));
}
```

---

### 2. SQL 注入风险 🔴 Security

**文件**: `src/main/java/com/example/repository/CustomRepository.java:20`

**问题描述**:
使用字符串拼接构建 SQL 查询，存在 SQL 注入风险。

**当前代码**:
```java
@Query(value = "SELECT * FROM users WHERE name LIKE '%" + name + "%'", nativeQuery = true)
```

**修复建议**:
```java
@Query(value = "SELECT * FROM users WHERE name LIKE CONCAT('%', :name, '%')", nativeQuery = true)
List<User> searchByName(@Param("name") String name);
```

---

## Minor 问题 (3)

### 1. 命名不规范 🟡 Style

**文件**: `src/main/java/com/example/service/UserService.java:15`

**问题描述**:
方法名 `get` 过于简单，建议使用更具描述性的名称。

**建议**:
```java
// 当前
public User get(Long id);

// 建议
public User getById(Long id);
// 或
public User findById(Long id);
```

---

## 优点认可 ✨

1. 良好的分层架构设计
2. 完善的异常处理机制
3. 统一的响应格式
4. 合理的日志记录

---

## 改进建议

1. 考虑为 `OrderService` 添加缓存
2. 建议增加接口文档注解
3. 考虑添加请求参数校验
```

---

## 自动化修复建议

### 可自动修复的问题

```java
// 1. 导入排序
// 使用 IDE 或 Maven 插件自动排序

// 2. 代码格式化
// 使用 spotless-maven-plugin

// 3. 未使用的导入
// 使用 IDE 自动清理

// 4. 缺少 final 的局部变量
// 使用 IDE 检查和建议

// 5. 缺少 @Override 注解
// 使用 IDE 自动添加
```

### 需要人工审查的问题

- 业务逻辑正确性
- 性能优化策略
- 架构设计合理性
- 安全措施完整性

---

## 检查清单

### 代码质量

- [ ] 命名清晰有意义
- [ ] 方法长度合理（< 50 行）
- [ ] 类长度合理（< 500 行）
- [ ] 圈复杂度 < 10
- [ ] 没有重复代码

### 安全性

- [ ] 没有 SQL 注入
- [ ] 没有 XSS 漏洞
- [ ] 认证授权完整
- [ ] 敏感信息不暴露
- [ ] 输入验证完整

### 性能

- [ ] 没有 N+1 查询
- [ ] 资源正确释放
- [ ] 避免循环中的 IO
- [ ] 合理使用缓存

### 最佳实践

- [ ] 正确使用事务
- [ ] 依赖注入而非 new
- [ ] 统一异常处理
- [ ] API 符合 RESTful
- [ ] 日志记录合理
