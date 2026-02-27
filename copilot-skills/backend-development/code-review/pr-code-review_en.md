# PR Code Review Skill

> Automatically review Pull Request code, detect security, performance and code quality issues

## Trigger Conditions

- Pull Request: opened, synchronize, reopened

---

## Review Dimensions

### 1. Code Quality

| Check Item | Description | Severity |
|------------|-------------|----------|
| Naming Conventions | Are class, method, variable names clear | Minor |
| Code Complexity | Cyclomatic complexity of methods/classes | Major |
| Code Duplication | Similar code block detection | Major |
| Comment Completeness | Are key logic sections commented | Minor |
| Error Handling | Is exception handling complete | Major |

### 2. Security

| Check Item | Description | Severity |
|------------|-------------|----------|
| SQL Injection | Parameterized query check | Critical |
| XSS | Output encoding check | Critical |
| Authentication & Authorization | Permission check completeness | Critical |
| Sensitive Information | Password, key leakage | Critical |
| Input Validation | Parameter validation completeness | Major |

### 3. Performance

| Check Item | Description | Severity |
|------------|-------------|----------|
| N+1 Queries | Association query issues | Major |
| Memory Leaks | Resource not released | Major |
| Loop Calls | Database/HTTP calls in loops | Major |
| Cache Usage | Repeated computation/queries | Minor |

### 4. Spring Boot Best Practices

| Check Item | Description | Severity |
|------------|-------------|----------|
| Transaction Management | Correct @Transactional usage | Major |
| Dependency Injection | Avoid using new to create objects | Minor |
| Configuration Management | Externalize sensitive configuration | Major |
| API Design | RESTful standards | Minor |

---

## Layer-Specific Review Rules

### Controller Layer

```java
// Review checkpoints

// 1. Annotation usage
@RestController                    // Must have
@RequestMapping("/api/v1/...")     // Versioned path
@RequiredArgsConstructor           // Constructor injection
@Validated                         // Enable validation

// 2. Method check
@GetMapping("/{id}")
@Operation(summary = "...")        // API documentation
public ApiResponse<UserResponse> getById(
    @PathVariable @Min(1) Long id  // Parameter validation
) {
    // Should not contain business logic
    // Should call Service layer
    return ApiResponse.success(userService.getById(id));
}

// 3. Common issues
// Problem: Directly returning Entity
public User getUser(Long id) {  // Exposes internal structure
    return userRepository.findById(id);
}

// Fix: Return DTO
public UserResponse getUser(Long id) {
    return userService.getById(id);
}

// Problem: Incomplete exception handling
public User getUser(Long id) {
    return userRepository.findById(id).orElse(null);  // Returns null
}

// Fix: Throw business exception
public UserResponse getUser(Long id) {
    return userRepository.findById(id)
        .map(UserResponse::from)
        .orElseThrow(() -> new ResourceNotFoundException("User", id));
}
```

### Service Layer

```java
// Review checkpoints

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // Default read-only transaction
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    // 1. Read-only operations
    public UserResponse getById(Long id) {
        return userRepository.findById(id)
            .map(UserResponse::from)
            .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    // 2. Write operations need separate marking
    @Transactional  // Override default readOnly
    public UserResponse create(UserCreateRequest request) {
        // Check uniqueness
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

    // 3. Avoid N+1 queries
    // Problem: Repository calls in loop
    public List<OrderResponse> getOrdersWithUser(List<Long> orderIds) {
        return orderIds.stream()
            .map(id -> {
                Order order = orderRepository.findById(id);  // N+1
                User user = userRepository.findById(order.getUserId());  // N+1
                return toResponse(order, user);
            })
            .toList();
    }

    // Fix: Use JOIN FETCH or batch query
    public List<OrderResponse> getOrdersWithUser(List<Long> orderIds) {
        List<Order> orders = orderRepository.findAllWithUser(orderIds);
        return orders.stream()
            .map(this::toResponse)
            .toList();
    }

    // 4. Avoid saving in loop
    // Problem
    public void batchCreate(List<UserCreateRequest> requests) {
        for (UserCreateRequest request : requests) {
            User user = toEntity(request);
            userRepository.save(user);  // N SQL statements
        }
    }

    // Fix
    public void batchCreate(List<UserCreateRequest> requests) {
        List<User> users = requests.stream()
            .map(this::toEntity)
            .toList();
        userRepository.saveAll(users);  // Batch save
    }
}
```

### Repository Layer

```java
// Review checkpoints

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // 1. Use method naming conventions
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Page<User> findByStatus(Pageable pageable, UserStatus status);

    // 2. Use @Query for complex queries
    @Query("SELECT u FROM User u WHERE u.status = :status AND u.deletedAt IS NULL")
    List<User> findActiveUsers(@Param("status") UserStatus status);

    // 3. Avoid SQL injection
    // Problem
    @Query(value = "SELECT * FROM users WHERE email = '" + "?1" + "'", nativeQuery = true)
    User findByEmailNative(String email);  // String concatenation

    // Fix
    @Query(value = "SELECT * FROM users WHERE email = :email", nativeQuery = true)
    Optional<User> findByEmailNative(@Param("email") String email);

    // 4. Update operations need @Modifying
    @Modifying
    @Query("UPDATE User u SET u.deletedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    void softDelete(@Param("id") Long id);
}
```

### Entity Layer

```java
// Review checkpoints

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

    // 1. Field constraints
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // 2. Use STRING for enums
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    // 3. Relationships
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Order> orders = new ArrayList<>();

    // 4. Timestamps
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 5. Soft delete
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 6. Avoid Lombok issues
    // Problem: @Data generates setters, may break encapsulation
    // Fix: Use @Getter and individual setters or business methods

    // 7. Avoid circular references
    // Problem: @ToString includes bidirectional associations
    @ToString(exclude = {"orders"})  // Exclude associations
    // Or use @ToString.Exclude
}
```

---

## Review Report Template

```markdown
# Pull Request Code Review Report

## Review Summary

| Dimension | Status | Issues |
|-----------|--------|--------|
| Code Quality | ✅ Pass | 2 |
| Security | ⚠️ Attention Needed | 1 |
| Performance | ❌ Changes Required | 3 |
| Best Practices | ✅ Pass | 1 |

**Overall Assessment**: Changes Required

---

## Critical Issues (0)

None

---

## Major Issues (4)

### 1. N+1 Query Issue 🔴 Performance

**File**: `src/main/java/com/example/service/OrderServiceImpl.java:45`

**Problem Description**:
Calling `userRepository.findById()` in a loop may cause N+1 query issues.

**Current Code**:
```java
for (Order order : orders) {
    User user = userRepository.findById(order.getUserId()).orElseThrow();
    order.setUser(user);
}
```

**Suggested Fix**:
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

### 2. SQL Injection Risk 🔴 Security

**File**: `src/main/java/com/example/repository/CustomRepository.java:20`

**Problem Description**:
Building SQL query using string concatenation, SQL injection risk exists.

**Current Code**:
```java
@Query(value = "SELECT * FROM users WHERE name LIKE '%" + name + "%'", nativeQuery = true)
```

**Suggested Fix**:
```java
@Query(value = "SELECT * FROM users WHERE name LIKE CONCAT('%', :name, '%')", nativeQuery = true)
List<User> searchByName(@Param("name") String name);
```

---

## Minor Issues (3)

### 1. Non-standard Naming 🟡 Style

**File**: `src/main/java/com/example/service/UserService.java:15`

**Problem Description**:
Method name `get` is too simple, suggest using a more descriptive name.

**Suggestion**:
```java
// Current
public User get(Long id);

// Suggested
public User getById(Long id);
// Or
public User findById(Long id);
```

---

## Strengths Recognized ✨

1. Good layered architecture design
2. Complete exception handling mechanism
3. Unified response format
4. Reasonable logging

---

## Improvement Suggestions

1. Consider adding cache for `OrderService`
2. Suggest adding interface documentation annotations
3. Consider adding request parameter validation
```

---

## Auto-fix Suggestions

### Automatically Fixable Issues

```java
// 1. Import sorting
// Use IDE or Maven plugin for auto-sorting

// 2. Code formatting
// Use spotless-maven-plugin

// 3. Unused imports
// Use IDE auto-cleanup

// 4. Missing final for local variables
// Use IDE inspection and suggestions

// 5. Missing @Override annotation
// Use IDE auto-add
```

### Issues Requiring Manual Review

- Business logic correctness
- Performance optimization strategies
- Architecture design reasonableness
- Security measure completeness

---

## Checklist

### Code Quality

- [ ] Clear and meaningful naming
- [ ] Reasonable method length (< 50 lines)
- [ ] Reasonable class length (< 500 lines)
- [ ] Cyclomatic complexity < 10
- [ ] No duplicate code

### Security

- [ ] No SQL injection
- [ ] No XSS vulnerabilities
- [ ] Complete authentication and authorization
- [ ] Sensitive information not exposed
- [ ] Complete input validation

### Performance

- [ ] No N+1 queries
- [ ] Resources properly released
- [ ] Avoid IO in loops
- [ ] Reasonable cache usage

### Best Practices

- [ ] Correct transaction usage
- [ ] Dependency injection not new
- [ ] Unified exception handling
- [ ] API follows RESTful
- [ ] Reasonable logging
