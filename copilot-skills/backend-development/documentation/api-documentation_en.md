# API Documentation Generation Skill

> Automatically generate and maintain API documentation, ensure documentation stays in sync with code

## Trigger Conditions

- Command: `/gen-api-docs`
- File Changes: Controller files

---

## OpenAPI/Swagger Configuration

### Spring Boot Configuration

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("My API")
                .version("v1.0.0")
                .description("API documentation description")
                .contact(new Contact()
                    .name("API Support")
                    .email("support@example.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0")))
            .externalDocs(new ExternalDocumentation()
                .description("Wiki Documentation")
                .url("https://wiki.example.com"))
            .addServersItem(new Server()
                .url("https://api.example.com")
                .description("Production environment"))
            .addServersItem(new Server()
                .url("https://api-dev.example.com")
                .description("Development environment"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT Authentication")));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
            .group("public")
            .pathsToMatch("/api/v1/public/**")
            .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
            .group("admin")
            .pathsToMatch("/api/v1/admin/**")
            .addOpenApiMethodFilter(method -> method.isAnnotationPresent(PreAuthorize.class))
            .build();
    }
}
```

### application.yml Configuration

```yaml
springdoc:
  api-docs:
    enabled: true
    path: /api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: alpha
    doc-expansion: none
    display-request-duration: true
    show-extensions: true
  show-actuator: true
  default-flat-param-object: true
```

---

## Controller Documentation Annotations

### Complete Annotation Example

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(
    name = "User Management",
    description = "User Management API - Provides CRUD functionality for users"
)
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @Operation(
        summary = "Get user details",
        description = "Get user details by ID, including basic profile and status",
        operationId = "getUserById"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved user information",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(
                    name = "Success Example",
                    value = """
                        {
                          "code": "SUCCESS",
                          "message": "Operation completed successfully",
                          "data": {
                            "id": 1,
                            "name": "John Doe",
                            "email": "john@example.com",
                            "status": "ACTIVE"
                          },
                          "timestamp": "2026-02-24T10:00:00Z"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "User not found",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          "code": "NOT_FOUND",
                          "message": "User with id 999 not found",
                          "timestamp": "2026-02-24T10:00:00Z"
                        }
                        """
                )
            )
        )
    })
    @Parameters({
        @Parameter(
            name = "id",
            description = "User ID",
            required = true,
            example = "1",
            schema = @Schema(type = "integer", format = "int64", minimum = "1")
        ),
        @Parameter(
            name = "X-Request-Id",
            description = "Request trace ID",
            in = ParameterIn.HEADER,
            required = false,
            example = "abc-123-def-456"
        )
    })
    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getById(
        @PathVariable @Min(1) Long id,
        @RequestHeader(value = "X-Request-Id", required = false) String requestId
    ) {
        return ApiResponse.success(userService.getById(id));
    }

    @Operation(
        summary = "Search user list",
        description = "Supports pagination, sorting and multiple filter conditions"
    )
    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> search(
        @Parameter(description = "Page number, starting from 1")
        @RequestParam(defaultValue = "1") int page,

        @Parameter(description = "Items per page, range 1-100")
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,

        @Parameter(description = "Sort field")
        @RequestParam(defaultValue = "createdAt") String sortBy,

        @Parameter(description = "Sort direction", schema = @Schema(allowableValues = {"asc", "desc"}))
        @RequestParam(defaultValue = "desc") String sortDirection,

        @Parameter(description = "User status filter")
        @RequestParam(required = false) UserStatus status,

        @Parameter(description = "Keyword search (name or email)")
        @RequestParam(required = false) String search
    ) {
        UserSearchRequest request = UserSearchRequest.builder()
            .page(page)
            .size(size)
            .sortBy(sortBy)
            .sortDirection("asc".equalsIgnoreCase(sortDirection) ?
                Sort.Direction.ASC : Sort.Direction.DESC)
            .status(status)
            .search(search)
            .build();

        return ApiResponse.success(userService.search(request));
    }

    @Operation(
        summary = "Create user",
        description = "Create a new user, email cannot be duplicated"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created successfully"),
        @ApiResponse(responseCode = "400", description = "Parameter validation failed"),
        @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> create(
        @Parameter(description = "User creation request", required = true)
        @Valid @RequestBody UserCreateRequest request
    ) {
        return ApiResponse.success(userService.create(request));
    }

    @Operation(summary = "Update user")
    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UserUpdateRequest request
    ) {
        return ApiResponse.success(userService.update(id, request));
    }

    @Operation(summary = "Delete user")
    @ApiResponse(responseCode = "204", description = "Deleted successfully")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }
}
```

---

## DTO Documentation Annotations

### Request DTO

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User creation request")
public class UserCreateRequest {

    @Schema(
        description = "User name",
        example = "John Doe",
        minLength = 2,
        maxLength = 50,
        required = true
    )
    @NotBlank(message = "Name cannot be empty")
    @Size(min = 2, max = 50, message = "Name length must be between 2-50")
    private String name;

    @Schema(
        description = "User email",
        example = "john@example.com",
        maxLength = 100,
        required = true
    )
    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Invalid email format")
    private String email;

    @Schema(
        description = "User password",
        example = "Password123!",
        minLength = 8,
        required = true
    )
    @NotBlank(message = "Password cannot be empty")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
        message = "Password must contain uppercase and lowercase letters and numbers, at least 8 characters"
    )
    private String password;

    @Schema(
        description = "User role",
        allowableValues = {"USER", "ADMIN"},
        defaultValue = "USER"
    )
    @Builder.Default
    private Role role = Role.USER;
}
```

### Response DTO

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User response")
public class UserResponse {

    @Schema(description = "User ID", example = "1")
    private Long id;

    @Schema(description = "User name", example = "John Doe")
    private String name;

    @Schema(description = "User email", example = "john@example.com")
    private String email;

    @Schema(
        description = "User status",
        allowableValues = {"ACTIVE", "INACTIVE", "SUSPENDED"}
    )
    private UserStatus status;

    @Schema(
        description = "Created at",
        example = "2026-02-24T10:00:00Z",
        type = "string",
        format = "date-time"
    )
    private String createdAt;

    @Schema(description = "Updated at", example = "2026-02-24T12:00:00Z")
    private String updatedAt;
}
```

### Page Response

```java
@Data
@Builder
@Schema(description = "Paginated response")
public class PageResponse<T> {

    @Schema(description = "Data list")
    private List<T> items;

    @Schema(description = "Pagination information")
    private Pagination pagination;

    @Data
    @Builder
    @Schema(description = "Pagination information")
    public static class Pagination {

        @Schema(description = "Current page number", example = "1")
        private int page;

        @Schema(description = "Items per page", example = "20")
        private int size;

        @Schema(description = "Total records", example = "100")
        private long total;

        @Schema(description = "Total pages", example = "5")
        private int totalPages;

        @Schema(description = "Has next page")
        private boolean hasNext;

        @Schema(description = "Has previous page")
        private boolean hasPrevious;
    }
}
```

---

## API Documentation Template

### README API Section

```markdown
## API Documentation

### Basic Information

| Item | Description |
|------|-------------|
| Base URL | `https://api.example.com/api/v1` |
| Authentication | Bearer Token (JWT) |
| Content Format | JSON |
| Character Encoding | UTF-8 |

### Authentication

All endpoints requiring authentication need to include a token in the request header:

```
Authorization: Bearer <access_token>
```

### Common Response Format

**Success Response**
```json
{
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": { ... },
  "timestamp": "2026-02-24T10:00:00Z"
}
```

**Error Response**
```json
{
  "code": "ERROR_CODE",
  "message": "Error description",
  "errors": [...],
  "timestamp": "2026-02-24T10:00:00Z",
  "traceId": "abc-123"
}
```

### Error Codes

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| SUCCESS | 200 | Success |
| CREATED | 201 | Created successfully |
| BAD_REQUEST | 400 | Invalid request parameters |
| UNAUTHORIZED | 401 | Not authenticated |
| FORBIDDEN | 403 | No permission |
| NOT_FOUND | 404 | Resource not found |
| CONFLICT | 409 | Resource conflict |
| VALIDATION_ERROR | 422 | Validation failed |
| INTERNAL_ERROR | 500 | Server error |

### User Management API

#### Get User List

```
GET /api/v1/users
```

**Request Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| page | int | No | Page number, default 1 |
| size | int | No | Items per page, default 20 |
| status | string | No | Status filter |
| search | string | No | Keyword search |

**Response Example**
```json
{
  "code": "SUCCESS",
  "data": {
    "items": [
      {
        "id": 1,
        "name": "John Doe",
        "email": "john@example.com",
        "status": "ACTIVE"
      }
    ],
    "pagination": {
      "page": 1,
      "size": 20,
      "total": 100,
      "totalPages": 5
    }
  }
}
```
```

---

## Checklist

### OpenAPI Specification

- [ ] Complete API information configured
- [ ] Security schemes defined
- [ ] Server list configured
- [ ] API group management

### Controller Annotations

- [ ] Every method has @Operation
- [ ] Complete @ApiResponses
- [ ] All parameters have @Parameter
- [ ] Example values are correct

### DTO Annotations

- [ ] Class-level @Schema present
- [ ] Fields have description and examples
- [ ] Enums have allowableValues
- [ ] Required fields marked

### Documentation Maintenance

- [ ] API changes synchronized with documentation
- [ ] Example values kept up to date
- [ ] Error codes completely listed
- [ ] Version number correct
