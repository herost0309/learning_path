# Database Query Optimization Skill

> Analyze and optimize database queries, resolve performance bottlenecks

## Trigger Conditions

- Command: `/query-optimize`
- Pull Request: Repository file changes

---

## N+1 Query Problem

### Problem Description

```java
// Problematic code: N+1 query
@GetMapping("/orders")
public List<OrderResponse> getOrders() {
    List<Order> orders = orderRepository.findAll();  // 1 query

    return orders.stream()
        .map(order -> {
            User user = userRepository.findById(order.getUserId());  // N queries
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());  // N queries
            return OrderResponse.of(order, user, items);
        })
        .toList();
}
// Total queries: 1 + N + N = 1 + 2N
```

### Solutions

#### 1. JOIN FETCH

```java
// Repository
@Query("SELECT o FROM Order o JOIN FETCH o.user LEFT JOIN FETCH o.items WHERE o.id = :id")
Optional<Order> findByIdWithDetails(@Param("id") Long id);

@Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.user LEFT JOIN FETCH o.items")
List<Order> findAllWithDetails();

// Usage
@GetMapping("/orders")
public List<OrderResponse> getOrders() {
    List<Order> orders = orderRepository.findAllWithDetails();  // 1 query
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
// Entity configuration
@Entity
public class Order {
    @OneToMany(mappedBy = "order")
    @BatchSize(size = 100)  // Batch loading
    private List<OrderItem> items;
}

// Global configuration
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100
```

#### 4. Batch Queries

```java
// Manual batch query
public List<OrderResponse> getOrders() {
    List<Order> orders = orderRepository.findAll();

    // Batch fetch users
    Set<Long> userIds = orders.stream()
        .map(Order::getUserId)
        .collect(Collectors.toSet());
    Map<Long, User> userMap = userRepository.findAllById(userIds)
        .stream()
        .collect(Collectors.toMap(User::getId, Function.identity()));

    // Batch fetch order items
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

## Index Optimization

### Index Design Principles

```sql
-- 1. Primary key index (auto-created)
PRIMARY KEY (id)

-- 2. Unique index
CREATE UNIQUE INDEX idx_user_email ON users(email);

-- 3. Regular index
CREATE INDEX idx_order_status ON orders(status);

-- 4. Composite index (note column order)
-- Leftmost prefix rule: (a, b, c) can be used for a, ab, abc
CREATE INDEX idx_order_user_status ON orders(user_id, status);
CREATE INDEX idx_order_status_created ON orders(status, created_at DESC);

-- 5. Covering index (avoid table lookup)
CREATE INDEX idx_order_covering ON orders(user_id, status, total_amount);

-- 6. Partial index
CREATE INDEX idx_active_users ON users(email) WHERE deleted_at IS NULL;

-- 7. Expression index
CREATE INDEX idx_lower_email ON users(lower(email));
```

### JPA Index Configuration

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

### Index Usage Check

```java
// Problem: Using function on indexed column causes index to be ignored
@Query("SELECT o FROM Order o WHERE DATE(o.createdAt) = :date")
List<Order> findByDate(@Param("date") LocalDate date);

// Fix: Use range query
@Query("SELECT o FROM Order o WHERE o.createdAt >= :start AND o.createdAt < :end")
List<Order> findByDateRange(
    @Param("start") LocalDateTime start,
    @Param("end") LocalDateTime end
);

// Problem: LIKE starting with wildcard causes index to be ignored
@Query("SELECT u FROM User u WHERE u.name LIKE %:keyword%")
List<User> search(@Param("keyword") String keyword);

// Optimization: Use full-text index or Elasticsearch
// Or at least allow prefix matching
@Query("SELECT u FROM User u WHERE u.name LIKE :keyword%")
List<User> searchByPrefix(@Param("keyword") String keyword);
```

---

## Pagination Optimization

### Traditional Pagination Problem

```sql
-- Problem: Large offset has poor performance
SELECT * FROM orders ORDER BY id LIMIT 10 OFFSET 1000000;
-- Needs to scan 1000010 rows
```

### Cursor-based Pagination

```java
// Repository
@Query("SELECT o FROM Order o WHERE o.id > :lastId ORDER BY o.id ASC LIMIT :limit")
List<Order> findAfterId(@Param("lastId") Long lastId, @Param("limit") int limit);

// Service
public PageResponse<OrderResponse> getOrders(Long lastId, int size) {
    List<Order> orders = orderRepository.findAfterId(
        lastId != null ? lastId : 0,
        size + 1  // Fetch one extra to determine if there's next page
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

### Covering Index Optimization

```java
// First get IDs via covering index
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

// Then get full data by IDs
@Query("SELECT o FROM Order o WHERE o.id IN :ids ORDER BY o.createdAt DESC")
List<Order> findByIds(@Param("ids") List<Long> ids);

// Combined usage
public Page<Order> findByStatus(String status, int page, int size) {
    int offset = page * size;
    List<Long> ids = findIdsByStatus(status, size, offset);
    List<Order> orders = findByIds(ids);
    // ...
}
```

---

## Batch Operation Optimization

### Batch Insert Configuration

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

### Batch Insert Implementation

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

// Or use JpaRepository
@Transactional
public void batchInsert(List<Order> orders) {
    orderRepository.saveAll(orders);
}
```

### Batch Update

```java
// Use JPQL batch update
@Modifying
@Query("UPDATE Order o SET o.status = :status WHERE o.id IN :ids")
int bulkUpdateStatus(@Param("ids") List<Long> ids, @Param("status") OrderStatus status);

// Use JdbcTemplate
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

## Query Analysis Tools

### EXPLAIN Analysis

```java
// Show SQL execution plan in logs
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

### Slow Query Logging

```yaml
# PostgreSQL
logging:
  level:
    org.hibernate.SQL_SLOW: DEBUG

# Custom slow query detection
@Component
@Slf4j
public class SlowQueryInterceptor implements EmptyInterceptor {

    private static final long SLOW_QUERY_THRESHOLD_MS = 1000;

    @Override
    public String onPrepareStatement(String sql) {
        long start = System.currentTimeMillis();
        // ... execute query
        long elapsed = System.currentTimeMillis() - start;

        if (elapsed > SLOW_QUERY_THRESHOLD_MS) {
            log.warn("Slow query detected ({}ms): {}", elapsed, sql);
        }

        return sql;
    }
}
```

### Database Monitoring

```java
// Use Micrometer for monitoring
@Bean
public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
    return registry -> registry.config().commonTags("application", "my-app");
}

// Custom query metrics
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

## Checklist

### N+1 Queries

- [ ] Identify all database calls in loops
- [ ] Use JOIN FETCH or EntityGraph
- [ ] Configure @BatchSize
- [ ] Manual batch queries

### Index Optimization

- [ ] Create indexes for common query conditions
- [ ] Composite index column order is correct
- [ ] Avoid using functions on indexed columns
- [ ] Regularly analyze index usage

### Pagination Optimization

- [ ] Use cursor-based pagination for large datasets
- [ ] Avoid large OFFSET
- [ ] Consider using covering index

### Batch Operations

- [ ] Configure batch size
- [ ] Periodically flush and clear
- [ ] Use batch update instead of individual updates

### Monitoring

- [ ] Enable slow query logging
- [ ] Configure query metrics
- [ ] Regularly analyze execution plans
