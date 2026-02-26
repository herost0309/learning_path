# 数据库查询优化 Skill

> 分析和优化数据库查询，解决性能瓶颈

## 触发条件

- 命令: `/query-optimize`
- Pull Request: Repository 文件变更

---

## N+1 查询问题

### 问题描述

```java
// 问题代码: N+1 查询
@GetMapping("/orders")
public List<OrderResponse> getOrders() {
    List<Order> orders = orderRepository.findAll();  // 1 次查询

    return orders.stream()
        .map(order -> {
            User user = userRepository.findById(order.getUserId());  // N 次查询
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());  // N 次查询
            return OrderResponse.of(order, user, items);
        })
        .toList();
}
// 总查询次数: 1 + N + N = 1 + 2N
```

### 解决方案

#### 1. JOIN FETCH

```java
// Repository
@Query("SELECT o FROM Order o JOIN FETCH o.user LEFT JOIN FETCH o.items WHERE o.id = :id")
Optional<Order> findByIdWithDetails(@Param("id") Long id);

@Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.user LEFT JOIN FETCH o.items")
List<Order> findAllWithDetails();

// 使用
@GetMapping("/orders")
public List<OrderResponse> getOrders() {
    List<Order> orders = orderRepository.findAllWithDetails();  // 1 次查询
    return orders.stream()
        .map(OrderResponse::from)
        .toList();
}
```

#### 2. EntityGraph

```java
// Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"user", "items"})
    List<Order> findAll();

    @EntityGraph(attributePaths = {"user", "items", "items.product"})
    Optional<Order> findWithAllDetailsById(Long id);
}

// Named EntityGraph
@NamedEntityGraph(
    name = "Order.withDetails",
    attributeNodes = {
        @NamedAttributeNode("user"),
        @NamedAttributeNode(value = "items", subgraph = "items-with-product")
    },
    subgraphs = {
        @NamedSubgraph(
            name = "items-with-product",
            attributeNodes = @NamedAttributeNode("product")
        )
    }
)
@Entity
public class Order { ... }
```

#### 3. @BatchSize

```java
// Entity 配置
@Entity
public class Order {
    @OneToMany(mappedBy = "order")
    @BatchSize(size = 100)  // 批量加载
    private List<OrderItem> items;
}

// 全局配置
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100
```

#### 4. 批量查询

```java
// 手动批量查询
public List<OrderResponse> getOrders() {
    List<Order> orders = orderRepository.findAll();

    // 批量获取用户
    Set<Long> userIds = orders.stream()
        .map(Order::getUserId)
        .collect(Collectors.toSet());
    Map<Long, User> userMap = userRepository.findAllById(userIds)
        .stream()
        .collect(Collectors.toMap(User::getId, Function.identity()));

    // 批量获取订单项
    List<Long> orderIds = orders.stream().map(Order::getId).toList();
    Map<Long, List<OrderItem>> itemsMap = orderItemRepository.findByOrderIdIn(orderIds)
        .stream()
        .collect(Collectors.groupingBy(item -> item.getOrder().getId()));

    return orders.stream()
        .map(order -> OrderResponse.of(
            order,
            userMap.get(order.getUserId()),
            itemsMap.getOrDefault(order.getId(), List.of())
        ))
        .toList();
}
```

---

## 索引优化

### 索引设计原则

```sql
-- 1. 主键索引（自动创建）
PRIMARY KEY (id)

-- 2. 唯一索引
CREATE UNIQUE INDEX idx_user_email ON users(email);

-- 3. 普通索引
CREATE INDEX idx_order_status ON orders(status);

-- 4. 复合索引（注意列顺序）
-- 最左前缀原则: (a, b, c) 可用于 a, ab, abc
CREATE INDEX idx_order_user_status ON orders(user_id, status);
CREATE INDEX idx_order_status_created ON orders(status, created_at DESC);

-- 5. 覆盖索引（避免回表）
CREATE INDEX idx_order_covering ON orders(user_id, status, total_amount);

-- 6. 部分索引
CREATE INDEX idx_active_users ON users(email) WHERE deleted_at IS NULL;

-- 7. 表达式索引
CREATE INDEX idx_lower_email ON users(lower(email));
```

### JPA 索引配置

```java
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_order_user_id", columnList = "user_id"),
    @Index(name = "idx_order_status", columnList = "status"),
    @Index(name = "idx_order_user_status", columnList = "user_id, status"),
    @Index(name = "idx_order_created_at", columnList = "created_at DESC")
})
public class Order {
    // ...
}
```

### 索引使用检查

```java
// 问题: 索引列使用函数，导致索引失效
@Query("SELECT o FROM Order o WHERE DATE(o.createdAt) = :date")
List<Order> findByDate(@Param("date") LocalDate date);

// 修复: 使用范围查询
@Query("SELECT o FROM Order o WHERE o.createdAt >= :start AND o.createdAt < :end")
List<Order> findByDateRange(
    @Param("start") LocalDateTime start,
    @Param("end") LocalDateTime end
);

// 问题: LIKE 以通配符开头，索引失效
@Query("SELECT u FROM User u WHERE u.name LIKE %:keyword%")
List<User> search(@Param("keyword") String keyword);

// 优化: 使用全文索引或 Elasticsearch
// 或至少允许前缀匹配
@Query("SELECT u FROM User u WHERE u.name LIKE :keyword%")
List<User> searchByPrefix(@Param("keyword") String keyword);
```

---

## 分页优化

### 传统分页问题

```sql
-- 问题: 大偏移量性能差
SELECT * FROM orders ORDER BY id LIMIT 10 OFFSET 1000000;
-- 需要扫描 1000010 行
```

### 游标分页（Cursor-based）

```java
// Repository
@Query("SELECT o FROM Order o WHERE o.id > :lastId ORDER BY o.id ASC LIMIT :limit")
List<Order> findAfterId(@Param("lastId") Long lastId, @Param("limit") int limit);

// Service
public PageResponse<OrderResponse> getOrders(Long lastId, int size) {
    List<Order> orders = orderRepository.findAfterId(
        lastId != null ? lastId : 0,
        size + 1  // 多查一条判断是否有下一页
    );

    boolean hasNext = orders.size() > size;
    if (hasNext) {
        orders = orders.subList(0, size);
    }

    Long nextCursor = hasNext ? orders.get(orders.size() - 1).getId() : null;

    return PageResponse.<OrderResponse>builder()
        .items(orders.stream().map(OrderResponse::from).toList())
        .nextCursor(nextCursor)
        .hasNext(hasNext)
        .build();
}
```

### 覆盖索引优化

```java
// 先通过覆盖索引获取 ID
@Query(value = """
    SELECT o.id FROM orders o
    WHERE o.status = :status
    ORDER BY o.created_at DESC
    LIMIT :limit OFFSET :offset
    """, nativeQuery = true)
List<Long> findIdsByStatus(
    @Param("status") String status,
    @Param("limit") int limit,
    @Param("offset") int offset
);

// 再通过 ID 获取完整数据
@Query("SELECT o FROM Order o WHERE o.id IN :ids ORDER BY o.createdAt DESC")
List<Order> findByIds(@Param("ids") List<Long> ids);

// 组合使用
public Page<Order> findByStatus(String status, int page, int size) {
    int offset = page * size;
    List<Long> ids = findIdsByStatus(status, size, offset);
    List<Order> orders = findByIds(ids);
    // ...
}
```

---

## 批量操作优化

### 批量插入配置

```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 100
          batch_versioned_data: true
        order_inserts: true
        order_updates: true
```

### 批量插入实现

```java
@Service
@RequiredArgsConstructor
public class BatchInsertService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void batchInsert(List<OrderCreateRequest> requests) {
        int batchSize = 100;

        for (int i = 0; i < requests.size(); i++) {
            Order order = toEntity(requests.get(i));
            entityManager.persist(order);

            if (i > 0 && i % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }

        entityManager.flush();
        entityManager.clear();
    }
}

// 或使用 JpaRepository
@Transactional
public void batchInsert(List<Order> orders) {
    orderRepository.saveAll(orders);
}
```

### 批量更新

```java
// 使用 JPQL 批量更新
@Modifying
@Query("UPDATE Order o SET o.status = :status WHERE o.id IN :ids")
int bulkUpdateStatus(@Param("ids") List<Long> ids, @Param("status") OrderStatus status);

// 使用 JdbcTemplate
public void batchUpdate(List<Order> orders) {
    jdbcTemplate.batchUpdate(
        "UPDATE orders SET status = ?, updated_at = ? WHERE id = ?",
        orders.stream()
            .map(order -> new Object[]{
                order.getStatus().name(),
                Timestamp.valueOf(LocalDateTime.now()),
                order.getId()
            })
            .toList()
    );
}
```

---

## 查询分析工具

### EXPLAIN 分析

```java
// 在日志中显示 SQL 执行计划
spring:
  jpa:
    properties:
      hibernate:
        show_sql: true
        format_sql: true
        use_sql_comments: true

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### 慢查询日志

```yaml
# PostgreSQL
logging:
  level:
    org.hibernate.SQL_SLOW: DEBUG

# 自定义慢查询检测
@Component
@Slf4j
public class SlowQueryInterceptor implements EmptyInterceptor {

    private static final long SLOW_QUERY_THRESHOLD_MS = 1000;

    @Override
    public String onPrepareStatement(String sql) {
        long start = System.currentTimeMillis();
        // ... 执行查询
        long elapsed = System.currentTimeMillis() - start;

        if (elapsed > SLOW_QUERY_THRESHOLD_MS) {
            log.warn("Slow query detected ({}ms): {}", elapsed, sql);
        }

        return sql;
    }
}
```

### 数据库监控

```java
// 使用 Micrometer 监控
@Bean
public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
    return registry -> registry.config().commonTags("application", "my-app");
}

// 自定义查询指标
@Component
@RequiredArgsConstructor
public class QueryMetricsAspect {

    private final MeterRegistry meterRegistry;

    @Around("execution(* com.example..repository.*.*(..))")
    public Object measureQueryTime(ProceedingJoinPoint pjp) throws Throwable {
        String methodName = pjp.getSignature().getName();
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            return pjp.proceed();
        } finally {
            sample.stop(meterRegistry.timer("repository.query",
                "method", methodName,
                "repository", pjp.getTarget().getClass().getSimpleName()
            ));
        }
    }
}
```

---

## 检查清单

### N+1 查询

- [ ] 识别所有循环中的数据库调用
- [ ] 使用 JOIN FETCH 或 EntityGraph
- [ ] 配置 @BatchSize
- [ ] 手动批量查询

### 索引优化

- [ ] 为常用查询条件创建索引
- [ ] 复合索引顺序正确
- [ ] 避免索引列使用函数
- [ ] 定期分析索引使用情况

### 分页优化

- [ ] 大数据量使用游标分页
- [ ] 避免大 OFFSET
- [ ] 考虑使用覆盖索引

### 批量操作

- [ ] 配置批量大小
- [ ] 定期 flush 和 clear
- [ ] 使用批量更新代替逐条更新

### 监控

- [ ] 开启慢查询日志
- [ ] 配置查询指标
- [ ] 定期分析执行计划
