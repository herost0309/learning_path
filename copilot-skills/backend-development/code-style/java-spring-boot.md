# Java Spring Boot 代码风格 Skill

> 确保 Java Spring Boot 代码遵循最佳实践和企业级开发规范

## 触发条件

- 命令: `/java-style`
- 文件变更: `src/main/java/**/*.java`
- Pull Request: opened, synchronize

---

## 命名规范

### 类命名

| 类型 | 命名规则 | 示例 |
|------|----------|------|
| Entity | PascalCase | `User`, `OrderItem` |
| DTO | PascalCase + Request/Response | `UserCreateRequest`, `UserResponse` |
| Service | PascalCase + Service | `UserService`, `OrderService` |
| Repository | PascalCase + Repository | `UserRepository` |
| Controller | PascalCase + Controller | `UserController` |
| Exception | PascalCase + Exception | `UserNotFoundException` |
| Config | PascalCase + Config | `SecurityConfig` |

### 方法命名

```java
// 查询方法
User findById(Long id);
List<User> findByStatus(UserStatus status);
Optional<User> findByEmail(String email);
boolean existsByEmail(String email);
Page<User> findByStatus(Pageable pageable, UserStatus status);

// 保存方法
User save(User user);
User create(UserCreateRequest request);
User update(Long id, UserUpdateRequest request);

// 删除方法
void deleteById(Long id);
void softDelete(Long id);

// 业务方法
void activateUser(Long id);
void deactivateUser(Long id);
User changePassword(Long id, PasswordChangeRequest request);
```

### 变量命名

```java
// 使用驼峰命名
private String userName;        // 正确
private String user_name;       // 错误

// 常量使用大写下划线
public static final int MAX_RETRY_COUNT = 3;
public static final String DEFAULT_CHARSET = "UTF-8";
public static final BigDecimal TAX_RATE = new BigDecimal("0.13");

// 布尔变量使用 is/has/can 前缀
private boolean isActive;
private boolean hasPermission;
private boolean canDelete;
```

---

## 注解规范

### Controller 层

```java
@RestController
@RequestMapping("/api/v1/users")
@Validated
@Tag(name = "User Management", description = "用户管理API")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取用户")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public ApiResponse<UserResponse> getById(
        @PathVariable @Min(1) Long id,
        @RequestHeader(value = "X-Request-Id", required = false) String requestId
    ) {
        log.debug("Getting user by id: {}", id);
        return ApiResponse.success(userService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建用户")
    public ApiResponse<UserResponse> create(
        @Valid @RequestBody UserCreateRequest request
    ) {
        return ApiResponse.success(userService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新用户")
    public ApiResponse<UserResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UserUpdateRequest request
    ) {
        return ApiResponse.success(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除用户")
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }
}
```

### Service 层

```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public UserResponse getById(Long id) {
        return userRepository.findById(id)
            .map(userMapper::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    @Override
    @Transactional
    public UserResponse create(UserCreateRequest request) {
        // 检查邮箱是否已存在
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        User user = userMapper.toEntity(request);
        user = userRepository.save(user);

        // 发布领域事件
        eventPublisher.publishEvent(new UserCreatedEvent(user.getId()));

        log.info("Created user with id: {}", user.getId());
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", id));

        userMapper.updateFromRequest(request, user);
        user = userRepository.save(user);

        log.info("Updated user with id: {}", id);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User", id);
        }
        userRepository.softDelete(id);
        log.info("Deleted user with id: {}", id);
    }
}
```

### Repository 层

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.status = :status AND u.deletedAt IS NULL")
    List<User> findActiveUsers(@Param("status") UserStatus status);

    Page<User> findByStatus(Pageable pageable, UserStatus status);

    @Modifying
    @Query("UPDATE User u SET u.deletedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    void softDelete(@Param("id") Long id);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.email = :email AND u.id != :excludeId")
    boolean existsByEmailAndIdNot(@Param("email") String email, @Param("excludeId") Long excludeId);
}
```

### Entity 层

```java
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email", unique = true),
    @Index(name = "idx_user_status", columnList = "status")
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

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Order> orders = new ArrayList<>();
}
```

### DTO 层

```java
// Create Request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(min = 2, max = 50, message = "姓名长度必须在2-50之间")
    private String name;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
             message = "密码必须包含大小写字母和数字，至少8位")
    private String password;
}

// Update Request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    @Size(min = 2, max = 50, message = "姓名长度必须在2-50之间")
    private String name;

    @Email(message = "邮箱格式不正确")
    private String email;
}

// Response
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private UserStatus status;
    private String createdAt;
    private String updatedAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
            .id(user.getId())
            .name(user.getName())
            .email(user.getEmail())
            .status(user.getStatus())
            .createdAt(user.getCreatedAt() != null ?
                user.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null)
            .updatedAt(user.getUpdatedAt() != null ?
                user.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null)
            .build();
    }
}
```

---

## 代码格式规范

### 缩进和空格

- 使用4个空格缩进
- 大括号放在同一行
- 操作符两侧加空格
- 逗号后加空格
- 方法之间空一行

### 导入顺序

```java
// 1. java.*
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

// 2. javax.* / jakarta.*
import jakarta.persistence.*;
import jakarta.validation.*;

// 3. 第三方库（按字母排序）
import lombok.*;
import org.springframework.*;
import com.fasterxml.jackson.*;

// 4. 本项目
import com.example.project.entity.*;
import com.example.project.dto.*;
```

---

## 异常处理

### 自定义异常

```java
// 基础业务异常
@Getter
public class BusinessException extends RuntimeException {
    private final String code;
    private final Object[] args;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.args = null;
    }

    public BusinessException(String code, String message, Object... args) {
        super(message);
        this.code = code;
        this.args = args;
    }
}

// 资源不存在异常
public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String resource, Object id) {
        super("NOT_FOUND", String.format("%s with id '%s' not found", resource, id));
    }
}

// 重复资源异常
public class DuplicateResourceException extends BusinessException {
    public DuplicateResourceException(String field, Object value) {
        super("DUPLICATE_RESOURCE", String.format("%s '%s' already exists", field, value));
    }
}
```

### 全局异常处理

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<ValidationError> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> new ValidationError(error.getField(), error.getDefaultMessage()))
            .toList();

        log.warn("Validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("VALIDATION_ERROR", "Validation failed", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
```

---

## 统一响应格式

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private String code;
    private String message;
    private T data;
    private String timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .code("SUCCESS")
            .message("Operation completed successfully")
            .data(data)
            .timestamp(Instant.now().toString())
            .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
            .code("SUCCESS")
            .message(message)
            .data(data)
            .timestamp(Instant.now().toString())
            .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
            .code(code)
            .message(message)
            .timestamp(Instant.now().toString())
            .build();
    }

    public static <T> ApiResponse<T> error(String code, String message, Object errors) {
        return ApiResponse.<T>builder()
            .code(code)
            .message(message)
            .data((T) errors)
            .timestamp(Instant.now().toString())
            .build();
    }
}
```

---

## 检查清单

### Controller 层检查

- [ ] 使用 @RestController 和适当的 HTTP 方法注解
- [ ] 请求参数使用 @Valid 进行验证
- [ ] 返回统一的 ApiResponse 格式
- [ ] 正确的 HTTP 状态码使用
- [ ] API 版本控制 (/api/v1/...)
- [ ] Swagger/OpenAPI 文档注解完整

### Service 层检查

- [ ] 业务逻辑在 Service 层，不在 Controller
- [ ] 事务注解 @Transactional 正确使用
- [ ] 适当的异常处理和日志记录
- [ ] 避免在循环中调用 Repository
- [ ] 使用领域事件解耦

### Repository 层检查

- [ ] 使用 Spring Data JPA 方法命名约定
- [ ] 复杂查询使用 @Query 或 Specification
- [ ] 避免 N+1 查询问题
- [ ] 分页查询返回 Page<T>

### Entity 层检查

- [ ] 实体类使用 @Entity 和适当的表名
- [ ] 主键生成策略正确配置
- [ ] 关联关系 (@OneToMany, @ManyToOne) 正确配置
- [ ] 避免在 Entity 中写业务逻辑
- [ ] 使用软删除而非物理删除

### 安全检查

- [ ] 敏感字段（密码）不返回到前端
- [ ] SQL 查询使用参数化，无注入风险
- [ ] 输入验证完整
- [ ] 权限检查到位
