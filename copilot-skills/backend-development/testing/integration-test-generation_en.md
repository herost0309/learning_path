# Integration Test Generation Skill

> Generate integration tests that test complete request-response flows and component integration

## Trigger Conditions

- Command: `/gen-integration-tests`
- File Changes: `src/main/java/**/*Controller*.java`

---

## Test Configuration

### Testcontainers Configuration

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
@DisplayName("User Controller Integration Tests")
class UserControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String authToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        // Create test user and get auth token
        User testUser = User.builder()
            .name("Test User")
            .email("test@example.com")
            .password(passwordEncoder.encode("password123"))
            .status(UserStatus.ACTIVE)
            .build();
        userRepository.save(testUser);

        authToken = "Bearer " + jwtTokenProvider.generateToken(testUser);
    }
}
```

---

## CRUD Integration Test Templates

### GET /{id} Tests

```java
@Nested
@DisplayName("GET /api/v1/users/{id}")
class GetUserByIdTests {

    @Test
    @DisplayName("should return 200 and user when user exists")
    void existingUser_returns200AndUser() throws Exception {
        // Arrange
        User user = User.builder()
            .name("John Doe")
            .email("john@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        user = userRepository.save(user);

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{id}", user.getId())
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.id").value(user.getId()))
            .andExpect(jsonPath("$.data.name").value("John Doe"))
            .andExpect(jsonPath("$.data.email").value("john@example.com"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("should return 404 when user not found")
    void nonExistingUser_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", 99999L)
                .header("Authorization", authToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("should return 401 when not authenticated")
    void noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/1"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should return 400 for invalid id format")
    void invalidIdFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/users/invalid")
                .header("Authorization", authToken))
            .andExpect(status().isBadRequest());
    }
}
```

### GET / List Tests

```java
@Nested
@DisplayName("GET /api/v1/users")
class GetUsersTests {

    @Test
    @DisplayName("should return paginated users")
    void multipleUsers_returnsPaginatedResult() throws Exception {
        // Arrange
        for (int i = 1; i <= 25; i++) {
            User user = User.builder()
                .name("User " + i)
                .email("user" + i + "@example.com")
                .status(UserStatus.ACTIVE)
                .build();
            userRepository.save(user);
        }

        // Act & Assert
        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", authToken)
                .param("page", "1")
                .param("size", "10")
                .param("sortBy", "name")
                .param("sortDirection", "asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.items.length()").value(10))
            .andExpect(jsonPath("$.data.pagination.page").value(1))
            .andExpect(jsonPath("$.data.pagination.size").value(10))
            .andExpect(jsonPath("$.data.pagination.total").value(25))
            .andExpect(jsonPath("$.data.pagination.totalPages").value(3));
    }

    @Test
    @DisplayName("should filter by status")
    void filterByStatus_returnsFilteredUsers() throws Exception {
        // Arrange
        User activeUser = User.builder()
            .name("Active User")
            .email("active@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        userRepository.save(activeUser);

        User inactiveUser = User.builder()
            .name("Inactive User")
            .email("inactive@example.com")
            .status(UserStatus.INACTIVE)
            .build();
        userRepository.save(inactiveUser);

        // Act & Assert
        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", authToken)
                .param("status", "ACTIVE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("should search by name")
    void searchByName_returnsMatchingUsers() throws Exception {
        // Arrange
        User user1 = User.builder()
            .name("John Doe")
            .email("john@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        userRepository.save(user1);

        User user2 = User.builder()
            .name("Jane Smith")
            .email("jane@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        userRepository.save(user2);

        // Act & Assert
        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", authToken)
                .param("search", "John"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].name").value("John Doe"));
    }

    @Test
    @DisplayName("should return empty page when no results")
    void noResults_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", authToken)
                .param("search", "NonExistent"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.pagination.total").value(0));
    }
}
```

### POST / Create Tests

```java
@Nested
@DisplayName("POST /api/v1/users")
class CreateUserTests {

    @Test
    @DisplayName("should return 201 and created user with valid data")
    void validData_returns201AndUser() throws Exception {
        // Arrange
        UserCreateRequest request = UserCreateRequest.builder()
            .name("New User")
            .email("newuser@example.com")
            .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.id").exists())
            .andExpect(jsonPath("$.data.name").value("New User"))
            .andExpect(jsonPath("$.data.email").value("newuser@example.com"));

        // Verify in database
        Optional<User> saved = userRepository.findByEmail("newuser@example.com");
        assertThat(saved).isPresent();
        assertThat(saved.get().getName()).isEqualTo("New User");
    }

    @Test
    @DisplayName("should return 409 when email already exists")
    void duplicateEmail_returns409() throws Exception {
        // Arrange
        User existingUser = User.builder()
            .name("Existing User")
            .email("existing@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        userRepository.save(existingUser);

        UserCreateRequest request = UserCreateRequest.builder()
            .name("New User")
            .email("existing@example.com")
            .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("should return 400 when name is blank")
    void blankName_returns400() throws Exception {
        // Arrange
        UserCreateRequest request = UserCreateRequest.builder()
            .name("")
            .email("test@example.com")
            .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists());
    }

    @Test
    @DisplayName("should return 400 when email is invalid")
    void invalidEmail_returns400() throws Exception {
        // Arrange
        UserCreateRequest request = UserCreateRequest.builder()
            .name("Test User")
            .email("invalid-email")
            .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @CsvSource({
        "'', test@example.com, name",
        "Test User, '', email",
        "'', '', name"
    })
    @DisplayName("should return 400 for various invalid inputs")
    void invalidInputs_returns400(String name, String email, String errorField) throws Exception {
        // Arrange
        UserCreateRequest request = UserCreateRequest.builder()
            .name(name)
            .email(email)
            .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
```

### PUT /{id} Update Tests

```java
@Nested
@DisplayName("PUT /api/v1/users/{id}")
class UpdateUserTests {

    @Test
    @DisplayName("should return 200 and updated user")
    void validData_returns200AndUser() throws Exception {
        // Arrange
        User existingUser = User.builder()
            .name("Old Name")
            .email("old@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        existingUser = userRepository.save(existingUser);

        UserUpdateRequest request = UserUpdateRequest.builder()
            .name("New Name")
            .build();

        // Act & Assert
        mockMvc.perform(put("/api/v1/users/{id}", existingUser.getId())
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("New Name"))
            .andExpect(jsonPath("$.data.email").value("old@example.com"));

        // Verify in database
        User updated = userRepository.findById(existingUser.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("should return 404 when user not found")
    void nonExistingUser_returns404() throws Exception {
        // Arrange
        UserUpdateRequest request = UserUpdateRequest.builder()
            .name("New Name")
            .build();

        // Act & Assert
        mockMvc.perform(put("/api/v1/users/{id}", 99999L)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should return 409 when updating to existing email")
    void updateToExistingEmail_returns409() throws Exception {
        // Arrange
        User user1 = User.builder()
            .name("User 1")
            .email("user1@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        user1 = userRepository.save(user1);

        User user2 = User.builder()
            .name("User 2")
            .email("user2@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        userRepository.save(user2);

        UserUpdateRequest request = UserUpdateRequest.builder()
            .email("user2@example.com")
            .build();

        // Act & Assert
        mockMvc.perform(put("/api/v1/users/{id}", user1.getId())
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict());
    }
}
```

### DELETE /{id} Tests

```java
@Nested
@DisplayName("DELETE /api/v1/users/{id}")
class DeleteUserTests {

    @Test
    @DisplayName("should return 204 when user deleted")
    void existingUser_returns204() throws Exception {
        // Arrange
        User user = User.builder()
            .name("To Delete")
            .email("delete@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        user = userRepository.save(user);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/users/{id}", user.getId())
                .header("Authorization", authToken))
            .andExpect(status().isNoContent());

        // Verify soft delete
        Optional<User> deleted = userRepository.findById(user.getId());
        assertThat(deleted).isEmpty(); // Because of @Where(clause = "deleted_at IS NULL")
    }

    @Test
    @DisplayName("should return 404 when user not found")
    void nonExistingUser_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{id}", 99999L)
                .header("Authorization", authToken))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should not be able to delete twice")
    void deleteTwice_returns404() throws Exception {
        // Arrange
        User user = User.builder()
            .name("To Delete")
            .email("delete@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        user = userRepository.save(user);

        // First delete
        mockMvc.perform(delete("/api/v1/users/{id}", user.getId())
                .header("Authorization", authToken))
            .andExpect(status().isNoContent());

        // Second delete
        mockMvc.perform(delete("/api/v1/users/{id}", user.getId())
                .header("Authorization", authToken))
            .andExpect(status().isNotFound());
    }
}
```

---

## Concurrency Tests

```java
@Nested
@DisplayName("Concurrency Tests")
class ConcurrencyTests {

    @Test
    @DisplayName("should handle concurrent updates with optimistic locking")
    void concurrentUpdates_handlesOptimisticLocking() throws Exception {
        // Arrange
        User user = User.builder()
            .name("Original Name")
            .email("concurrent@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        user = userRepository.save(user);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();

                    UserUpdateRequest request = UserUpdateRequest.builder()
                        .name("Updated Name " + index)
                        .build();

                    String result = mockMvc.perform(put("/api/v1/users/{id}", user.getId())
                            .header("Authorization", authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                        .andReturn().getResponse().getContentAsString();

                    if (result.contains("SUCCESS")) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // May fail due to optimistic locking
                    conflictCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // At least one should succeed
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
    }
}
```

---

## Test Utilities

```java
public class IntegrationTestHelper {

    public static String toJson(Object obj) throws JsonProcessingException {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .writeValueAsString(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) throws JsonProcessingException {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .readValue(json, clazz);
    }

    public static ResultActions performGet(MockMvc mockMvc, String url, String authToken) throws Exception {
        return mockMvc.perform(get(url)
            .header("Authorization", authToken)
            .contentType(MediaType.APPLICATION_JSON));
    }

    public static ResultActions performPost(MockMvc mockMvc, String url, Object body, String authToken) throws Exception {
        return mockMvc.perform(post(url)
            .header("Authorization", authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(toJson(body)));
    }
}
```

---

## Checklist

### Test Coverage

- [ ] Every API endpoint has tests
- [ ] Success scenario tests
- [ ] Error scenario tests
- [ ] Boundary value tests
- [ ] Concurrency scenario tests

### Test Quality

- [ ] Use real database (Testcontainers)
- [ ] Test data isolation
- [ ] Correct status code validation
- [ ] Response structure validation
- [ ] Database state verification

### Configuration

- [ ] Testcontainers configured correctly
- [ ] Auth token set up correctly
- [ ] Test data cleanup
- [ ] Random port assignment
