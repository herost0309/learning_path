# SQL Injection Prevention Skill

> Identify and fix SQL injection vulnerabilities, ensure database query security

## Trigger Conditions

- Command: `/sql-injection-check`
- File Changes: Repository, Service files

---

## Vulnerability Types

### 1. String Concatenation Injection

#### Problematic Code

```java
// Wrong: Directly concatenate user input
@Query(value = "SELECT * FROM users WHERE email = '" + email + "'", nativeQuery = true)
User findByEmail(String email);

// Wrong: Dynamic SQL construction
public User findByEmail(String email) {
    String sql = "SELECT * FROM users WHERE email = '" + email + "'";
    return jdbcTemplate.queryForObject(sql, userRowMapper);
}

// Attack example
// email = "admin@example.com' OR '1'='1"
// Generated SQL: SELECT * FROM users WHERE email = 'admin@example.com' OR '1'='1'
```

#### Fix

```java
// Correct: Use parameterized query
@Query("SELECT u FROM User u WHERE u.email = :email")
Optional<User> findByEmail(@Param("email") String email);

// Correct: Use JdbcTemplate parameters
public User findByEmail(String email) {
    String sql = "SELECT * FROM users WHERE email = ?";
    return jdbcTemplate.queryForObject(sql, userRowMapper, email);
}

// Correct: Use NamedParameterJdbcTemplate
public User findByEmail(String email) {
    String sql = "SELECT * FROM users WHERE email = :email";
    MapSqlParameterSource params = new MapSqlParameterSource("email", email);
    return namedJdbcTemplate.queryForObject(sql, params, userRowMapper);
}
```

---

### 2. JPQL/HQL Injection

#### Problematic Code

```java
// Wrong: JPQL string concatenation
public List<User> search(String name) {
    String jpql = "SELECT u FROM User u WHERE u.name = '" + name + "'";
    return entityManager.createQuery(jpql, User.class).getResultList();
}

// Wrong: Dynamic ORDER BY
public List<User> findAll(String sortBy) {
    String jpql = "SELECT u FROM User u ORDER BY " + sortBy;
    return entityManager.createQuery(jpql, User.class).getResultList();
}
```

#### Fix

```java
// Correct: Use parameterized JPQL
public List<User> search(String name) {
    String jpql = "SELECT u FROM User u WHERE u.name = :name";
    return entityManager.createQuery(jpql, User.class)
        .setParameter("name", name)
        .getResultList();
}

// Correct: Use whitelist for dynamic sorting
public List<User> findAll(String sortBy) {
    // Whitelist validation
    Set<String> allowedSortFields = Set.of("id", "name", "createdAt", "updatedAt");
    if (!allowedSortFields.contains(sortBy)) {
        throw new IllegalArgumentException("Invalid sort field");
    }

    // Use Criteria API
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<User> query = cb.createQuery(User.class);
    Root<User> root = query.from(User.class);

    // Safely build sort
    Path<Object> sortPath = root.get(sortBy);
    query.orderBy(cb.asc(sortPath));

    return entityManager.createQuery(query).getResultList();
}
```

---

### 3. Native SQL Injection

#### Problematic Code

```java
// Wrong: Native SQL concatenation
@Query(value = "SELECT * FROM users WHERE status = :status AND name LIKE '%" + name + "%'", nativeQuery = true)
List<User> search(@Param("status") String status, String name);

// Wrong: Dynamic table/column names
@Query(value = "SELECT * FROM #{#entityName} WHERE #{#column} = :value", nativeQuery = true)
List<?> findByDynamicColumn(@Param("value") String value);
```

#### Fix

```java
// Correct: Fully parameterized
@Query(value = "SELECT * FROM users WHERE status = :status AND name LIKE CONCAT('%', :name, '%')", nativeQuery = true)
List<User> search(@Param("status") String status, @Param("name") String name);

// Correct: Use whitelist for dynamic table names
@Repository
public class DynamicQueryRepository {

    private static final Set<String> ALLOWED_TABLES = Set.of("users", "orders", "products");
    private static final Set<String> ALLOWED_COLUMNS = Map.of(
        "users", Set.of("id", "name", "email", "status"),
        "orders", Set.of("id", "user_id", "status", "total"),
        "products", Set.of("id", "name", "price")
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> query(String table, String column, String value) {
        // Validate table name
        if (!ALLOWED_TABLES.contains(table)) {
            throw new SecurityException("Invalid table name");
        }

        // Validate column name
        if (!ALLOWED_COLUMNS.get(table).contains(column)) {
            throw new SecurityException("Invalid column name");
        }

        String sql = String.format("SELECT * FROM %s WHERE %s = ?", table, column);
        return jdbcTemplate.queryForList(sql, value);
    }
}
```

---

### 4. Stored Procedure Injection

#### Problematic Code

```java
// Wrong: Concatenate stored procedure parameters
public void callProcedure(String userId) {
    String sql = "{call getUserInfo('" + userId + "')}";
    jdbcTemplate.execute(sql);
}
```

#### Fix

```java
// Correct: Use SimpleJdbcCall
@Autowired
private JdbcTemplate jdbcTemplate;

public Map<String, Object> callProcedure(String userId) {
    SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
        .withProcedureName("getUserInfo");

    Map<String, Object> params = Map.of("user_id", userId);
    return jdbcCall.execute(params);
}

// Correct: Use CallableStatement
public void callProcedure(String userId) {
    jdbcTemplate.execute(
        (Connection con) -> con.prepareCall("{call getUserInfo(?)}"),
        (CallableStatement cs) -> {
            cs.setString(1, userId);
            return cs.execute();
        }
    );
}
```

---

## Safe Query Patterns

### Spring Data JPA Safe Patterns

```java
// 1. Method naming conventions (auto-parameterized)
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByStatusAndNameContaining(UserStatus status, String name);
}

// 2. @Query parameterization
@Query("SELECT u FROM User u WHERE u.email = :email AND u.status = :status")
Optional<User> findByEmailAndStatus(
    @Param("email") String email,
    @Param("status") UserStatus status
);

// 3. Native query parameterization
@Query(value = "SELECT * FROM users WHERE email = :email", nativeQuery = true)
Optional<User> findByEmailNative(@Param("email") String email);

// 4. SpEL parameterization (supports entity name)
@Query("SELECT u FROM #{#entityName} u WHERE u.id = :id")
Optional<T> findByIdCustom(@Param("id") Long id);
```

### Specification Dynamic Queries

```java
public class UserSpecification {

    public static Specification<User> withFilters(UserFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Safe string fuzzy search
            if (StringUtils.hasText(filter.getName())) {
                predicates.add(cb.like(
                    cb.lower(root.get("name")),
                    "%" + filter.getName().toLowerCase() + "%"
                ));
            }

            // Safe enum match
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            // Safe date range
            if (filter.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get("createdAt"),
                    filter.getStartDate()
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

// Usage
Specification<User> spec = UserSpecification.withFilters(filter);
Page<User> users = userRepository.findAll(spec, pageable);
```

### QueryDSL Type-Safe Queries

```java
@Repository
@RequiredArgsConstructor
public class UserQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<User> search(UserFilter filter) {
        QUser user = QUser.user;

        return queryFactory
            .selectFrom(user)
            .where(
                nameContains(filter.getName()),
                statusEq(filter.getStatus()),
                createdAtAfter(filter.getStartDate())
            )
            .orderBy(user.createdAt.desc())
            .fetch();
    }

    private BooleanExpression nameContains(String name) {
        return StringUtils.hasText(name)
            ? user.name.containsIgnoreCase(name)
            : null;
    }

    private BooleanExpression statusEq(UserStatus status) {
        return status != null ? user.status.eq(status) : null;
    }

    private BooleanExpression createdAtAfter(LocalDate date) {
        return date != null
            ? user.createdAt.goe(date.atStartOfDay())
            : null;
    }
}
```

---

## Input Validation

### Whitelist Validation

```java
@Component
public class SqlInjectionValidator {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Set<String> SQL_KEYWORDS = Set.of(
        "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "UNION", "OR", "AND",
        "--", "/*", "*/", ";", "'", "\""
    );

    /**
     * Validate if identifier (table name, column name) is safe
     */
    public boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }

        // Check format
        if (!SAFE_IDENTIFIER.matcher(identifier).matches()) {
            return false;
        }

        // Check SQL keywords
        String upper = identifier.toUpperCase();
        return !SQL_KEYWORDS.contains(upper);
    }

    /**
     * Sanitize string value
     */
    public String sanitizeValue(String value) {
        if (value == null) {
            return null;
        }

        // Detect suspicious patterns
        if (containsSqlInjection(value)) {
            throw new SecurityException("Potential SQL injection detected");
        }

        return value;
    }

    private boolean containsSqlInjection(String value) {
        String lower = value.toLowerCase();

        // Detect common injection patterns
        return lower.contains("' or '") ||
               lower.contains("\" or \"") ||
               lower.contains("--") ||
               lower.contains("/*") ||
               lower.contains("union select") ||
               lower.contains("; drop") ||
               lower.matches(".*'\\s*(or|and)\\s*'.*");
    }
}
```

### Custom Validation Annotation

```java
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SafeSqlIdentifierValidator.class)
public @interface SafeSqlIdentifier {
    String message() default "Invalid SQL identifier";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class SafeSqlIdentifierValidator implements ConstraintValidator<SafeSqlIdentifier, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        return SAFE_IDENTIFIER.matcher(value).matches();
    }
}

// Usage
@GetMapping("/search")
public List<User> search(
    @RequestParam @SafeSqlIdentifier String sortBy
) {
    // ...
}
```

---

## Checklist

### Parameterized Queries

- [ ] All SQL queries use parameterization
- [ ] No direct concatenation of user input
- [ ] Use @Param annotation for named parameters
- [ ] Native queries also parameterized

### Dynamic SQL

- [ ] Use Criteria API or QueryDSL
- [ ] Whitelist dynamic table/column names
- [ ] Whitelist validate dynamic sort fields
- [ ] Avoid directly concatenating SQL fragments

### Input Validation

- [ ] Validate all user input
- [ ] Prefer whitelist over blacklist
- [ ] Detect suspicious injection patterns
- [ ] Set reasonable length limits

### Least Privilege

- [ ] Database user has minimal privileges
- [ ] Disable unnecessary stored procedures
- [ ] Limit dynamic SQL usage
- [ ] Secondary verification for sensitive operations
