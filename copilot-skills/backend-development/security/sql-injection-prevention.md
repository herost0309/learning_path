# SQL 注入防护 Skill

> 识别和修复 SQL 注入漏洞，确保数据库查询安全

## 触发条件

- 命令: `/sql-injection-check`
- 文件变更: Repository、Service 文件

---

## 漏洞类型

### 1. 字符串拼接注入

#### 问题代码

```java
// 错误: 直接拼接用户输入
@Query(value = "SELECT * FROM users WHERE email = '" + email + "'", nativeQuery = true)
User findByEmail(String email);

// 错误: 动态构建 SQL
public User findByEmail(String email) {
    String sql = "SELECT * FROM users WHERE email = '" + email + "'";
    return jdbcTemplate.queryForObject(sql, userRowMapper);
}

// 攻击示例
// email = "admin@example.com' OR '1'='1"
// 生成的 SQL: SELECT * FROM users WHERE email = 'admin@example.com' OR '1'='1'
```

#### 修复方案

```java
// 正确: 使用参数化查询
@Query("SELECT u FROM User u WHERE u.email = :email")
Optional<User> findByEmail(@Param("email") String email);

// 正确: 使用 JdbcTemplate 参数
public User findByEmail(String email) {
    String sql = "SELECT * FROM users WHERE email = ?";
    return jdbcTemplate.queryForObject(sql, userRowMapper, email);
}

// 正确: 使用 NamedParameterJdbcTemplate
public User findByEmail(String email) {
    String sql = "SELECT * FROM users WHERE email = :email";
    MapSqlParameterSource params = new MapSqlParameterSource("email", email);
    return namedJdbcTemplate.queryForObject(sql, params, userRowMapper);
}
```

---

### 2. JPQL/HQL 注入

#### 问题代码

```java
// 错误: JPQL 字符串拼接
public List<User> search(String name) {
    String jpql = "SELECT u FROM User u WHERE u.name = '" + name + "'";
    return entityManager.createQuery(jpql, User.class).getResultList();
}

// 错误: 动态 ORDER BY
public List<User> findAll(String sortBy) {
    String jpql = "SELECT u FROM User u ORDER BY " + sortBy;
    return entityManager.createQuery(jpql, User.class).getResultList();
}
```

#### 修复方案

```java
// 正确: 使用参数化 JPQL
public List<User> search(String name) {
    String jpql = "SELECT u FROM User u WHERE u.name = :name";
    return entityManager.createQuery(jpql, User.class)
        .setParameter("name", name)
        .getResultList();
}

// 正确: 动态排序使用白名单
public List<User> findAll(String sortBy) {
    // 白名单验证
    Set<String> allowedSortFields = Set.of("id", "name", "createdAt", "updatedAt");
    if (!allowedSortFields.contains(sortBy)) {
        throw new IllegalArgumentException("Invalid sort field");
    }

    // 使用 Criteria API
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<User> query = cb.createQuery(User.class);
    Root<User> root = query.from(User.class);

    // 安全地构建排序
    Path<Object> sortPath = root.get(sortBy);
    query.orderBy(cb.asc(sortPath));

    return entityManager.createQuery(query).getResultList();
}
```

---

### 3. 原生 SQL 注入

#### 问题代码

```java
// 错误: 原生 SQL 拼接
@Query(value = "SELECT * FROM users WHERE status = :status AND name LIKE '%" + name + "%'", nativeQuery = true)
List<User> search(@Param("status") String status, String name);

// 错误: 动态表名/列名
@Query(value = "SELECT * FROM #{#entityName} WHERE #{#column} = :value", nativeQuery = true)
List<?> findByDynamicColumn(@Param("value") String value);
```

#### 修复方案

```java
// 正确: 完全参数化
@Query(value = "SELECT * FROM users WHERE status = :status AND name LIKE CONCAT('%', :name, '%')", nativeQuery = true)
List<User> search(@Param("status") String status, @Param("name") String name);

// 正确: 动态表名使用白名单
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
        // 验证表名
        if (!ALLOWED_TABLES.contains(table)) {
            throw new SecurityException("Invalid table name");
        }

        // 验证列名
        if (!ALLOWED_COLUMNS.get(table).contains(column)) {
            throw new SecurityException("Invalid column name");
        }

        String sql = String.format("SELECT * FROM %s WHERE %s = ?", table, column);
        return jdbcTemplate.queryForList(sql, value);
    }
}
```

---

### 4. 存储过程注入

#### 问题代码

```java
// 错误: 拼接存储过程参数
public void callProcedure(String userId) {
    String sql = "{call getUserInfo('" + userId + "')}";
    jdbcTemplate.execute(sql);
}
```

#### 修复方案

```java
// 正确: 使用 SimpleJdbcCall
@Autowired
private JdbcTemplate jdbcTemplate;

public Map<String, Object> callProcedure(String userId) {
    SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
        .withProcedureName("getUserInfo");

    Map<String, Object> params = Map.of("user_id", userId);
    return jdbcCall.execute(params);
}

// 正确: 使用 CallableStatement
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

## 安全查询模式

### Spring Data JPA 安全模式

```java
// 1. 方法命名约定（自动参数化）
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByStatusAndNameContaining(UserStatus status, String name);
}

// 2. @Query 参数化
@Query("SELECT u FROM User u WHERE u.email = :email AND u.status = :status")
Optional<User> findByEmailAndStatus(
    @Param("email") String email,
    @Param("status") UserStatus status
);

// 3. 原生查询参数化
@Query(value = "SELECT * FROM users WHERE email = :email", nativeQuery = true)
Optional<User> findByEmailNative(@Param("email") String email);

// 4. SpEL 参数化（支持实体名）
@Query("SELECT u FROM #{#entityName} u WHERE u.id = :id")
Optional<T> findByIdCustom(@Param("id") Long id);
```

### Specification 动态查询

```java
public class UserSpecification {

    public static Specification<User> withFilters(UserFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 安全的字符串模糊查询
            if (StringUtils.hasText(filter.getName())) {
                predicates.add(cb.like(
                    cb.lower(root.get("name")),
                    "%" + filter.getName().toLowerCase() + "%"
                ));
            }

            // 安全的枚举匹配
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            // 安全的日期范围
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

// 使用
Specification<User> spec = UserSpecification.withFilters(filter);
Page<User> users = userRepository.findAll(spec, pageable);
```

### QueryDSL 类型安全查询

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

## 输入验证

### 白名单验证

```java
@Component
public class SqlInjectionValidator {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Set<String> SQL_KEYWORDS = Set.of(
        "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "UNION", "OR", "AND",
        "--", "/*", "*/", ";", "'", "\""
    );

    /**
     * 验证标识符（表名、列名）是否安全
     */
    public boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }

        // 检查格式
        if (!SAFE_IDENTIFIER.matcher(identifier).matches()) {
            return false;
        }

        // 检查 SQL 关键字
        String upper = identifier.toUpperCase();
        return !SQL_KEYWORDS.contains(upper);
    }

    /**
     * 清理字符串值
     */
    public String sanitizeValue(String value) {
        if (value == null) {
            return null;
        }

        // 检测可疑模式
        if (containsSqlInjection(value)) {
            throw new SecurityException("Potential SQL injection detected");
        }

        return value;
    }

    private boolean containsSqlInjection(String value) {
        String lower = value.toLowerCase();

        // 检测常见注入模式
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

### 自定义验证注解

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

// 使用
@GetMapping("/search")
public List<User> search(
    @RequestParam @SafeSqlIdentifier String sortBy
) {
    // ...
}
```

---

## 检查清单

### 参数化查询

- [ ] 所有 SQL 查询使用参数化
- [ ] 不直接拼接用户输入
- [ ] 使用 @Param 注解命名参数
- [ ] 原生查询也要参数化

### 动态 SQL

- [ ] 使用 Criteria API 或 QueryDSL
- [ ] 动态表名/列名使用白名单
- [ ] 动态排序字段白名单验证
- [ ] 避免直接拼接 SQL 片段

### 输入验证

- [ ] 验证所有用户输入
- [ ] 白名单优于黑名单
- [ ] 检测可疑注入模式
- [ ] 设置合理的长度限制

### 最小权限

- [ ] 数据库用户最小权限
- [ ] 禁用不必要的存储过程
- [ ] 限制动态 SQL 使用
- [ ] 敏感操作二次验证
