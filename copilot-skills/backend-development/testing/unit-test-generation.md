# 单元测试生成 Skill

> 自动生成高质量、全面的单元测试，遵循 AAA 模式和最佳实践

## 触发条件

- 命令: `/gen-unit-tests`
- 文件变更: `src/main/java/**/*Service*.java`

---

## 测试原则

### FIRST 原则

- **F**ast: 测试要快
- **I**ndependent: 测试之间独立
- **R**epeatable: 测试可重复执行
- **S**elf-validating: 自动验证结果
- **T**imely: 及时编写

### AAA 模式

```java
@Test
void methodName_scenario_expectedResult() {
    // Arrange (Given) - 准备测试数据
    Long userId = 1L;
    User expectedUser = User.builder()
        .id(userId)
        .name("John")
        .build();
    when(userRepository.findById(userId)).thenReturn(Optional.of(expectedUser));

    // Act (When) - 执行被测试方法
    UserResponse result = userService.getById(userId);

    // Assert (Then) - 验证结果
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(userId);
    assertThat(result.getName()).isEqualTo("John");

    verify(userRepository).findById(userId);
    verifyNoMoreInteractions(userRepository);
}
```

---

## 测试模板

### Service 层测试模板

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserServiceImpl userService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    // ==================== GetById Tests ====================

    @Nested
    @DisplayName("GetById Method")
    class GetByIdTests {

        @Test
        @DisplayName("should return user when user exists")
        void existingUser_returnsUser() {
            // Arrange
            Long userId = 1L;
            User user = User.builder()
                .id(userId)
                .name("John Doe")
                .email("john@example.com")
                .status(UserStatus.ACTIVE)
                .build();

            UserResponse expected = UserResponse.builder()
                .id(userId)
                .name("John Doe")
                .email("john@example.com")
                .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userMapper.toResponse(user)).thenReturn(expected);

            // Act
            UserResponse result = userService.getById(userId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(userId);
            assertThat(result.getName()).isEqualTo("John Doe");

            verify(userRepository).findById(userId);
            verify(userMapper).toResponse(user);
        }

        @Test
        @DisplayName("should throw exception when user not found")
        void nonExistingUser_throwsException() {
            // Arrange
            Long userId = 999L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userService.getById(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User")
                .hasMessageContaining(String.valueOf(userId));

            verify(userRepository).findById(userId);
            verifyNoInteractions(userMapper);
        }

        @ParameterizedTest
        @ValueSource(longs = {-1, 0, -999})
        @DisplayName("should throw exception for invalid id")
        void invalidId_throwsException(Long invalidId) {
            // Act & Assert
            assertThatThrownBy(() -> userService.getById(invalidId))
                .isInstanceOf(InvalidIdException.class);
        }
    }

    // ==================== Create Tests ====================

    @Nested
    @DisplayName("Create Method")
    class CreateTests {

        @Test
        @DisplayName("should create user with valid data")
        void validInput_createsUser() {
            // Arrange
            UserCreateRequest request = UserCreateRequest.builder()
                .name("John Doe")
                .email("john@example.com")
                .build();

            User userToSave = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .status(UserStatus.ACTIVE)
                .build();

            User savedUser = User.builder()
                .id(1L)
                .name(request.getName())
                .email(request.getEmail())
                .status(UserStatus.ACTIVE)
                .build();

            UserResponse expected = UserResponse.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .build();

            when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(userMapper.toResponse(savedUser)).thenReturn(expected);

            // Act
            UserResponse result = userService.create(request);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);

            verify(userRepository).existsByEmail(request.getEmail());
            verify(userRepository).save(userCaptor.capture());
            verify(eventPublisher).publishEvent(any(UserCreatedEvent.class));

            User capturedUser = userCaptor.getValue();
            assertThat(capturedUser.getName()).isEqualTo(request.getName());
            assertThat(capturedUser.getEmail()).isEqualTo(request.getEmail());
            assertThat(capturedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("should throw exception when email already exists")
        void duplicateEmail_throwsException() {
            // Arrange
            UserCreateRequest request = UserCreateRequest.builder()
                .name("John Doe")
                .email("existing@example.com")
                .build();

            when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("existing@example.com");

            verify(userRepository).existsByEmail(request.getEmail());
            verify(userRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   "})
        @DisplayName("should throw exception when name is blank")
        void blankName_throwsException(String blankName) {
            // Arrange
            UserCreateRequest request = UserCreateRequest.builder()
                .name(blankName)
                .email("john@example.com")
                .build();

            // Act & Assert
            assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ValidationException.class);
        }

        @ParameterizedTest
        @CsvSource({
            "John Doe, john@example.com",
            "Jane Smith, jane@example.com",
            "Bob Wilson, bob@example.com"
        })
        @DisplayName("should create users with different valid data")
        void validCombinations_createsSuccessfully(String name, String email) {
            // Arrange
            UserCreateRequest request = UserCreateRequest.builder()
                .name(name)
                .email(email)
                .build();

            User savedUser = User.builder()
                .id(1L)
                .name(name)
                .email(email)
                .build();

            when(userRepository.existsByEmail(email)).thenReturn(false);
            when(userRepository.save(any())).thenReturn(savedUser);
            when(userMapper.toResponse(any())).thenReturn(UserResponse.builder()
                .id(1L).name(name).email(email).build());

            // Act & Assert
            assertThatCode(() -> userService.create(request))
                .doesNotThrowAnyException();
        }
    }

    // ==================== Update Tests ====================

    @Nested
    @DisplayName("Update Method")
    class UpdateTests {

        @Test
        @DisplayName("should update user with valid data")
        void validInput_updatesUser() {
            // Arrange
            Long userId = 1L;
            UserUpdateRequest request = UserUpdateRequest.builder()
                .name("Jane Doe")
                .build();

            User existingUser = User.builder()
                .id(userId)
                .name("John Doe")
                .email("john@example.com")
                .build();

            User updatedUser = User.builder()
                .id(userId)
                .name("Jane Doe")
                .email("john@example.com")
                .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
            when(userRepository.save(existingUser)).thenReturn(updatedUser);
            when(userMapper.toResponse(updatedUser)).thenReturn(
                UserResponse.builder().id(userId).name("Jane Doe").build()
            );

            // Act
            UserResponse result = userService.update(userId, request);

            // Assert
            assertThat(result.getName()).isEqualTo("Jane Doe");
            verify(userRepository).save(existingUser);
        }

        @Test
        @DisplayName("should throw exception when user not found")
        void nonExistingUser_throwsException() {
            // Arrange
            Long userId = 999L;
            UserUpdateRequest request = UserUpdateRequest.builder()
                .name("Jane Doe")
                .build();

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userService.update(userId, request))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== Delete Tests ====================

    @Nested
    @DisplayName("Delete Method")
    class DeleteTests {

        @Test
        @DisplayName("should soft delete existing user")
        void existingUser_softDeletes() {
            // Arrange
            Long userId = 1L;
            when(userRepository.existsById(userId)).thenReturn(true);
            doNothing().when(userRepository).softDelete(userId);

            // Act
            userService.delete(userId);

            // Assert
            verify(userRepository).existsById(userId);
            verify(userRepository).softDelete(userId);
        }

        @Test
        @DisplayName("should throw exception when deleting non-existing user")
        void nonExistingUser_throwsException() {
            // Arrange
            Long userId = 999L;
            when(userRepository.existsById(userId)).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> userService.delete(userId))
                .isInstanceOf(ResourceNotFoundException.class);

            verify(userRepository, never()).softDelete(any());
        }
    }

    // ==================== Search Tests ====================

    @Nested
    @DisplayName("Search Method")
    class SearchTests {

        @Test
        @DisplayName("should return paginated results")
        void validRequest_returnsPaginatedResults() {
            // Arrange
            UserSearchRequest request = UserSearchRequest.builder()
                .page(1)
                .size(10)
                .build();

            List<User> users = List.of(
                User.builder().id(1L).name("User 1").build(),
                User.builder().id(2L).name("User 2").build()
            );

            Page<User> page = new PageImpl<>(users);

            when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

            // Act
            PageResponse<UserResponse> result = userService.search(request);

            // Assert
            assertThat(result.getItems()).hasSize(2);
            assertThat(result.getPagination().getTotal()).isEqualTo(2);
        }

        @Test
        @DisplayName("should filter by status")
        void filterByStatus_returnsFilteredResults() {
            // Arrange
            UserSearchRequest request = UserSearchRequest.builder()
                .status(UserStatus.ACTIVE)
                .page(1)
                .size(10)
                .build();

            // ... setup mocks

            // Act & Assert
            // Verify Specification contains status filter
        }
    }
}
```

---

## Repository 层测试模板

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DisplayName("UserRepository Tests")
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("FindByEmail")
    class FindByEmailTests {

        @Test
        @DisplayName("should find user by email")
        void existingEmail_returnsUser() {
            // Arrange
            User user = User.builder()
                .name("John Doe")
                .email("john@example.com")
                .status(UserStatus.ACTIVE)
                .build();
            entityManager.persistAndFlush(user);

            // Act
            Optional<User> found = userRepository.findByEmail("john@example.com");

            // Assert
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("John Doe");
        }

        @Test
        @DisplayName("should return empty when email not found")
        void nonExistingEmail_returnsEmpty() {
            // Act
            Optional<User> found = userRepository.findByEmail("notfound@example.com");

            // Assert
            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("ExistsByEmail")
    class ExistsByEmailTests {

        @Test
        @DisplayName("should return true when email exists")
        void existingEmail_returnsTrue() {
            // Arrange
            User user = User.builder()
                .name("John Doe")
                .email("john@example.com")
                .status(UserStatus.ACTIVE)
                .build();
            entityManager.persistAndFlush(user);

            // Act & Assert
            assertThat(userRepository.existsByEmail("john@example.com")).isTrue();
        }

        @Test
        @DisplayName("should return false when email does not exist")
        void nonExistingEmail_returnsFalse() {
            // Act & Assert
            assertThat(userRepository.existsByEmail("notfound@example.com")).isFalse();
        }
    }

    @Nested
    @DisplayName("SoftDelete")
    class SoftDeleteTests {

        @Test
        @DisplayName("should soft delete user")
        void existingUser_softDeletes() {
            // Arrange
            User user = User.builder()
                .name("John Doe")
                .email("john@example.com")
                .status(UserStatus.ACTIVE)
                .build();
            user = entityManager.persistAndFlush(user);

            // Act
            userRepository.softDelete(user.getId());
            entityManager.clear();

            // Assert - 不应该被查询到（因为有 @Where(clause = "deleted_at IS NULL")）
            Optional<User> found = userRepository.findById(user.getId());
            assertThat(found).isEmpty();
        }
    }
}
```

---

## 测试命名规范

```java
// 格式: methodName_scenario_expectedResult

// 查询测试
getById_existingUser_returnsUserResponse()
getById_nonExistingUser_throwsResourceNotFoundException()
getById_invalidId_throwsInvalidIdException()

// 创建测试
create_validRequest_savesUserAndReturnsResponse()
create_duplicateEmail_throwsDuplicateEmailException()
create_blankName_throwsValidationException()

// 更新测试
update_validRequest_updatesAndReturnsResponse()
update_nonExistingUser_throwsResourceNotFoundException()
update_emptyRequest_noChangesMade()

// 删除测试
delete_existingUser_softDeletesUser()
delete_nonExistingUser_throwsResourceNotFoundException()

// 搜索测试
search_validRequest_returnsPaginatedResults()
search_withFilters_returnsFilteredResults()
search_emptyResult_returnsEmptyPage()
```

---

## 参数化测试

### @ValueSource

```java
@ParameterizedTest
@ValueSource(strings = {"", " ", "   "})
@DisplayName("should reject blank names")
void blankNames_throwsException(String blankName) {
    // ...
}
```

### @CsvSource

```java
@ParameterizedTest
@CsvSource({
    "John, john@example.com, true",
    "Jane, jane@example.com, true",
    "'', test@example.com, false"
})
@DisplayName("should validate user creation")
void userValidation(String name, String email, boolean expectedValid) {
    // ...
}
```

### @EnumSource

```java
@ParameterizedTest
@EnumSource(value = UserStatus.class, names = {"ACTIVE", "INACTIVE"})
@DisplayName("should handle different statuses")
void differentStatuses(UserStatus status) {
    // ...
}
```

### @MethodSource

```java
static Stream<Arguments> provideInvalidEmails() {
    return Stream.of(
        Arguments.of("", "empty"),
        Arguments.of("invalid", "no at sign"),
        Arguments.of("invalid@", "no domain"),
        Arguments.of("@example.com", "no local part")
    );
}

@ParameterizedTest
@MethodSource("provideInvalidEmails")
@DisplayName("should reject invalid email formats")
void invalidEmails_throwsException(String email, String description) {
    // ...
}
```

---

## 测试工具类

```java
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public Clock testClock() {
        return Clock.fixed(Instant.parse("2026-02-24T10:00:00Z"), ZoneId.of("UTC"));
    }
}

public class TestFixture {

    public static User.UserBuilder defaultUser() {
        return User.builder()
            .name("Test User")
            .email("test@example.com")
            .status(UserStatus.ACTIVE);
    }

    public static UserCreateRequest.UserCreateRequestBuilder defaultCreateRequest() {
        return UserCreateRequest.builder()
            .name("Test User")
            .email("test@example.com");
    }

    public static User aUser() {
        return defaultUser().build();
    }

    public static User aUserWithEmail(String email) {
        return defaultUser().email(email).build();
    }
}
```

---

## 检查清单

### 测试覆盖

- [ ] 每个公共方法都有测试
- [ ] 正常场景覆盖
- [ ] 边界值测试
- [ ] 异常情况测试
- [ ] 空值/无效输入测试

### 测试质量

- [ ] 测试独立，不依赖执行顺序
- [ ] 测试可重复执行
- [ ] 测试名称清晰描述意图
- [ ] 使用断言而非打印
- [ ] Mock 使用正确

### 代码规范

- [ ] 使用 AAA 模式
- [ ] 适当的注释
- [ ] 测试数据使用 Builder 或工厂方法
- [ ] 避免魔法数字
