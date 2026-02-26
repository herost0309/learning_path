# RESTful API 设计 Skill

> 确保 API 设计遵循 REST 原则和最佳实践

## 触发条件

- 命令: `/api-design`
- Pull Request: Controller 文件变更

---

## URL 设计规范

### 资源命名

```
# 使用名词复数
GET    /users                    # 获取用户列表
GET    /users/{id}               # 获取单个用户
POST   /users                    # 创建用户
PUT    /users/{id}               # 更新用户（完整更新）
PATCH  /users/{id}               # 更新用户（部分更新）
DELETE /users/{id}               # 删除用户

# 嵌套资源（最多两层）
GET    /users/{userId}/orders              # 用户的订单列表
GET    /users/{userId}/orders/{orderId}    # 用户的特定订单

# 避免超过两层的嵌套
# 错误: /users/{userId}/orders/{orderId}/items/{itemId}
# 正确: /order-items/{itemId}?orderId={orderId}
```

### URL 命名规则

```
# 使用 kebab-case（连字符）
/user-profiles          # 正确
/userProfiles           # 错误
/user_profiles          # 错误

# 使用小写
/users                  # 正确
/Users                  # 错误

# 避免动词
/users                  # 正确（使用 HTTP 方法表示动作）
/getUsers               # 错误
/createUser             # 错误

# 使用集合名词
/products               # 正确
/product-list           # 错误
```

---

## HTTP 方法语义

| Method | 用途 | 幂等性 | 安全性 | 请求体 | 响应体 |
|--------|------|--------|--------|--------|--------|
| GET | 获取资源 | 是 | 是 | 否 | 是 |
| POST | 创建资源 | 否 | 否 | 是 | 是 |
| PUT | 完整更新 | 是 | 否 | 是 | 是 |
| PATCH | 部分更新 | 否 | 否 | 是 | 是 |
| DELETE | 删除资源 | 是 | 否 | 否 | 可选 |
| HEAD | 获取头信息 | 是 | 是 | 否 | 否 |
| OPTIONS | 获取支持的方法 | 是 | 是 | 否 | 是 |

### 示例

```
# 获取列表
GET /users
Response: 200 OK

# 获取单个资源
GET /users/1
Response: 200 OK 或 404 Not Found

# 创建资源
POST /users
Request: { "name": "John", "email": "john@example.com" }
Response: 201 Created
Location: /users/1

# 完整更新（需要提供所有字段）
PUT /users/1
Request: { "name": "John Doe", "email": "john.doe@example.com" }
Response: 200 OK

# 部分更新（只提供要更新的字段）
PATCH /users/1
Request: { "name": "John Doe" }
Response: 200 OK

# 删除资源
DELETE /users/1
Response: 204 No Content
```

---

## HTTP 状态码

### 成功响应 (2xx)

| 状态码 | 说明 | 使用场景 |
|--------|------|----------|
| 200 OK | 成功 | GET、PUT、PATCH、DELETE |
| 201 Created | 创建成功 | POST |
| 202 Accepted | 请求已接受 | 异步操作 |
| 204 No Content | 成功无内容 | DELETE |
| 206 Partial Content | 部分内容 | 范围请求 |

### 客户端错误 (4xx)

| 状态码 | 说明 | 使用场景 |
|--------|------|----------|
| 400 Bad Request | 请求格式错误 | 参数错误 |
| 401 Unauthorized | 未认证 | 缺少认证信息 |
| 403 Forbidden | 无权限 | 已认证但无权限 |
| 404 Not Found | 资源不存在 | 资源未找到 |
| 405 Method Not Allowed | 方法不允许 | HTTP 方法不支持 |
| 406 Not Acceptable | 无法接受 | Accept 头不匹配 |
| 409 Conflict | 冲突 | 资源冲突（如重复创建） |
| 410 Gone | 已删除 | 资源已永久删除 |
| 415 Unsupported Media Type | 不支持的媒体类型 | Content-Type 错误 |
| 422 Unprocessable Entity | 语义错误 | 验证失败 |
| 429 Too Many Requests | 请求过于频繁 | 限流 |

### 服务端错误 (5xx)

| 状态码 | 说明 | 使用场景 |
|--------|------|----------|
| 500 Internal Server Error | 服务器内部错误 | 未预期的错误 |
| 502 Bad Gateway | 网关错误 | 上游服务错误 |
| 503 Service Unavailable | 服务不可用 | 服务维护/过载 |
| 504 Gateway Timeout | 网关超时 | 上游服务超时 |

---

## 统一响应格式

### 成功响应

```json
{
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "id": 1,
    "name": "John Doe"
  },
  "timestamp": "2026-02-24T10:00:00Z"
}
```

### 分页响应

```json
{
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "items": [
      { "id": 1, "name": "John" },
      { "id": 2, "name": "Jane" }
    ],
    "pagination": {
      "page": 1,
      "size": 20,
      "total": 100,
      "totalPages": 5,
      "hasNext": true,
      "hasPrevious": false
    }
  },
  "timestamp": "2026-02-24T10:00:00Z"
}
```

### 错误响应

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "errors": [
    {
      "field": "email",
      "message": "Invalid email format"
    },
    {
      "field": "name",
      "message": "Name is required"
    }
  ],
  "timestamp": "2026-02-24T10:00:00Z",
  "traceId": "abc-123-def-456"
}
```

### Java 实现

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
}

@Data
@Builder
public class PageResponse<T> {
    private List<T> items;
    private Pagination pagination;

    @Data
    @Builder
    public static class Pagination {
        private int page;
        private int size;
        private long total;
        private int totalPages;
        private boolean hasNext;
        private boolean hasPrevious;
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.<T>builder()
            .items(page.getContent())
            .pagination(Pagination.builder()
                .page(page.getNumber() + 1)
                .size(page.getSize())
                .total(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build())
            .build();
    }
}
```

---

## 查询参数设计

### 分页

```
GET /users?page=1&size=20
GET /users?offset=0&limit=20
```

### 排序

```
# 单字段排序
GET /users?sort=name&order=asc

# 多字段排序（逗号分隔）
GET /users?sort=name,email&order=asc,desc

# 使用 +/- 前缀
GET /users?sort=-createdAt,name  # createdAt 降序, name 升序
```

### 过滤

```
# 精确匹配
GET /users?status=active

# 多值匹配
GET /users?status=active,pending

# 范围查询
GET /orders?amount[min]=100&amount[max]=1000
GET /orders?createdAfter=2026-01-01
```

### 字段选择

```
# 只返回指定字段
GET /users?fields=id,name,email
```

### 搜索

```
# 全文搜索
GET /users?search=john

# 多字段搜索
GET /users?q=name:john,email:example.com
```

### 展开/嵌套

```
# 展开关联资源
GET /orders?include=items,user

# 控制嵌套深度
GET /orders?expand=items.product
```

---

## 版本控制

### URL 路径版本（推荐）

```
/api/v1/users
/api/v2/users
```

### 请求头版本

```
Accept: application/vnd.myapi.v1+json
Accept: application/vnd.myapi.v2+json
```

### 查询参数版本

```
/api/users?version=1
/api/users?version=2
```

### 版本变更策略

```
# 向后兼容的变更（不需要升级版本）
- 添加新的可选字段
- 添加新的端点
- 添加新的查询参数

# 不兼容的变更（需要升级版本）
- 删除或重命名字段
- 修改字段类型
- 修改 URL 结构
- 修改认证方式
```

---

## HATEOAS（超媒体驱动）

```json
{
  "data": {
    "id": 1,
    "name": "John Doe"
  },
  "_links": {
    "self": { "href": "/api/v1/users/1", "method": "GET" },
    "update": { "href": "/api/v1/users/1", "method": "PUT" },
    "delete": { "href": "/api/v1/users/1", "method": "DELETE" },
    "orders": { "href": "/api/v1/users/1/orders", "method": "GET" }
  }
}
```

---

## 安全最佳实践

### 认证头

```
# Bearer Token
Authorization: Bearer <access_token>

# API Key
X-API-Key: <api_key>
```

### 标准请求头

```
Content-Type: application/json
Accept: application/json
Accept-Language: zh-CN
X-Request-Id: <uuid>           # 请求追踪
X-Correlation-Id: <uuid>       # 关联ID
If-Match: <etag>               # 并发控制
If-None-Match: <etag>          # 缓存
```

### CORS 配置

```java
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("https://myapp.com"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Total-Count", "Link"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
```

---

## API 文档规范

### OpenAPI/Swagger 注解

```java
@Tag(name = "Users", description = "用户管理 API")
public class UserController {

    @Operation(
        summary = "获取用户列表",
        description = "支持分页、排序和过滤"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "成功获取列表",
            content = @Content(schema = @Schema(implementation = PageResponse.class))
        ),
        @ApiResponse(responseCode = "401", description = "未认证"),
        @ApiResponse(responseCode = "403", description = "无权限")
    })
    @Parameters({
        @Parameter(name = "page", description = "页码", example = "1"),
        @Parameter(name = "size", description = "每页数量", example = "20"),
        @Parameter(name = "status", description = "状态过滤",
                   schema = @Schema(implementation = UserStatus.class))
    })
    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> search(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) UserStatus status
    ) {
        // ...
    }
}
```

---

## 检查清单

### URL 设计

- [ ] 使用名词而非动词
- [ ] 使用复数形式
- [ ] 使用 kebab-case
- [ ] 层级不超过两层

### HTTP 方法

- [ ] 正确使用 HTTP 方法语义
- [ ] GET 请求不修改数据
- [ ] PUT 替换整个资源
- [ ] PATCH 部分更新

### 状态码

- [ ] 成功使用 2xx
- [ ] 客户端错误使用 4xx
- [ ] 服务端错误使用 5xx
- [ ] 正确区分 401 和 403

### 响应格式

- [ ] 统一的响应结构
- [ ] 包含时间戳
- [ ] 错误响应包含详细信息
- [ ] 分页响应包含分页信息

### 安全

- [ ] 敏感端点需要认证
- [ ] 正确的 CORS 配置
- [ ] 输入验证
- [ ] 速率限制
