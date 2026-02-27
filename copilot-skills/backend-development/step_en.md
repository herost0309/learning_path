# Backend Development Process Example: From Development to Testing

> This document demonstrates a complete backend feature development process, showing how to use the Skill collection to complete the full journey from requirements to testing

## Project Background

**Requirement**: Develop a "Product Management" module with CRUD functionality for products

**Tech Stack**: Java 17 + Spring Boot 3.x + PostgreSQL

---

## Step 1: Project Initialization

### 1.1 Create Project Directory Structure

```bash
# Create project directories
mkdir -p product-service/src/{main,test}/java/com/example/product
mkdir -p product-service/src/main/resources

# Enter project directory
cd product-service
```

### 1.2 Initialize Spring Boot Project

```bash
# Create project using Spring Initializr
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

### 1.3 Invoke Skill: Code Style Standards

**Command**: `/java-style`

**Purpose**: Ensure IDE and code style configuration is correct

**Skill Reference**: `code-style/java-spring-boot.md`

**Create Configuration Files**:

```bash
# Create .editorconfig
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

## Step 2: Define Entity Model

### 2.1 Design Entity Fields

**Product Entity Field Definition**:

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | Long | PK, Auto | Primary key |
| name | String | NOT NULL, 100 | Product name |
| sku | String | UNIQUE, 50 | Product code |
| description | String | 500 | Description |
| price | BigDecimal | NOT NULL, 19,2 | Price |
| stock | Integer | NOT NULL, DEFAULT 0 | Stock quantity |
| status | Enum | NOT NULL | Status |
| categoryId | Long | FK | Category ID |

### 2.2 Invoke Skill: CRUD Generator

**Command**: `/generate-crud`

**Skill Reference**: `feature-development/crud-generator.md`

**Interaction Example**:

```
👤 User: /generate-crud

🤖 Copilot: Please provide entity definition information:

1. Entity name: Product
2. Field list:
   - name: String, required, length=100
   - sku: String, required, unique, length=50
   - description: String, length=500
   - price: BigDecimal, required
   - stock: Integer, required, default=0
   - status: Enum (ACTIVE/INACTIVE/DISCONTINUED), required
   - categoryId: Long

🤖 Copilot: Generating code...
```

**Generated Files**:

```
src/main/java/com/example/product/
├── entity/
│   └── Product.java              # Entity class
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

## Step 3: Code Implementation and Review

### 3.1 View Generated Entity

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

### 3.2 Invoke Skill: RESTful API Design Check

**Command**: `/api-design`

**Skill Reference**: `feature-development/restful-api-design.md`

**Check Generated API Endpoints**:

```
✅ GET    /api/v1/products           # Get product list
✅ GET    /api/v1/products/{id}      # Get single product
✅ POST   /api/v1/products           # Create product
✅ PUT    /api/v1/products/{id}      # Update product
✅ DELETE /api/v1/products/{id}      # Delete product

Check results:
- ✅ URLs use plural nouns
- ✅ Use kebab-case
- ✅ API versioning /api/v1/
- ✅ HTTP method semantics correct
```

### 3.3 Invoke Skill: Database Query Optimization Check

**Command**: `/query-optimize`

**Skill Reference**: `database/query-optimization.md`

**Check Service Layer**:

```java
// Check point: N+1 queries

// ⚠️ Issue found: ProductService.java:45
public PageResponse<ProductResponse> search(ProductSearchRequest request) {
    Page<Product> products = productRepository.findAll(buildSpec(request), pageable);

    return products.stream()
        .map(product -> {
            // ⚠️ Potential N+1 issue: Query category for each product
            Category category = categoryRepository.findById(product.getCategoryId());
            return ProductResponse.from(product, category);
        });
}

// ✅ Fix: Use JOIN FETCH
@Query("SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.id = :id")
Optional<Product> findByIdWithCategory(@Param("id") Long id);
```

### 3.4 Invoke Skill: Security Check

**Command**: `/security-scan`

**Skill Reference**: `security/owasp-security-check.md`

**Security Audit Results**:

```
🔒 Security Scan Report

✅ A01 - Access Control
   - Recommendation: Add @PreAuthorize annotation

⚠️ A03 - Injection
   - Found: ProductRepository.java:25
   - Issue: Dynamic sort field not validated
   - Fix: Add sort field whitelist

✅ A02 - Cryptographic Failures
   - No sensitive data needs encryption

✅ A05 - Security Misconfiguration
   - Recommendation: Disable actuator details in production
```

**Fix Security Issues**:

```java
// ProductRepository.java - Fix dynamic sorting issue
public List<Product> findAll(String sortBy) {
    // Whitelist validation
    Set<String> allowedSortFields = Set.of("id", "name", "price", "createdAt");
    if (!allowedSortFields.contains(sortBy)) {
        throw new IllegalArgumentException("Invalid sort field: " + sortBy);
    }
    // ...
}
```

---

## Step 4: Generate Test Code

### 4.1 Invoke Skill: Unit Test Generation

**Command**: `/gen-unit-tests`

**Skill Reference**: `testing/unit-test-generation.md`

**Interaction Example**:

```
👤 User: /gen-unit-tests src/main/java/com/example/product/service/impl/ProductServiceImpl.java

🤖 Copilot: Analyzing source file...
🤖 Copilot: Detected methods requiring tests:
   - getById(Long id)
   - search(ProductSearchRequest request)
   - create(ProductCreateRequest request)
   - update(Long id, ProductUpdateRequest request)
   - delete(Long id)

🤖 Copilot: Generating test cases...
🤖 Copilot: Test file generated
```

**Generated Test File**:

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

    // ... more tests
}
```

### 4.2 Run Unit Tests

```bash
# Run all unit tests
mvn test

# Run specific test class
mvn test -Dtest=ProductServiceImplTest

# Run specific test method
mvn test -Dtest=ProductServiceImplTest$CreateTests#validInput_createsProduct

# Generate test coverage report
mvn jacoco:report
```

### 4.3 Invoke Skill: Integration Test Generation

**Command**: `/gen-integration-tests`

**Skill Reference**: `testing/integration-test-generation.md`

**Generated Integration Test**:

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

### 4.4 Run Integration Tests

```bash
# Run integration tests
mvn verify -Dtest=*IntegrationTest

# Run all tests (unit + integration)
mvn verify

# View test report
open target/site/jacoco/index.html
```

---

## Step 5: Code Review

### 5.1 Create Pull Request

```bash
# Create feature branch
git checkout -b feature/product-management

# Commit code
git add .
git commit -m "feat: add product management module

- Add Product entity with soft delete
- Implement CRUD operations
- Add unit and integration tests
- Configure database indexes

Refs: #123"

# Push to remote
git push origin feature/product-management

# Create PR (using GitHub CLI)
gh pr create \
  --title "feat: Add Product Management Module" \
  --body "## Changes

### New Features
- Product CRUD API
- Paginated search functionality
- SKU uniqueness validation

### Test Coverage
- Unit tests: 95%
- Integration tests: Main API endpoints

### Checklist
- [x] Code style check passed
- [x] Unit tests passed
- [x] Integration tests passed
- [x] Security check passed
"
```

### 5.2 Invoke Skill: PR Code Review

**Command**: Auto-triggered (when PR is created/updated)

**Skill Reference**: `code-review/pr-code-review.md`

**Automatic Review Report**:

```markdown
# Pull Request Code Review Report

## Review Summary

| Dimension | Status | Issues |
|-----------|--------|--------|
| Code Quality | ✅ Pass | 2 |
| Security | ✅ Pass | 0 |
| Performance | ⚠️ Attention Needed | 1 |
| Best Practices | ✅ Pass | 1 |

**Overall Assessment**: Can merge (recommend fixing performance issue)

---

## Minor Issues (2)

### 1. Log Level Suggestion

**File**: `ProductServiceImpl.java:35`

**Current Code**:
```java
log.info("Creating product with SKU: {}", request.getSku());
```

**Suggestion**: SKU may contain sensitive information, recommend using debug level in production

---

## Performance Issues (1)

### 1. Pagination Query Optimization Suggestion

**File**: `ProductRepository.java:28`

**Suggestion**: Create composite index for `status` and `created_at` to improve search performance

```sql
CREATE INDEX idx_product_status_created ON products(status, created_at DESC);
```

---

## Strengths Recognized ✨

1. Comprehensive test coverage
2. Correct use of soft delete
3. API design follows RESTful standards
4. Reasonable index configuration
5. Good exception handling
```

---

## Step 6: Generate API Documentation

### 6.1 Invoke Skill: API Documentation Generation

**Command**: `/gen-api-docs`

**Skill Reference**: `documentation/api-documentation.md`

**Add OpenAPI Annotations**:

```java
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Product Management", description = "Product Management API")
public class ProductController {

    @Operation(summary = "Get product list", description = "Supports pagination, sorting and multiple filter conditions")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> search(
        @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
        @Parameter(description = "Items per page") @RequestParam(defaultValue = "20") int size
    ) {
        // ...
    }
}
```

### 6.2 Access API Documentation

```bash
# Start application
mvn spring-boot:run

# Access Swagger UI
open http://localhost:8080/swagger-ui.html

# Access OpenAPI JSON
curl http://localhost:8080/v3/api-docs
```

---

## Step 7: Dockerize

### 7.1 Invoke Skill: Docker Best Practices

**Command**: `/docker-check`

**Skill Reference**: `devops/docker-best-practices.md`

**Create Dockerfile**:

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

### 7.2 Build and Run

```bash
# Build image
docker build -t product-service:latest .

# Run container
docker run -d \
  --name product-service \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/products \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  product-service:latest

# Check health status
docker ps
curl http://localhost:8080/actuator/health
```

---

## Complete Process Summary

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Development Process Overview                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Step 1: Project Initialization                                      │
│  ├── Create project structure                                        │
│  ├── Initialize Spring Boot                                          │
│  └── 🔧 /java-style → Configure code style                           │
│                                                                     │
│  Step 2: Generate Code                                               │
│  └── 🚀 /generate-crud → Generate CRUD code                          │
│      ├── Entity                                                      │
│      ├── DTO (Request/Response)                                      │
│      ├── Repository                                                  │
│      ├── Service                                                     │
│      └── Controller                                                  │
│                                                                     │
│  Step 3: Code Review                                                 │
│  ├── 📐 /api-design → API design check                               │
│  ├── ⚡ /query-optimize → Database query optimization                │
│  └── 🔒 /security-scan → Security check                              │
│                                                                     │
│  Step 4: Testing                                                     │
│  ├── 🧪 /gen-unit-tests → Generate unit tests                        │
│  ├── 🧪 /gen-integration-tests → Generate integration tests          │
│  └── mvn verify → Run all tests                                      │
│                                                                     │
│  Step 5: PR Review                                                   │
│  ├── Create Pull Request                                             │
│  └── 📋 Auto review → Generate review report                         │
│                                                                     │
│  Step 6: Documentation                                               │
│  └── 📚 /gen-api-docs → Generate API documentation                   │
│                                                                     │
│  Step 7: Deployment                                                  │
│  └── 🐳 /docker-check → Dockerize                                    │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Common Commands Quick Reference

| Stage | Command | Description |
|-------|---------|-------------|
| **Initialization** | `/java-style` | Configure code style |
| **Development** | `/generate-crud` | Generate CRUD code |
| **Development** | `/api-design` | API design check |
| **Optimization** | `/query-optimize` | Query optimization check |
| **Security** | `/security-scan` | Security scan |
| **Testing** | `/gen-unit-tests` | Generate unit tests |
| **Testing** | `/gen-integration-tests` | Generate integration tests |
| **Testing** | `mvn test` | Run unit tests |
| **Testing** | `mvn verify` | Run all tests |
| **Documentation** | `/gen-api-docs` | Generate API documentation |
| **Deployment** | `/docker-check` | Docker check |
| **Deployment** | `docker build` | Build image |

---

## Checklist

### Development Phase

- [ ] Code style follows standards
- [ ] API design follows RESTful
- [ ] No N+1 query issues
- [ ] No security vulnerabilities
- [ ] Appropriate index configuration

### Testing Phase

- [ ] Unit test coverage >= 80%
- [ ] Integration tests cover main scenarios
- [ ] Boundary condition tests
- [ ] Exception scenario tests

### Documentation Phase

- [ ] API documentation complete
- [ ] Request/Response examples correct
- [ ] Error codes fully documented

### Deployment Phase

- [ ] Dockerfile best practices
- [ ] Health check configured
- [ ] Resource limits set
- [ ] Log collection configured
