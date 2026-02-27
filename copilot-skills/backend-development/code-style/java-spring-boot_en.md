# Java Spring Boot Code Style Skill

> Ensure Java Spring Boot code follows best practices and enterprise development standards

## Trigger Conditions

- Command: `/java-style`
- File Changes: `src/main/java/**/*.java`
- Pull Request: opened, synchronize

---

## Naming Conventions

### Class Naming

| Type | Naming Rule | Example |
|------|-------------|---------|
| Entity | PascalCase | `User`, `OrderItem` |
| DTO | PascalCase + Request/Response | `UserCreateRequest`, `UserResponse` |
| Service | PascalCase + Service | `UserService`, `OrderService` |
| Repository | PascalCase + Repository | `UserRepository` |
| Controller | PascalCase + Controller | `UserController` |
| Exception | PascalCase + Exception | `UserNotFoundException` |
| Config | PascalCase + Config | `SecurityConfig` |

### Method Naming

```java
// Query methods
User findById(Long id);
List<User> findByStatus(UserStatus status);
Optional<User> findByEmail(String email);
boolean existsByEmail(String email);
Page<User> findByStatus(Pageable pageable, UserStatus status);

// Save methods
User save(User user);
User create(UserCreateRequest request);
User update(Long id, UserUpdateRequest request);

// Delete methods
void deleteById(Long id);
void softDelete(Long id);

// Business methods
void activateUser(Long id);
void deactivateUser(Long id);
User changePassword(Long id, PasswordChangeRequest request);
```

### Variable Naming

```java
// Use camelCase
private String userName;        // Correct
private String user_name;       // Wrong

// Constants use UPPER_SNAKE_CASE
public static final int MAX_RETRY_COUNT = 3;
public static final String DEFAULT_CHARSET = "UTF-8";
public static final BigDecimal TAX_RATE = new BigDecimal("0.13");

// Boolean variables use is/has/can prefix
private boolean isActive;
private boolean hasPermission;
private boolean canDelete;
```

---

## Annotation Standards

### Controller Layer

```java
@RestController
@RequestMapping("/api/v1/users")
@Validated
@Tag(name = "User Management", description = "User Management API")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "404", description = "User not found")
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
    @Operation(summary = "Create user")
    public ApiResponse<UserResponse> create(
        @Valid @RequestBody UserCreateRequest request
    ) {
        return ApiResponse.success(userService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user")
    public ApiResponse<UserResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UserUpdateRequest request
    ) {
        return ApiResponse.success(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete user")
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }
}
```

### Service Layer

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
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        User user = userMapper.toEntity(request);
        user = userRepository.save(user);

        // Publish domain event
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

### Repository Layer

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

### Entity Layer

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

### DTO Layer

```java
// Create Request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequest {

    @NotBlank(message = "Name cannot be empty")
    @Size(min = 2, max = 50, message = "Name length must be between 2-50")
    private String name;

    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email length cannot exceed 100")
    private String email;

    @NotBlank(message = "Password cannot be empty")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
             message = "Password must contain uppercase, lowercase letters and numbers, at least 8 characters")
    private String password;
}

// Update Request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    @Size(min = 2, max = 50, message = "Name length must be between 2-50")
    private String name;

    @Email(message = "Invalid email format")
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

## Code Format Standards

### Indentation and Spacing

- Use 4 spaces for indentation
- Place opening braces on the same line
- Add spaces around operators
- Add space after commas
- Add blank line between methods

### Import Order

```java
// 1. java.*
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

// 2. javax.* / jakarta.*
import jakarta.persistence.*;
import jakarta.validation.*;

// 3. Third-party libraries (alphabetically sorted)
import lombok.*;
import org.springframework.*;
import com.fasterxml.jackson.*;

// 4. Project packages
import com.example.project.entity.*;
import com.example.project.dto.*;
```

---

## Exception Handling

### Custom Exceptions

```java
// Base business exception
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

// Resource not found exception
public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String resource, Object id) {
        super("NOT_FOUND", String.format("%s with id '%s' not found", resource, id));
    }
}

// Duplicate resource exception
public class DuplicateResourceException extends BusinessException {
    public DuplicateResourceException(String field, Object value) {
        super("DUPLICATE_RESOURCE", String.format("%s '%s' already exists", field, value));
    }
}
```

### Global Exception Handler

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

## Unified Response Format

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

## Checklist

### Controller Layer Checks

- [ ] Use @RestController and appropriate HTTP method annotations
- [ ] Request parameters use @Valid for validation
- [ ] Return unified ApiResponse format
- [ ] Correct HTTP status code usage
- [ ] API versioning (/api/v1/...)
- [ ] Complete Swagger/OpenAPI documentation annotations

### Service Layer Checks

- [ ] Business logic in Service layer, not Controller
- [ ] @Transactional annotation used correctly
- [ ] Proper exception handling and logging
- [ ] Avoid calling Repository in loops
- [ ] Use domain events for decoupling

### Repository Layer Checks

- [ ] Use Spring Data JPA method naming conventions
- [ ] Complex queries use @Query or Specification
- [ ] Avoid N+1 query issues
- [ ] Paginated queries return Page<T>

### Entity Layer Checks

- [ ] Entity classes use @Entity and appropriate table names
- [ ] Primary key generation strategy configured correctly
- [ ] Relationships (@OneToMany, @ManyToOne) configured correctly
- [ ] Avoid writing business logic in Entity
- [ ] Use soft delete instead of physical delete

### Security Checks

- [ ] Sensitive fields (passwords) not returned to frontend
- [ ] SQL queries use parameterization, no injection risks
- [ ] Complete input validation
- [ ] Proper permission checks in place
