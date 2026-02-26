# API 文档生成 Skill

> 自动生成和维护 API 文档，确保文档与代码同步

## 触发条件

- 命令: `/gen-api-docs`
- 文件变更: Controller 文件

---

## OpenAPI/Swagger 配置

### Spring Boot 配置

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("My API")
                .version("v1.0.0")
                .description("API 文档描述")
                .contact(new Contact()
                    .name("API Support")
                    .email("support@example.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0")))
            .externalDocs(new ExternalDocumentation()
                .description("Wiki 文档")
                .url("https://wiki.example.com"))
            .addServersItem(new Server()
                .url("https://api.example.com")
                .description("生产环境"))
            .addServersItem(new Server()
                .url("https://api-dev.example.com")
                .description("开发环境"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT 认证")));
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

### application.yml 配置

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

## Controller 文档注解

### 完整注解示例

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(
    name = "User Management",
    description = "用户管理 API - 提供用户的增删改查功能"
)
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @Operation(
        summary = "获取用户详情",
        description = "根据用户ID获取用户的详细信息，包括基本资料和状态",
        operationId = "getUserById"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "成功获取用户信息",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(
                    name = "成功示例",
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
            description = "未认证",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "用户不存在",
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
            description = "用户ID",
            required = true,
            example = "1",
            schema = @Schema(type = "integer", format = "int64", minimum = "1")
        ),
        @Parameter(
            name = "X-Request-Id",
            description = "请求追踪ID",
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
        summary = "搜索用户列表",
        description = "支持分页、排序和多条件过滤"
    )
    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> search(
        @Parameter(description = "页码，从1开始")
        @RequestParam(defaultValue = "1") int page,

        @Parameter(description = "每页数量，范围1-100")
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,

        @Parameter(description = "排序字段")
        @RequestParam(defaultValue = "createdAt") String sortBy,

        @Parameter(description = "排序方向", schema = @Schema(allowableValues = {"asc", "desc"}))
        @RequestParam(defaultValue = "desc") String sortDirection,

        @Parameter(description = "用户状态过滤")
        @RequestParam(required = false) UserStatus status,

        @Parameter(description = "关键词搜索（姓名或邮箱）")
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
        summary = "创建用户",
        description = "创建新用户，邮箱不能重复"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "创建成功"),
        @ApiResponse(responseCode = "400", description = "参数验证失败"),
        @ApiResponse(responseCode = "409", description = "邮箱已存在")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> create(
        @Parameter(description = "用户创建请求", required = true)
        @Valid @RequestBody UserCreateRequest request
    ) {
        return ApiResponse.success(userService.create(request));
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UserUpdateRequest request
    ) {
        return ApiResponse.success(userService.update(id, request));
    }

    @Operation(summary = "删除用户")
    @ApiResponse(responseCode = "204", description = "删除成功")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }
}
```

---

## DTO 文档注解

### Request DTO

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户创建请求")
public class UserCreateRequest {

    @Schema(
        description = "用户姓名",
        example = "John Doe",
        minLength = 2,
        maxLength = 50,
        required = true
    )
    @NotBlank(message = "姓名不能为空")
    @Size(min = 2, max = 50, message = "姓名长度必须在2-50之间")
    private String name;

    @Schema(
        description = "用户邮箱",
        example = "john@example.com",
        maxLength = 100,
        required = true
    )
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(
        description = "用户密码",
        example = "Password123!",
        minLength = 8,
        required = true
    )
    @NotBlank(message = "密码不能为空")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
        message = "密码必须包含大小写字母和数字，至少8位"
    )
    private String password;

    @Schema(
        description = "用户角色",
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
@Schema(description = "用户响应")
public class UserResponse {

    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "用户姓名", example = "John Doe")
    private String name;

    @Schema(description = "用户邮箱", example = "john@example.com")
    private String email;

    @Schema(
        description = "用户状态",
        allowableValues = {"ACTIVE", "INACTIVE", "SUSPENDED"}
    )
    private UserStatus status;

    @Schema(
        description = "创建时间",
        example = "2026-02-24T10:00:00Z",
        type = "string",
        format = "date-time"
    )
    private String createdAt;

    @Schema(description = "更新时间", example = "2026-02-24T12:00:00Z")
    private String updatedAt;
}
```

### Page Response

```java
@Data
@Builder
@Schema(description = "分页响应")
public class PageResponse<T> {

    @Schema(description = "数据列表")
    private List<T> items;

    @Schema(description = "分页信息")
    private Pagination pagination;

    @Data
    @Builder
    @Schema(description = "分页信息")
    public static class Pagination {

        @Schema(description = "当前页码", example = "1")
        private int page;

        @Schema(description = "每页数量", example = "20")
        private int size;

        @Schema(description = "总记录数", example = "100")
        private long total;

        @Schema(description = "总页数", example = "5")
        private int totalPages;

        @Schema(description = "是否有下一页")
        private boolean hasNext;

        @Schema(description = "是否有上一页")
        private boolean hasPrevious;
    }
}
```

---

## API 文档模板

### README API 部分

```markdown
## API 文档

### 基础信息

| 项目 | 说明 |
|------|------|
| Base URL | `https://api.example.com/api/v1` |
| 认证方式 | Bearer Token (JWT) |
| 内容格式 | JSON |
| 字符编码 | UTF-8 |

### 认证

所有需要认证的接口需要在请求头中携带 Token：

```
Authorization: Bearer <access_token>
```

### 通用响应格式

**成功响应**
```json
{
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": { ... },
  "timestamp": "2026-02-24T10:00:00Z"
}
```

**错误响应**
```json
{
  "code": "ERROR_CODE",
  "message": "Error description",
  "errors": [...],
  "timestamp": "2026-02-24T10:00:00Z",
  "traceId": "abc-123"
}
```

### 错误码

| 错误码 | HTTP状态码 | 说明 |
|--------|------------|------|
| SUCCESS | 200 | 成功 |
| CREATED | 201 | 创建成功 |
| BAD_REQUEST | 400 | 请求参数错误 |
| UNAUTHORIZED | 401 | 未认证 |
| FORBIDDEN | 403 | 无权限 |
| NOT_FOUND | 404 | 资源不存在 |
| CONFLICT | 409 | 资源冲突 |
| VALIDATION_ERROR | 422 | 验证失败 |
| INTERNAL_ERROR | 500 | 服务器错误 |

### 用户管理 API

#### 获取用户列表

```
GET /api/v1/users
```

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认1 |
| size | int | 否 | 每页数量，默认20 |
| status | string | 否 | 状态过滤 |
| search | string | 否 | 关键词搜索 |

**响应示例**
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

## 检查清单

### OpenAPI 规范

- [ ] 配置完整的 API 信息
- [ ] 定义安全方案
- [ ] 配置服务器列表
- [ ] 分组管理 API

### Controller 注解

- [ ] 每个方法都有 @Operation
- [ ] 完整的 @ApiResponses
- [ ] 参数都有 @Parameter
- [ ] 示例值正确

### DTO 注解

- [ ] 类级别有 @Schema
- [ ] 字段有描述和示例
- [ ] 枚举有 allowableValues
- [ ] 必填字段标记 required

### 文档维护

- [ ] API 变更同步更新文档
- [ ] 示例值保持最新
- [ ] 错误码完整列出
- [ ] 版本号正确
