# CRUD Code Generator Skill

> Automatically generate complete CRUD code (Entity, DTO, Repository, Service, Controller, Tests) based on entity definition

## Trigger Conditions

- Command: `/generate-crud`
- Parameters: Entity name, field list

---

## Entity Definition Example

```yaml
entity_name: User
fields:
  - name: name
    type: String
    required: true
    length: 50
  - name: email
    type: String
    required: true
    unique: true
    length: 100
  - name: phone
    type: String
    required: false
    length: 20
  - name: status
    type: Enum
    enumType: UserStatus
    required: true
  - name: birthDate
    type: LocalDate
    required: false
```

---

## Generation Templates

### Entity Template

```java
@Entity
@Table(name = "{{ table_name }}", indexes = {
    @Index(name = "idx_{{ entity_name }}_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SQLDelete(sql = "UPDATE {{ table_name }} SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@ToString(exclude = {"{{ relations }}"})
public class {{ EntityName }} {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    {{ #each fields }}
    {{ #if equals type 'String' }}
    @Column({{ #if required }}nullable = false, {{ /if }}{{ #if unique }}unique = true, {{ /if }}length = {{ length }})
    {{ /if }}
    {{ #if equals type 'BigDecimal' }}
    @Column(precision = {{ precision | default 19 }}, scale = {{ scale | default 2 }})
    {{ /if }}
    {{ #if equals type 'LocalDateTime' }}
    @Column(name = "{{ snake_case name }}")
    {{ /if }}
    {{ #if equals type 'LocalDate' }}
    @Column(name = "{{ snake_case name }}")
    {{ /if }}
    {{ #if equals type 'Enum' }}
    @Enumerated(EnumType.STRING)
    @Column(nullable = {{ #if required }}false{{ else }}true{{ /if }}, length = 20)
    {{ /if }}
    private {{ type }} {{ name }};

    {{ /each }}

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Business methods
    public void activate() {
        this.status = {{ EntityName }}Status.ACTIVE;
    }

    public void deactivate() {
        this.status = {{ EntityName }}Status.INACTIVE;
    }
}
```

### Create Request DTO

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class {{ EntityName }}CreateRequest {

    {{ #each fields }}
    {{ #unless equals name 'id' }}
    {{ #unless equals name 'createdAt' }}
    {{ #unless equals name 'updatedAt' }}
    {{ #unless equals name 'deletedAt' }}
    {{ #unless equals name 'status' }}
    {{ #if required }}
    @NotBlank(message = "{{ name }} cannot be empty")
    {{ /if }}
    {{ #if equals type 'String' }}
    {{ #if length }}
    @Size(max = {{ length }}, message = "{{ name }} length cannot exceed {{ length }}")
    {{ /if }}
    {{ /if }}
    {{ #if equals name 'email' }}
    @Email(message = "Invalid email format")
    {{ /if }}
    private {{ type }} {{ name }};

    {{ /unless }}
    {{ /unless }}
    {{ /unless }}
    {{ /unless }}
    {{ /unless }}
    {{ /each }}
}
```

### Update Request DTO

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class {{ EntityName }}UpdateRequest {

    {{ #each fields }}
    {{ #unless equals name 'id' }}
    {{ #unless equals name 'createdAt' }}
    {{ #unless equals name 'updatedAt' }}
    {{ #unless equals name 'deletedAt' }}
    {{ #unless equals name 'status' }}
    {{ #if equals type 'String' }}
    {{ #if length }}
    @Size(max = {{ length }}, message = "{{ name }} length cannot exceed {{ length }}")
    {{ /if }}
    {{ /if }}
    {{ #if equals name 'email' }}
    @Email(message = "Invalid email format")
    {{ /if }}
    private {{ type }} {{ name }};

    {{ /unless }}
    {{ /unless }}
    {{ /unless }}
    {{ /unless }}
    {{ /unless }}
    {{ /each }}
}
```

### Response DTO

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class {{ EntityName }}Response {

    private Long id;

    {{ #each fields }}
    {{ #unless equals name 'deletedAt' }}
    private {{ type }} {{ name }};

    {{ /unless }}
    {{ /each }}

    public static {{ EntityName }}Response from({{ EntityName }} entity) {
        return {{ EntityName }}Response.builder()
            .id(entity.getId())
            {{ #each fields }}
            {{ #unless equals name 'deletedAt' }}
            .{{ name }}(entity.get{{ pascal_case name }}())
            {{ /unless }}
            {{ /each }}
            .build();
    }
}
```

### Search Request DTO

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class {{ EntityName }}SearchRequest {

    {{ #each fields }}
    {{ #if equals type 'String' }}
    private String {{ name }};
    {{ /if }}
    {{ #if equals type 'Enum' }}
    private {{ enumType }} status;
    {{ /if }}
    {{ /each }}

    @Builder.Default
    private int page = 1;

    @Builder.Default
    private int size = 20;

    @Builder.Default
    private String sortBy = "createdAt";

    @Builder.Default
    private Sort.Direction sortDirection = Sort.Direction.DESC;
}
```

### Repository

```java
@Repository
public interface {{ EntityName }}Repository extends JpaRepository<{{ EntityName }}, Long>, JpaSpecificationExecutor<{{ EntityName }}> {

    {{ #each fields }}
    {{ #if unique }}
    Optional<{{ EntityName }}> findBy{{ pascal_case name }}({{ type }} {{ name }});
    boolean existsBy{{ pascal_case name }}({{ type }} {{ name }});

    {{ /if }}
    {{ /each }}
    @Query("SELECT e FROM {{ EntityName }} e WHERE e.status = :status AND e.deletedAt IS NULL")
    List<{{ EntityName }}> findByStatus(@Param("status") {{ EntityName }}Status status);

    Page<{{ EntityName }}> findByStatus(Pageable pageable, {{ EntityName }}Status status);

    @Modifying
    @Query("UPDATE {{ EntityName }} e SET e.deletedAt = CURRENT_TIMESTAMP WHERE e.id = :id")
    void softDelete(@Param("id") Long id);
}
```

### Service Interface

```java
public interface {{ EntityName }}Service {
    {{ EntityName }}Response getById(Long id);
    PageResponse<{{ EntityName }}Response> search({{ EntityName }}SearchRequest request);
    {{ EntityName }}Response create({{ EntityName }}CreateRequest request);
    {{ EntityName }}Response update(Long id, {{ EntityName }}UpdateRequest request);
    void delete(Long id);
    boolean existsById(Long id);
    {{ #each fields }}
    {{ #if unique }}
    boolean existsBy{{ pascal_case name }}({{ type }} {{ name }});
    {{ /if }}
    {{ /each }}
}
```

### Service Implementation

```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class {{ EntityName }}ServiceImpl implements {{ EntityName }}Service {

    private final {{ EntityName }}Repository repository;

    @Override
    public {{ EntityName }}Response getById(Long id) {
        return repository.findById(id)
            .map({{ EntityName }}Response::from)
            .orElseThrow(() -> new ResourceNotFoundException("{{ EntityName }}", id));
    }

    @Override
    public PageResponse<{{ EntityName }}Response> search({{ EntityName }}SearchRequest request) {
        Specification<{{ EntityName }}> spec = buildSpecification(request);
        Pageable pageable = PageRequest.of(
            request.getPage() - 1,
            request.getSize(),
            Sort.by(request.getSortDirection(), request.getSortBy())
        );

        Page<{{ EntityName }}> page = repository.findAll(spec, pageable);
        return PageResponse.of(page.map({{ EntityName }}Response::from));
    }

    @Override
    @Transactional
    public {{ EntityName }}Response create({{ EntityName }}CreateRequest request) {
        {{ #each fields }}
        {{ #if unique }}
        // Check if {{ name }} already exists
        if (repository.existsBy{{ pascal_case name }}(request.get{{ pascal_case name }}())) {
            throw new DuplicateResourceException("{{ name }}", request.get{{ pascal_case name }}());
        }
        {{ /if }}
        {{ /each }}

        {{ EntityName }} entity = {{ EntityName }}.builder()
            {{ #each fields }}
            {{ #unless equals name 'id' }}
            {{ #unless equals name 'createdAt' }}
            {{ #unless equals name 'updatedAt' }}
            {{ #unless equals name 'deletedAt' }}
            {{ #unless equals name 'status' }}
            .{{ name }}(request.get{{ pascal_case name }}())
            {{ /unless }}
            {{ /unless }}
            {{ /unless }}
            {{ /unless }}
            {{ /unless }}
            {{ /each }}
            .status({{ EntityName }}Status.ACTIVE)
            .build();

        entity = repository.save(entity);
        log.info("Created {{ EntityName }} with id: {}", entity.getId());
        return {{ EntityName }}Response.from(entity);
    }

    @Override
    @Transactional
    public {{ EntityName }}Response update(Long id, {{ EntityName }}UpdateRequest request) {
        {{ EntityName }} entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("{{ EntityName }}", id));

        {{ #each fields }}
        {{ #unless equals name 'id' }}
        {{ #unless equals name 'createdAt' }}
        {{ #unless equals name 'updatedAt' }}
        {{ #unless equals name 'deletedAt' }}
        {{ #unless equals name 'status' }}
        if (request.get{{ pascal_case name }}() != null) {
            entity.set{{ pascal_case name }}(request.get{{ pascal_case name }}());
        }
        {{ /unless }}
        {{ /unless }}
        {{ /unless }}
        {{ /unless }}
        {{ /unless }}
        {{ /each }}

        entity = repository.save(entity);
        log.info("Updated {{ EntityName }} with id: {}", entity.getId());
        return {{ EntityName }}Response.from(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("{{ EntityName }}", id);
        }
        repository.softDelete(id);
        log.info("Deleted {{ EntityName }} with id: {}", id);
    }

    @Override
    public boolean existsById(Long id) {
        return repository.existsById(id);
    }

    {{ #each fields }}
    {{ #if unique }}
    @Override
    public boolean existsBy{{ pascal_case name }}({{ type }} {{ name }}) {
        return repository.existsBy{{ pascal_case name }}({{ name }});
    }
    {{ /if }}
    {{ /each }}

    private Specification<{{ EntityName }}> buildSpecification({{ EntityName }}SearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            {{ #each fields }}
            {{ #if equals type 'String' }}
            if (StringUtils.hasText(request.get{{ pascal_case name }}())) {
                predicates.add(cb.like(
                    cb.lower(root.get("{{ name }}")),
                    "%" + request.get{{ pascal_case name }}().toLowerCase() + "%"
                ));
            }
            {{ /if }}
            {{ /each }}
            if (request.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), request.getStatus()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

### Controller

```java
@RestController
@RequestMapping("/api/v1/{{ kebab_case entity_name }}s")
@RequiredArgsConstructor
@Tag(name = "{{ EntityName }} Management", description = "{{ EntityName }} CRUD API")
@Validated
@Slf4j
public class {{ EntityName }}Controller {

    private final {{ EntityName }}Service service;

    @GetMapping("/{id}")
    @Operation(summary = "Get {{ EntityName }} by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "404", description = "{{ EntityName }} not found")
    })
    public ApiResponse<{{ EntityName }}Response> getById(
        @PathVariable @Min(1) Long id
    ) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    @Operation(summary = "Search {{ EntityName }} list")
    public ApiResponse<PageResponse<{{ EntityName }}Response>> search(
        {{ EntityName }}SearchRequest request
    ) {
        return ApiResponse.success(service.search(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create {{ EntityName }}")
    public ApiResponse<{{ EntityName }}Response> create(
        @Valid @RequestBody {{ EntityName }}CreateRequest request
    ) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update {{ EntityName }}")
    public ApiResponse<{{ EntityName }}Response> update(
        @PathVariable Long id,
        @Valid @RequestBody {{ EntityName }}UpdateRequest request
    ) {
        return ApiResponse.success(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete {{ EntityName }}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
```

---

## Test Generation Templates

### Service Unit Tests

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("{{ EntityName }}Service Tests")
class {{ EntityName }}ServiceTest {

    @Mock
    private {{ EntityName }}Repository repository;

    @InjectMocks
    private {{ EntityName }}ServiceImpl service;

    @Nested
    @DisplayName("GetById Method")
    class GetByIdTests {

        @Test
        @DisplayName("should return {{ entity_name }} when exists")
        void existing{{ EntityName }}_returns{{ EntityName }}() {
            // Arrange
            Long id = 1L;
            {{ EntityName }} entity = {{ EntityName }}.builder()
                .id(id)
                {{ #each fields }}
                {{ #unless equals name 'id' }}
                .{{ name }}(test{{ pascal_case name }})
                {{ /unless }}
                {{ /each }}
                .build();

            when(repository.findById(id)).thenReturn(Optional.of(entity));

            // Act
            {{ EntityName }}Response result = service.getById(id);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(id);
        }

        @Test
        @DisplayName("should throw exception when not found")
        void nonExisting{{ EntityName }}_throwsException() {
            // Arrange
            Long id = 999L;
            when(repository.findById(id)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Create Method")
    class CreateTests {

        @Test
        @DisplayName("should create with valid data")
        void validInput_creates{{ EntityName }}() {
            // Arrange
            {{ EntityName }}CreateRequest request = {{ EntityName }}CreateRequest.builder()
                {{ #each fields }}
                {{ #unless equals name 'id' }}
                {{ #unless equals name 'status' }}
                .{{ name }}(test{{ pascal_case name }})
                {{ /unless }}
                {{ /unless }}
                {{ /each }}
                .build();

            {{ #each fields }}
            {{ #if unique }}
            when(repository.existsBy{{ pascal_case name }}(request.get{{ pascal_case name }}())).thenReturn(false);
            {{ /if }}
            {{ /each }}

            {{ EntityName }} savedEntity = {{ EntityName }}.builder()
                .id(1L)
                {{ #each fields }}
                {{ #unless equals name 'id' }}
                .{{ name }}(request.get{{ pascal_case name }}())
                {{ /unless }}
                {{ /each }}
                .build();

            when(repository.save(any())).thenReturn(savedEntity);

            // Act
            {{ EntityName }}Response result = service.create(request);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }
    }
}
```

---

## Generated File List

| File Type | File Path | Description |
|-----------|-----------|-------------|
| Entity | `entity/{{ EntityName }}.java` | Entity class |
| Create Request | `dto/request/{{ EntityName }}CreateRequest.java` | Create request DTO |
| Update Request | `dto/request/{{ EntityName }}UpdateRequest.java` | Update request DTO |
| Search Request | `dto/request/{{ EntityName }}SearchRequest.java` | Search request DTO |
| Response | `dto/response/{{ EntityName }}Response.java` | Response DTO |
| Repository | `repository/{{ EntityName }}Repository.java` | Data access layer |
| Service | `service/{{ EntityName }}Service.java` | Service interface |
| Service Impl | `service/impl/{{ EntityName }}ServiceImpl.java` | Service implementation |
| Controller | `controller/{{ EntityName }}Controller.java` | Controller |
| Service Test | `test/service/{{ EntityName }}ServiceTest.java` | Unit tests |
| Controller Test | `test/controller/{{ EntityName }}ControllerIntegrationTest.java` | Integration tests |

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/{{ entity_name }}s/{id}` | Get by ID |
| GET | `/api/v1/{{ entity_name }}s` | Search list (paginated) |
| POST | `/api/v1/{{ entity_name }}s` | Create |
| PUT | `/api/v1/{{ entity_name }}s/{id}` | Update |
| DELETE | `/api/v1/{{ entity_name }}s/{id}` | Delete |
