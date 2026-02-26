# Go 后端代码风格 Skill

> 确保 Go 后端代码遵循官方规范和社区最佳实践

## 触发条件

- 命令: `/go-style`
- 文件变更: `**/*.go`

---

## 命名规范

### 包命名

```go
// 使用简短、小写、单数名称
package user
package httputil
package json

// 避免使用下划线或驼峰
package user_service  // 错误
package userService   // 错误
```

### 变量和函数

```go
// 驼峰命名
userName := "John"
maxRetryCount := 3

// 首字母大写表示导出
func CreateUser(name string) *User { }
var DefaultTimeout = 30 * time.Second

// 首字母小写表示私有
func validateEmail(email string) error { }
var defaultPoolSize = 10

// 缩写保持一致大小写
var httpClient HTTPClient
var userId string  // 错误
var userID string  // 正确
```

### 接口命名

```go
// 单方法接口使用 -er 后缀
type Reader interface {
    Read(p []byte) (n int, err error)
}

type Writer interface {
    Write(p []byte) (n int, err error)
}

type UserRepository interface {
    GetByID(ctx context.Context, id int64) (*User, error)
    Create(ctx context.Context, user *User) error
    Update(ctx context.Context, user *User) error
    Delete(ctx context.Context, id int64) error
    List(ctx context.Context, filter UserFilter) ([]*User, int64, error)
}
```

---

## 错误处理

### 基本错误处理

```go
// 始终检查错误
user, err := userService.GetByID(ctx, userID)
if err != nil {
    return nil, fmt.Errorf("failed to get user: %w", err)
}

// 使用 fmt.Errorf 包装错误
if err := db.Connect(); err != nil {
    return fmt.Errorf("database connection failed: %w", err)
}

// 自定义错误类型
type NotFoundError struct {
    Resource string
    ID       int64
}

func (e *NotFoundError) Error() string {
    return fmt.Sprintf("%s with id %d not found", e.Resource, e.ID)
}

func (e *NotFoundError) Is(target error) bool {
    t, ok := target.(*NotFoundError)
    if !ok {
        return false
    }
    return e.Resource == t.Resource
}

// 错误判断
var notFound *NotFoundError
if errors.As(err, &notFound) {
    // 处理特定错误
    return echo.NewHTTPError(http.StatusNotFound, notFound.Error())
}
```

### 错误处理最佳实践

```go
// 定义 Sentinel 错误
var (
    ErrNotFound      = errors.New("resource not found")
    ErrUnauthorized  = errors.New("unauthorized")
    ErrInvalidInput  = errors.New("invalid input")
)

// 使用 errors.Is 判断
if errors.Is(err, ErrNotFound) {
    return echo.NewHTTPError(http.StatusNotFound)
}

// 错误包装
func (s *Service) GetUser(ctx context.Context, id int64) (*User, error) {
    user, err := s.repo.GetByID(ctx, id)
    if err != nil {
        return nil, fmt.Errorf("failed to get user %d: %w", id, err)
    }
    return user, nil
}
```

---

## 注释规范

### 包注释

```go
// Package user provides user management functionality.
// It includes CRUD operations, authentication, and authorization.
//
// Example usage:
//
//	user, err := user.NewService(repo).GetByID(ctx, 1)
package user
```

### 函数注释

```go
// GetByID retrieves a user by their unique identifier.
// It returns ErrNotFound if no user exists with the given ID.
//
// The context parameter allows for request cancellation and timeout.
func (s *Service) GetByID(ctx context.Context, id int64) (*User, error) {
    // ...
}
```

### 类型注释

```go
// User represents a system user with associated metadata.
// User instances are immutable after creation.
type User struct {
    // ID is the unique identifier for the user.
    ID int64

    // Name is the display name of the user.
    // It must be between 2 and 100 characters.
    Name string

    // Email is the user's email address.
    // It must be a valid email format.
    Email string
}
```

---

## 结构体设计

```go
// 使用结构体标签
type User struct {
    ID        int64     `json:"id" db:"id"`
    Name      string    `json:"name" db:"name" validate:"required,min=2,max=100"`
    Email     string    `json:"email" db:"email" validate:"required,email"`
    Status    Status    `json:"status" db:"status"`
    CreatedAt time.Time `json:"created_at" db:"created_at"`
    UpdatedAt time.Time `json:"updated_at" db:"updated_at"`
}

// 使用构造函数
func NewUser(name, email string) *User {
    return &User{
        Name:      name,
        Email:     email,
        Status:    StatusActive,
        CreatedAt: time.Now(),
        UpdatedAt: time.Now(),
    }
}

// 使用函数选项模式
type Option func(*User)

func WithStatus(status Status) Option {
    return func(u *User) {
        u.Status = status
    }
}

func NewUser(name, email string, opts ...Option) *User {
    user := &User{
        Name:   name,
        Email:  email,
        Status: StatusActive,
    }
    for _, opt := range opts {
        opt(user)
    }
    return user
}

// 使用方式
user := NewUser("John", "john@example.com", WithStatus(StatusActive))
```

---

## HTTP Handler 最佳实践

### Echo 框架

```go
type Handler struct {
    service UserService
    logger  *zap.Logger
}

func NewHandler(service UserService, logger *zap.Logger) *Handler {
    return &Handler{
        service: service,
        logger:  logger,
    }
}

func (h *Handler) RegisterRoutes(e *echo.Echo) {
    users := e.Group("/api/v1/users")
    users.GET("", h.ListUsers)
    users.GET("/:id", h.GetUser)
    users.POST("", h.CreateUser)
    users.PUT("/:id", h.UpdateUser)
    users.DELETE("/:id", h.DeleteUser)
}

// ListUsers godoc
// @Summary List users
// @Description Get a list of users with pagination
// @Tags users
// @Accept json
// @Produce json
// @Param page query int false "Page number" default(1)
// @Param size query int false "Page size" default(20)
// @Success 200 {object} PageResponse[UserResponse]
// @Failure 500 {object} ErrorResponse
// @Router /users [get]
func (h *Handler) ListUsers(c echo.Context) error {
    ctx := c.Request().Context()

    var params ListUsersParams
    if err := c.Bind(&params); err != nil {
        return echo.NewHTTPError(http.StatusBadRequest, "invalid parameters")
    }

    users, total, err := h.service.List(ctx, params)
    if err != nil {
        h.logger.Error("failed to list users", zap.Error(err))
        return echo.NewHTTPError(http.StatusInternalServerError)
    }

    return c.JSON(http.StatusOK, PageResponse[UserResponse]{
        Items: users,
        Total: total,
        Page:  params.Page,
        Size:  params.Size,
    })
}

// CreateUser godoc
// @Summary Create a new user
// @Description Create a new user with the provided information
// @Tags users
// @Accept json
// @Produce json
// @Param request body CreateUserRequest true "User creation request"
// @Success 201 {object} UserResponse
// @Failure 400 {object} ErrorResponse
// @Failure 409 {object} ErrorResponse
// @Router /users [post]
func (h *Handler) CreateUser(c echo.Context) error {
    ctx := c.Request().Context()

    var req CreateUserRequest
    if err := c.Bind(&req); err != nil {
        return echo.NewHTTPError(http.StatusBadRequest, "invalid request body")
    }

    if err := c.Validate(&req); err != nil {
        return echo.NewHTTPError(http.StatusBadRequest, err.Error())
    }

    user, err := h.service.Create(ctx, &req)
    if err != nil {
        if errors.Is(err, ErrDuplicateEmail) {
            return echo.NewHTTPError(http.StatusConflict, "email already exists")
        }
        h.logger.Error("failed to create user", zap.Error(err))
        return echo.NewHTTPError(http.StatusInternalServerError)
    }

    return c.JSON(http.StatusCreated, user)
}
```

---

## 并发模式

```go
// 使用 context 进行取消
func (s *Service) ProcessBatch(ctx context.Context, ids []int64) error {
    for _, id := range ids {
        select {
        case <-ctx.Done():
            return ctx.Err()
        default:
            if err := s.processOne(ctx, id); err != nil {
                return err
            }
        }
    }
    return nil
}

// 使用 errgroup 进行并发错误处理
func (s *Service) FetchAll(ctx context.Context, ids []int64) ([]*User, error) {
    g, ctx := errgroup.WithContext(ctx)
    users := make([]*User, len(ids))

    for i, id := range ids {
        i, id := i, id // 捕获循环变量
        g.Go(func() error {
            user, err := s.GetByID(ctx, id)
            if err != nil {
                return err
            }
            users[i] = user
            return nil
        })
    }

    if err := g.Wait(); err != nil {
        return nil, err
    }
    return users, nil
}

// 使用 sync.Pool 复用对象
var bufferPool = sync.Pool{
    New: func() interface{} {
        return new(bytes.Buffer)
    },
}

func processData(data []byte) ([]byte, error) {
    buf := bufferPool.Get().(*bytes.Buffer)
    defer func() {
        buf.Reset()
        bufferPool.Put(buf)
    }()

    buf.Write(data)
    // ...
    result := make([]byte, buf.Len())
    copy(result, buf.Bytes())
    return result, nil
}

// Worker Pool 模式
func (s *Service) ProcessWithWorkers(ctx context.Context, jobs <-chan Job, workers int) error {
    var wg sync.WaitGroup
    errCh := make(chan error, workers)

    for i := 0; i < workers; i++ {
        wg.Add(1)
        go func() {
            defer wg.Done()
            for job := range jobs {
                if err := s.process(ctx, job); err != nil {
                    errCh <- err
                    return
                }
            }
        }()
    }

    done := make(chan struct{})
    go func() {
        wg.Wait()
        close(done)
    }()

    select {
    case err := <-errCh:
        return err
    case <-done:
        return nil
    case <-ctx.Done():
        return ctx.Err()
    }
}
```

---

## 数据库操作

### GORM 最佳实践

```go
type Repository struct {
    db *gorm.DB
}

func NewRepository(db *gorm.DB) *Repository {
    return &Repository{db: db}
}

func (r *Repository) GetByID(ctx context.Context, id int64) (*User, error) {
    var user User
    err := r.db.WithContext(ctx).
        Where("id = ? AND deleted_at IS NULL", id).
        First(&user).Error

    if errors.Is(err, gorm.ErrRecordNotFound) {
        return nil, ErrNotFound
    }
    if err != nil {
        return nil, fmt.Errorf("failed to get user: %w", err)
    }
    return &user, nil
}

func (r *Repository) List(ctx context.Context, filter UserFilter) ([]*User, int64, error) {
    var users []*User
    var total int64

    query := r.db.WithContext(ctx).Model(&User{}).Where("deleted_at IS NULL")

    if filter.Status != "" {
        query = query.Where("status = ?", filter.Status)
    }
    if filter.Search != "" {
        search := "%" + filter.Search + "%"
        query = query.Where("name LIKE ? OR email LIKE ?", search, search)
    }

    if err := query.Count(&total).Error; err != nil {
        return nil, 0, fmt.Errorf("failed to count users: %w", err)
    }

    offset := (filter.Page - 1) * filter.Size
    if err := query.Offset(offset).Limit(filter.Size).Find(&users).Error; err != nil {
        return nil, 0, fmt.Errorf("failed to list users: %w", err)
    }

    return users, total, nil
}

func (r *Repository) Create(ctx context.Context, user *User) error {
    if err := r.db.WithContext(ctx).Create(user).Error; err != nil {
        return fmt.Errorf("failed to create user: %w", err)
    }
    return nil
}

func (r *Repository) Update(ctx context.Context, user *User) error {
    result := r.db.WithContext(ctx).
        Model(&User{}).
        Where("id = ? AND deleted_at IS NULL", user.ID).
        Updates(user)

    if result.Error != nil {
        return fmt.Errorf("failed to update user: %w", result.Error)
    }
    if result.RowsAffected == 0 {
        return ErrNotFound
    }
    return nil
}

func (r *Repository) SoftDelete(ctx context.Context, id int64) error {
    result := r.db.WithContext(ctx).
        Model(&User{}).
        Where("id = ?", id).
        Update("deleted_at", time.Now())

    if result.Error != nil {
        return fmt.Errorf("failed to delete user: %w", result.Error)
    }
    if result.RowsAffected == 0 {
        return ErrNotFound
    }
    return nil
}
```

---

## 测试规范

```go
// 测试文件命名: xxx_test.go
// 测试函数命名: Test + 函数名 + 场景

func TestGetByID_ExistingUser_ReturnsUser(t *testing.T) {
    // Arrange
    ctrl := gomock.NewController(t)
    defer ctrl.Finish()

    mockRepo := NewMockRepository(ctrl)
    service := NewService(mockRepo)

    expectedUser := &User{ID: 1, Name: "John"}
    mockRepo.EXPECT().
        GetByID(gomock.Any(), int64(1)).
        Return(expectedUser, nil)

    // Act
    user, err := service.GetByID(context.Background(), 1)

    // Assert
    require.NoError(t, err)
    assert.Equal(t, expectedUser, user)
}

func TestGetByID_NonExistingUser_ReturnsError(t *testing.T) {
    // Table-driven test
    tests := []struct {
        name    string
        id      int64
        wantErr error
    }{
        {name: "zero id", id: 0, wantErr: ErrInvalidID},
        {name: "negative id", id: -1, wantErr: ErrInvalidID},
        {name: "not found", id: 999, wantErr: ErrNotFound},
    }

    for _, tt := range tests {
        t.Run(tt.name, func(t *testing.T) {
            ctrl := gomock.NewController(t)
            mockRepo := NewMockRepository(ctrl)

            if tt.wantErr == ErrNotFound {
                mockRepo.EXPECT().
                    GetByID(gomock.Any(), tt.id).
                    Return(nil, ErrNotFound)
            }

            service := NewService(mockRepo)
            _, err := service.GetByID(context.Background(), tt.id)

            require.ErrorIs(t, err, tt.wantErr)
        })
    }
}

// 集成测试
func TestUserRepository_Integration(t *testing.T) {
    if testing.Short() {
        t.Skip("skipping integration test")
    }

    // 使用 testcontainers
    ctx := context.Background()
    container, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
        ContainerRequest: testcontainers.ContainerRequest{
            Image: "postgres:15-alpine",
            Env: map[string]string{
                "POSTGRES_USER":     "test",
                "POSTGRES_PASSWORD": "test",
                "POSTGRES_DB":       "testdb",
            },
            ExposedPorts: []string{"5432/tcp"},
            WaitingFor:   wait.ForListeningPort("5432/tcp"),
        },
        Started: true,
    })
    require.NoError(t, err)
    defer container.Terminate(ctx)

    // 连接数据库并运行测试
    // ...
}
```

---

## 检查清单

### 代码风格

- [ ] 遵循 gofmt 格式
- [ ] 使用 goimports 管理导入
- [ ] 遵循 Go 命名规范
- [ ] 适当的注释

### 错误处理

- [ ] 始终检查错误
- [ ] 使用错误包装
- [ ] 自定义错误类型
- [ ] 错误信息有意义

### 并发安全

- [ ] 正确使用 context
- [ ] 避免数据竞争
- [ ] 适当的同步机制
- [ ] 资源正确释放

### 测试

- [ ] 单元测试覆盖
- [ ] 表驱动测试
- [ ] Mock 使用正确
- [ ] 集成测试完整
