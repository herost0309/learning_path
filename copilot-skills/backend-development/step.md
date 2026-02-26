# 后端开发流程示例：从开发到测试

> 本文档演示一个完整的后端功能开发流程，展示如何使用 Skill 集合完成从需求到测试的全过程

## 项目背景

**需求**: 开发一个"产品管理"模块，包含产品的 CRUD 功能

**技术栈**: Java 17 + Spring Boot 3.x + PostgreSQL

---

## Step 1: 项目初始化

### 1.1 创建项目目录结构

```bash
# 创建项目目录
mkdir -p product-service/src/{main,test}/java/com/example/product
mkdir -p product-service/src/main/resources

# 进入项目目录
cd product-service
```

### 1.2 初始化 Spring Boot 项目

```bash
# 使用 Spring Initializr 创建项目
curl https://start.spring.io/starter.zip \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=3.2.0 \
  -d baseDir=. \
  -d groupId=com.example \
  -d artifactId=product-service \
  -d name=product-service \
  -d packageName=com.example.product \
  -d javaVersion=17 \
  -d dependencies=web,data-jpa,validation,postgresql,lombok \
  -o project.zip && unzip project.zip && rm project.zip
```

### 1.3 调用 Skill: 代码风格规范

**命令**: `/java-style`

**目的**: 确保 IDE 和代码风格配置正确

**Skill 参考**: `code-style/java-spring-boot.md`

**创建配置文件**:

```bash
# 创建 .editorconfig
cat > .editorconfig << 'EOF'
root = true

[*]
charset = utf-8
end_of_line = lf
indent_size = 4
indent_style = space
insert_final_newline = true
max_line_length = 120
trim_trailing_whitespace = true

[*.java]
indent_size = 4

[*.yml]
indent_size = 2

[*.xml]
indent_size = 4
EOF
```

---

## Step 2: 定义实体模型

### 2.1 设计实体字段

**产品实体字段定义**:

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Long | PK, Auto | 主键 |
| name | String | NOT NULL, 100 | 产品名称 |
| sku | String | UNIQUE, 50 | 产品编码 |
| description | String | 500 | 描述 |
| price | BigDecimal | NOT NULL, 19,2 | 价格 |
| stock | Integer | NOT NULL, DEFAULT 0 | 库存 |
| status | Enum | NOT NULL | 状态 |
| categoryId | Long | FK | 分类ID |

### 2.2 调用 Skill: CRUD 生成器

**命令**: `/generate-crud`

**Skill 参考**: `feature-development/crud-generator.md`

**交互示例**:

```
👤 用户: /generate-crud

🤖 Copilot: 请提供实体定义信息：

1. 实体名称: Product
2. 字段列表:
   - name: String, required, length=100
   - sku: String, required, unique, length=50
   - description: String, length=500
   - price: BigDecimal, required
   - stock: Integer, required, default=0
   - status: Enum (ACTIVE/INACTIVE/DISCONTINUED), required
   - categoryId: Long

🤖 Copilot: 正在生成代码...
```

**生成的文件**:

```
src/main/java/com/example/product/
├── entity/
│   └── Product.java              # 实体类
├── dto/
│   ├── request/
│   │   ├── ProductCreateRequest.java
│   │   ├── ProductUpdateRequest.java
│   │   └── ProductSearchRequest.java
│   └── response/
│       └── ProductResponse.java
├── repository/
│   └── ProductRepository.java
├── service/
│   ├── ProductService.java
│   └── impl/
│       └── ProductServiceImpl.java
└── controller/
    └── ProductController.java
```

---

## Step 3: 代码实现与审查

### 3.1 查看生成的 Entity

```java
// src/main/java/com/example/product/entity/Product.java
@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_sku", columnList = "sku", unique = true),
    @Index(name = "idx_product_status", columnList = "status"),
    @Index(name = "idx_product_category", columnList = "category_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SQLDelete(sql = "UPDATE products SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    @Builder.Default
    private Integer stock = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    @Column(name = "category_id")
    private Long categoryId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
```

### 3.2 调用 Skill: RESTful API 设计检查

**命令**: `/api-design`

**Skill 参考**: `feature-development/restful-api-design.md`

**检查生成的 API 端点**:

```
✅ GET    /api/v1/products           # 获取产品列表
✅ GET    /api/v1/products/{id}      # 获取单个产品
✅ POST   /api/v1/products           # 创建产品
✅ PUT    /api/v1/products/{id}      # 更新产品
✅ DELETE /api/v1/products/{id}      # 删除产品

检查结果:
- ✅ URL 使用名词复数
- ✅ 使用 kebab-case
- ✅ API 版本控制 /api/v1/
- ✅ HTTP 方法语义正确
```

### 3.3 调用 Skill: 数据库查询优化检查

**命令**: `/query-optimize`

**Skill 参考**: `database/query-optimization.md`

**检查 Service 层**:

```java
// 检查点: 是否存在 N+1 查询

// ⚠️ 发现问题: ProductService.java:45
public PageResponse<ProductResponse> search(ProductSearchRequest request) {
    Page<Product> products = productRepository.findAll(buildSpec(request), pageable);

    return products.stream()
        .map(product -> {
            // ⚠️ 潜在的 N+1 问题: 每个产品都查询分类
            Category category = categoryRepository.findById(product.getCategoryId());
            return ProductResponse.from(product, category);
        });
}

// ✅ 修复方案: 使用 JOIN FETCH
@Query("SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.id = :id")
Optional<Product> findByIdWithCategory(@Param("id") Long id);
```

### 3.4 调用 Skill: 安全检查

**命令**: `/security-scan`

**Skill 参考**: `security/owasp-security-check.md`

**安全审查结果**:

```
🔒 安全扫描报告

✅ A01 - 访问控制
   - 建议: 添加 @PreAuthorize 注解

⚠️ A03 - 注入攻击
   - 发现: ProductRepository.java:25
   - 问题: 动态排序字段未验证
   - 修复: 添加排序字段白名单

✅ A02 - 加密失败
   - 无敏感数据需要加密

✅ A05 - 安全配置
   - 建议: 生产环境关闭 actuator 详细信息
```

**修复安全问题**:

```java
// ProductRepository.java - 修复动态排序问题
public List<Product> findAll(String sortBy) {
    // 白名单验证
    Set<String> allowedSortFields = Set.of("id", "name", "price", "createdAt");
    if (!allowedSortFields.contains(sortBy)) {
        throw new IllegalArgumentException("Invalid sort field: " + sortBy);
    }
    // ...
}
```

---

## Step 4: 生成测试代码

### 4.1 调用 Skill: 单元测试生成

**命令**: `/gen-unit-tests`

**Skill 参考**: `testing/unit-test-generation.md`

**交互示例**:

```
👤 用户: /gen-unit-tests src/main/java/com/example/product/service/impl/ProductServiceImpl.java

🤖 Copilot: 正在分析源文件...
🤖 Copilot: 检测到以下方法需要测试:
   - getById(Long id)
   - search(ProductSearchRequest request)
   - create(ProductCreateRequest request)
   - update(Long id, ProductUpdateRequest request)
   - delete(Long id)

🤖 Copilot: 正在生成测试用例...
🤖 Copilot: 已生成测试文件
```

**生成的测试文件**:

```java
// src/test/java/com/example/product/service/impl/ProductServiceImplTest.java
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Tests")
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    @Nested
    @DisplayName("GetById Method")
    class GetByIdTests {

        @Test
        @DisplayName("should return product when exists")
        void existingProduct_returnsProduct() {
            // Arrange
            Long productId = 1L;
            Product product = Product.builder()
                .id(productId)
                .name("Test Product")
                .sku("SKU-001")
                .price(BigDecimal.valueOf(99.99))
                .stock(100)
                .status(ProductStatus.ACTIVE)
                .build();

            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            // Act
            ProductResponse result = productService.getById(productId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(productId);
            assertThat(result.getName()).isEqualTo("Test Product");
        }

        @Test
        @DisplayName("should throw exception when product not found")
        void nonExistingProduct_throwsException() {
            // Arrange
            Long productId = 999L;
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> productService.getById(productId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product");
        }
    }

    @Nested
    @DisplayName("Create Method")
    class CreateTests {

        @Test
        @DisplayName("should create product with valid data")
        void validInput_createsProduct() {
            // Arrange
            ProductCreateRequest request = ProductCreateRequest.builder()
                .name("New Product")
                .sku("SKU-002")
                .price(BigDecimal.valueOf(49.99))
                .stock(50)
                .build();

            when(productRepository.existsBySku(request.getSku())).thenReturn(false);

            Product savedProduct = Product.builder()
                .id(1L)
                .name(request.getName())
                .sku(request.getSku())
                .price(request.getPrice())
                .stock(request.getStock())
                .status(ProductStatus.ACTIVE)
                .build();

            when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

            // Act
            ProductResponse result = productService.create(request);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("New Product");
        }

        @Test
        @DisplayName("should throw exception when SKU already exists")
        void duplicateSku_throwsException() {
            // Arrange
            ProductCreateRequest request = ProductCreateRequest.builder()
                .name("New Product")
                .sku("SKU-EXISTING")
                .build();

            when(productRepository.existsBySku(request.getSku())).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(DuplicateSkuException.class);
        }
    }

    // ... 更多测试
}
```

### 4.2 运行单元测试

```bash
# 运行所有单元测试
mvn test

# 运行指定测试类
mvn test -Dtest=ProductServiceImplTest

# 运行指定测试方法
mvn test -Dtest=ProductServiceImplTest$CreateTests#validInput_createsProduct

# 生成测试覆盖率报告
mvn jacoco:report
```

### 4.3 调用 Skill: 集成测试生成

**命令**: `/gen-integration-tests`

**Skill 参考**: `testing/integration-test-generation.md`

**生成的集成测试**:

```java
// src/test/java/com/example/product/controller/ProductControllerIntegrationTest.java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
@DisplayName("Product Controller Integration Tests")
class ProductControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Nested
    @DisplayName("POST /api/v1/products")
    class CreateProductTests {

        @Test
        @DisplayName("should return 201 and created product")
        void validData_returns201() throws Exception {
            // Arrange
            ProductCreateRequest request = ProductCreateRequest.builder()
                .name("Test Product")
                .sku("SKU-001")
                .price(BigDecimal.valueOf(99.99))
                .stock(100)
                .build();

            // Act & Assert
            mockMvc.perform(post("/api/v1/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name").value("Test Product"));

            // Verify in database
            Optional<Product> saved = productRepository.findBySku("SKU-001");
            assertThat(saved).isPresent();
        }

        @Test
        @DisplayName("should return 409 when SKU already exists")
        void duplicateSku_returns409() throws Exception {
            // Arrange
            Product existing = Product.builder()
                .name("Existing Product")
                .sku("SKU-001")
                .price(BigDecimal.TEN)
                .stock(10)
                .status(ProductStatus.ACTIVE)
                .build();
            productRepository.save(existing);

            ProductCreateRequest request = ProductCreateRequest.builder()
                .name("New Product")
                .sku("SKU-001")
                .price(BigDecimal.valueOf(99.99))
                .stock(100)
                .build();

            // Act & Assert
            mockMvc.perform(post("/api/v1/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SKU"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products")
    class ListProductsTests {

        @Test
        @DisplayName("should return paginated products")
        void multipleProducts_returnsPaginatedResult() throws Exception {
            // Arrange
            for (int i = 1; i <= 25; i++) {
                Product product = Product.builder()
                    .name("Product " + i)
                    .sku("SKU-" + String.format("%03d", i))
                    .price(BigDecimal.valueOf(i * 10))
                    .stock(i * 10)
                    .status(ProductStatus.ACTIVE)
                    .build();
                productRepository.save(product);
            }

            // Act & Assert
            mockMvc.perform(get("/api/v1/products")
                    .param("page", "1")
                    .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(10))
                .andExpect(jsonPath("$.data.pagination.total").value(25))
                .andExpect(jsonPath("$.data.pagination.totalPages").value(3));
        }
    }
}
```

### 4.4 运行集成测试

```bash
# 运行集成测试
mvn verify -Dtest=*IntegrationTest

# 运行所有测试（单元测试 + 集成测试）
mvn verify

# 查看测试报告
open target/site/jacoco/index.html
```

---

## Step 5: 代码审查

### 5.1 创建 Pull Request

```bash
# 创建功能分支
git checkout -b feature/product-management

# 提交代码
git add .
git commit -m "feat: add product management module

- Add Product entity with soft delete
- Implement CRUD operations
- Add unit and integration tests
- Configure database indexes

Refs: #123"

# 推送到远程
git push origin feature/product-management

# 创建 PR (使用 GitHub CLI)
gh pr create \
  --title "feat: Add Product Management Module" \
  --body "## 变更说明

### 新增功能
- 产品 CRUD API
- 分页搜索功能
- SKU 唯一性校验

### 测试覆盖
- 单元测试: 95%
- 集成测试: 主要 API 端点

### 检查清单
- [x] 代码风格检查通过
- [x] 单元测试通过
- [x] 集成测试通过
- [x] 安全检查无问题
"
```

### 5.2 调用 Skill: PR 代码审查

**命令**: 自动触发（PR 创建/更新时）

**Skill 参考**: `code-review/pr-code-review.md`

**自动审查报告**:

```markdown
# Pull Request 代码审查报告

## 审查总结

| 维度 | 状态 | 问题数 |
|------|------|--------|
| 代码质量 | ✅ 通过 | 2 |
| 安全性 | ✅ 通过 | 0 |
| 性能 | ⚠️ 需注意 | 1 |
| 最佳实践 | ✅ 通过 | 1 |

**总体评价**: 可以合并（建议修复性能问题）

---

## Minor 问题 (2)

### 1. 日志级别建议

**文件**: `ProductServiceImpl.java:35`

**当前代码**:
```java
log.info("Creating product with SKU: {}", request.getSku());
```

**建议**: SKU 可能包含敏感信息，生产环境建议使用 debug 级别

---

## 性能问题 (1)

### 1. 分页查询优化建议

**文件**: `ProductRepository.java:28`

**建议**: 为 `status` 和 `created_at` 创建复合索引，提升搜索性能

```sql
CREATE INDEX idx_product_status_created ON products(status, created_at DESC);
```

---

## 优点认可 ✨

1. 完善的测试覆盖
2. 正确使用软删除
3. API 设计遵循 RESTful 规范
4. 合理的索引配置
5. 良好的异常处理
```

---

## Step 6: 生成 API 文档

### 6.1 调用 Skill: API 文档生成

**命令**: `/gen-api-docs`

**Skill 参考**: `documentation/api-documentation.md`

**添加 OpenAPI 注解**:

```java
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Product Management", description = "产品管理 API")
public class ProductController {

    @Operation(summary = "获取产品列表", description = "支持分页、排序和多条件过滤")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功"),
        @ApiResponse(responseCode = "400", description = "参数错误")
    })
    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> search(
        @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
        @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size
    ) {
        // ...
    }
}
```

### 6.2 访问 API 文档

```bash
# 启动应用
mvn spring-boot:run

# 访问 Swagger UI
open http://localhost:8080/swagger-ui.html

# 访问 OpenAPI JSON
curl http://localhost:8080/v3/api-docs
```

---

## Step 7: Docker 化

### 7.1 调用 Skill: Docker 最佳实践

**命令**: `/docker-check`

**Skill 参考**: `devops/docker-best-practices.md`

**创建 Dockerfile**:

```dockerfile
# Dockerfile
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
USER appuser
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s \
    CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

### 7.2 构建和运行

```bash
# 构建镜像
docker build -t product-service:latest .

# 运行容器
docker run -d \
  --name product-service \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/products \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  product-service:latest

# 检查健康状态
docker ps
curl http://localhost:8080/actuator/health
```

---

## 完整流程总结

```
┌─────────────────────────────────────────────────────────────────────┐
│                        开发流程总览                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Step 1: 项目初始化                                                  │
│  ├── 创建项目结构                                                    │
│  ├── 初始化 Spring Boot                                              │
│  └── 🔧 /java-style → 配置代码风格                                   │
│                                                                     │
│  Step 2: 生成代码                                                    │
│  └── 🚀 /generate-crud → 生成 CRUD 代码                              │
│      ├── Entity                                                      │
│      ├── DTO (Request/Response)                                      │
│      ├── Repository                                                  │
│      ├── Service                                                     │
│      └── Controller                                                  │
│                                                                     │
│  Step 3: 代码审查                                                    │
│  ├── 📐 /api-design → API 设计检查                                   │
│  ├── ⚡ /query-optimize → 数据库查询优化                              │
│  └── 🔒 /security-scan → 安全检查                                    │
│                                                                     │
│  Step 4: 测试                                                        │
│  ├── 🧪 /gen-unit-tests → 生成单元测试                               │
│  ├── 🧪 /gen-integration-tests → 生成集成测试                        │
│  └── mvn verify → 运行所有测试                                       │
│                                                                     │
│  Step 5: PR 审查                                                     │
│  ├── 创建 Pull Request                                               │
│  └── 📋 自动审查 → 生成审查报告                                       │
│                                                                     │
│  Step 6: 文档                                                        │
│  └── 📚 /gen-api-docs → 生成 API 文档                                │
│                                                                     │
│  Step 7: 部署                                                        │
│  └── 🐳 /docker-check → Docker 化                                    │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 常用命令速查

| 阶段 | 命令 | 说明 |
|------|------|------|
| **初始化** | `/java-style` | 配置代码风格 |
| **开发** | `/generate-crud` | 生成 CRUD 代码 |
| **开发** | `/api-design` | API 设计检查 |
| **优化** | `/query-optimize` | 查询优化检查 |
| **安全** | `/security-scan` | 安全扫描 |
| **测试** | `/gen-unit-tests` | 生成单元测试 |
| **测试** | `/gen-integration-tests` | 生成集成测试 |
| **测试** | `mvn test` | 运行单元测试 |
| **测试** | `mvn verify` | 运行所有测试 |
| **文档** | `/gen-api-docs` | 生成 API 文档 |
| **部署** | `/docker-check` | Docker 检查 |
| **部署** | `docker build` | 构建镜像 |

---

## 检查清单

### 开发阶段

- [ ] 代码风格符合规范
- [ ] API 设计遵循 RESTful
- [ ] 无 N+1 查询问题
- [ ] 无安全漏洞
- [ ] 适当的索引配置

### 测试阶段

- [ ] 单元测试覆盖率 >= 80%
- [ ] 集成测试覆盖主要场景
- [ ] 边界条件测试
- [ ] 异常情况测试

### 文档阶段

- [ ] API 文档完整
- [ ] 请求/响应示例正确
- [ ] 错误码说明完整

### 部署阶段

- [ ] Dockerfile 最佳实践
- [ ] 健康检查配置
- [ ] 资源限制设置
- [ ] 日志收集配置
