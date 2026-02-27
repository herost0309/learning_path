# RESTful API Design Skill

> Ensure API design follows REST principles and best practices

## Trigger Conditions

- Command: `/api-design`
- Pull Request: Controller file changes

---

## URL Design Standards

### Resource Naming

```
# Use plural nouns
GET    /users                    # Get user list
GET    /users/{id}               # Get single user
POST   /users                    # Create user
PUT    /users/{id}               # Update user (full update)
PATCH  /users/{id}               # Update user (partial update)
DELETE /users/{id}               # Delete user

# Nested resources (max 2 levels)
GET    /users/{userId}/orders              # User's order list
GET    /users/{userId}/orders/{orderId}    # User's specific order

# Avoid more than 2 levels of nesting
# Wrong: /users/{userId}/orders/{orderId}/items/{itemId}
# Correct: /order-items/{itemId}?orderId={orderId}
```

### URL Naming Rules

```
# Use kebab-case (hyphens)
/user-profiles          # Correct
/userProfiles           # Wrong
/user_profiles          # Wrong

# Use lowercase
/users                  # Correct
/Users                  # Wrong

# Avoid verbs
/users                  # Correct (use HTTP methods for actions)
/getUsers               # Wrong
/createUser             # Wrong

# Use collective nouns
/products               # Correct
/product-list           # Wrong
```

---

## HTTP Method Semantics

| Method | Purpose | Idempotent | Safe | Request Body | Response Body |
|--------|---------|------------|------|--------------|---------------|
| GET | Retrieve resource | Yes | Yes | No | Yes |
| POST | Create resource | No | No | Yes | Yes |
| PUT | Full update | Yes | No | Yes | Yes |
| PATCH | Partial update | No | No | Yes | Yes |
| DELETE | Delete resource | Yes | No | No | Optional |
| HEAD | Get headers | Yes | Yes | No | No |
| OPTIONS | Get supported methods | Yes | Yes | No | Yes |

### Examples

```
# Get list
GET /users
Response: 200 OK

# Get single resource
GET /users/1
Response: 200 OK or 404 Not Found

# Create resource
POST /users
Request: { "name": "John", "email": "john@example.com" }
Response: 201 Created
Location: /users/1

# Full update (all fields required)
PUT /users/1
Request: { "name": "John Doe", "email": "john.doe@example.com" }
Response: 200 OK

# Partial update (only fields to update)
PATCH /users/1
Request: { "name": "John Doe" }
Response: 200 OK

# Delete resource
DELETE /users/1
Response: 204 No Content
```

---

## HTTP Status Codes

### Success Responses (2xx)

| Status Code | Description | Use Case |
|-------------|-------------|----------|
| 200 OK | Success | GET, PUT, PATCH, DELETE |
| 201 Created | Created successfully | POST |
| 202 Accepted | Request accepted | Async operations |
| 204 No Content | Success with no content | DELETE |
| 206 Partial Content | Partial content | Range requests |

### Client Errors (4xx)

| Status Code | Description | Use Case |
|-------------|-------------|----------|
| 400 Bad Request | Invalid request format | Parameter errors |
| 401 Unauthorized | Not authenticated | Missing authentication |
| 403 Forbidden | No permission | Authenticated but no permission |
| 404 Not Found | Resource not found | Resource not found |
| 405 Method Not Allowed | Method not allowed | HTTP method not supported |
| 406 Not Acceptable | Not acceptable | Accept header mismatch |
| 409 Conflict | Conflict | Resource conflict (e.g., duplicate creation) |
| 410 Gone | Deleted | Resource permanently deleted |
| 415 Unsupported Media Type | Unsupported media type | Wrong Content-Type |
| 422 Unprocessable Entity | Semantic error | Validation failure |
| 429 Too Many Requests | Too many requests | Rate limiting |

### Server Errors (5xx)

| Status Code | Description | Use Case |
|-------------|-------------|----------|
| 500 Internal Server Error | Internal server error | Unexpected error |
| 502 Bad Gateway | Gateway error | Upstream service error |
| 503 Service Unavailable | Service unavailable | Service maintenance/overload |
| 504 Gateway Timeout | Gateway timeout | Upstream service timeout |

---

## Unified Response Format

### Success Response

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

### Paginated Response

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

### Error Response

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

### Java Implementation

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

## Query Parameter Design

### Pagination

```
GET /users?page=1&size=20
GET /users?offset=0&limit=20
```

### Sorting

```
# Single field sorting
GET /users?sort=name&order=asc

# Multi-field sorting (comma separated)
GET /users?sort=name,email&order=asc,desc

# Using +/- prefix
GET /users?sort=-createdAt,name  # createdAt desc, name asc
```

### Filtering

```
# Exact match
GET /users?status=active

# Multi-value match
GET /users?status=active,pending

# Range query
GET /orders?amount[min]=100&amount[max]=1000
GET /orders?createdAfter=2026-01-01
```

### Field Selection

```
# Return only specified fields
GET /users?fields=id,name,email
```

### Search

```
# Full-text search
GET /users?search=john

# Multi-field search
GET /users?q=name:john,email:example.com
```

### Expansion/Nesting

```
# Expand related resources
GET /orders?include=items,user

# Control nesting depth
GET /orders?expand=items.product
```

---

## Versioning

### URL Path Versioning (Recommended)

```
/api/v1/users
/api/v2/users
```

### Header Versioning

```
Accept: application/vnd.myapi.v1+json
Accept: application/vnd.myapi.v2+json
```

### Query Parameter Versioning

```
/api/users?version=1
/api/users?version=2
```

### Version Change Strategy

```
# Backward compatible changes (no version upgrade needed)
- Add new optional fields
- Add new endpoints
- Add new query parameters

# Breaking changes (version upgrade required)
- Remove or rename fields
- Change field types
- Modify URL structure
- Change authentication method
```

---

## HATEOAS (Hypermedia Driven)

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

## Security Best Practices

### Authentication Headers

```
# Bearer Token
Authorization: Bearer <access_token>

# API Key
X-API-Key: <api_key>
```

### Standard Request Headers

```
Content-Type: application/json
Accept: application/json
Accept-Language: en-US
X-Request-Id: <uuid>           # Request tracing
X-Correlation-Id: <uuid>       # Correlation ID
If-Match: <etag>               # Concurrency control
If-None-Match: <etag>          # Caching
```

### CORS Configuration

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

## API Documentation Standards

### OpenAPI/Swagger Annotations

```java
@Tag(name = "Users", description = "User Management API")
public class UserController {

    @Operation(
        summary = "Get user list",
        description = "Supports pagination, sorting and filtering"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved list",
            content = @Content(schema = @Schema(implementation = PageResponse.class))
        ),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "No permission")
    })
    @Parameters({
        @Parameter(name = "page", description = "Page number", example = "1"),
        @Parameter(name = "size", description = "Items per page", example = "20"),
        @Parameter(name = "status", description = "Status filter",
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

## Checklist

### URL Design

- [ ] Use nouns not verbs
- [ ] Use plural form
- [ ] Use kebab-case
- [ ] No more than 2 levels of nesting

### HTTP Methods

- [ ] Correct use of HTTP method semantics
- [ ] GET requests don't modify data
- [ ] PUT replaces entire resource
- [ ] PATCH for partial updates

### Status Codes

- [ ] Use 2xx for success
- [ ] Use 4xx for client errors
- [ ] Use 5xx for server errors
- [ ] Correctly distinguish 401 and 403

### Response Format

- [ ] Unified response structure
- [ ] Include timestamp
- [ ] Error responses include detailed information
- [ ] Paginated responses include pagination info

### Security

- [ ] Sensitive endpoints require authentication
- [ ] Correct CORS configuration
- [ ] Input validation
- [ ] Rate limiting
