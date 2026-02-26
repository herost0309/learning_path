# Python FastAPI 代码风格 Skill

> 确保 Python FastAPI 代码遵循 PEP 8 和现代 Python 最佳实践

## 触发条件

- 命令: `/python-style`
- 文件变更: `**/*.py`

---

## 命名规范

### 变量和函数

```python
# 使用 snake_case
user_name = "John"           # 正确
userName = "John"            # 错误

def calculate_total_price(items: list[Item]) -> Decimal:
    """计算商品总价"""
    pass

# 私有变量使用单下划线前缀
_internal_value = 42

# 名称冲突避免使用双下划线
class_ = "User"  # 避免 class 关键字冲突
```

### 类命名

```python
# 使用 PascalCase
class UserService:
    pass

class HttpRequestHandler:
    pass

# 异常类
class UserNotFoundError(Exception):
    pass

# 抽象基类
class BaseRepository(ABC):
    @abstractmethod
    def get_by_id(self, id: int) -> Model:
        pass
```

### 常量

```python
# 模块级常量使用全大写
MAX_CONNECTIONS = 100
DEFAULT_TIMEOUT = 30.0
API_VERSION = "v1"

# 枚举
class Status(str, Enum):
    ACTIVE = "active"
    INACTIVE = "inactive"
    PENDING = "pending"
```

---

## 类型注解规范

### 函数类型注解

```python
from typing import Optional, List, Dict, Any, Callable
from collections.abc import Sequence

# 基本类型注解
def get_user(user_id: int) -> Optional[User]:
    """根据ID获取用户"""
    pass

# 集合类型注解 (Python 3.9+)
def get_users(ids: list[int]) -> dict[int, User]:
    pass

# 复杂类型
def process_data(
    items: Sequence[dict[str, Any]],
    processor: Callable[[dict], Result]
) -> list[Result]:
    pass

# 使用 TypeVar 处理泛型
T = TypeVar('T')

def first_or_none(items: Sequence[T]) -> Optional[T]:
    return items[0] if items else None
```

### Pydantic 模型

```python
from pydantic import BaseModel, Field, EmailStr, validator
from datetime import datetime
from decimal import Decimal

class UserCreate(BaseModel):
    """用户创建请求"""
    name: str = Field(..., min_length=2, max_length=50, description="用户姓名")
    email: EmailStr = Field(..., description="用户邮箱")
    age: int | None = Field(None, ge=0, le=150, description="用户年龄")

    @validator('name')
    def name_must_not_contain_numbers(cls, v):
        if any(char.isdigit() for char in v):
            raise ValueError('姓名不能包含数字')
        return v

class UserResponse(BaseModel):
    """用户响应"""
    id: int
    name: str
    email: str
    status: Status
    created_at: datetime
    updated_at: datetime | None = None

    class Config:
        from_attributes = True  # Pydantic v2
```

---

## 文档字符串规范 (Google Style)

```python
def create_user(
    name: str,
    email: str,
    role: Role = Role.USER
) -> User:
    """创建新用户。

    根据提供的用户信息创建新用户，并进行必要的验证。

    Args:
        name: 用户名称，不能为空，长度2-50字符。
        email: 用户邮箱，需要是有效邮箱格式。
        role: 用户角色，默认为普通用户。

    Returns:
        新创建的用户对象，包含生成的ID和时间戳。

    Raises:
        ValidationError: 当邮箱格式无效或名称不符合要求时。
        DuplicateEmailError: 当邮箱已存在时。

    Example:
        >>> user = create_user("John", "john@example.com")
        >>> user.name
        'John'
        >>> user.id
        1
    """
    pass

class UserRepository:
    """用户数据访问层。

    提供用户的 CRUD 操作接口，支持异步数据库访问。

    Attributes:
        session: SQLAlchemy 异步会话。

    Example:
        >>> async with get_session() as session:
        ...     repo = UserRepository(session)
        ...     user = await repo.get_by_id(1)
    """

    def __init__(self, session: AsyncSession) -> None:
        """初始化用户仓库。

        Args:
            session: SQLAlchemy 异步会话。
        """
        self._session = session
```

---

## FastAPI 最佳实践

### 路由定义

```python
from fastapi import APIRouter, Depends, HTTPException, status
from typing import Annotated

router = APIRouter(prefix="/api/v1/users", tags=["Users"])

@router.get(
    "/{user_id}",
    response_model=UserResponse,
    summary="根据ID获取用户",
    description="获取指定ID的用户详情",
    responses={
        200: {"description": "成功获取用户"},
        404: {"description": "用户不存在"},
    }
)
async def get_user(
    user_id: Annotated[int, Path(ge=1, description="用户ID")],
    current_user: Annotated[User, Depends(get_current_user)],
    service: Annotated[UserService, Depends(get_user_service)]
) -> UserResponse:
    """获取用户详情"""
    user = await service.get_by_id(user_id)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"User with id {user_id} not found"
        )
    return user

@router.post(
    "",
    response_model=UserResponse,
    status_code=status.HTTP_201_CREATED,
    summary="创建用户"
)
async def create_user(
    request: UserCreate,
    service: Annotated[UserService, Depends(get_user_service)]
) -> UserResponse:
    """创建新用户"""
    return await service.create(request)
```

### 依赖注入

```python
from functools import lru_cache
from typing import Annotated

# 数据库会话依赖
async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with async_session_maker() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise

DbSession = Annotated[AsyncSession, Depends(get_db)]

# 服务依赖
def get_user_service(db: DbSession) -> UserService:
    return UserService(db)

UserServiceDep = Annotated[UserService, Depends(get_user_service)]

# 当前用户依赖
async def get_current_user(
    token: Annotated[str, Depends(oauth2_scheme)],
    db: DbSession
) -> User:
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        email: str = payload.get("sub")
        if email is None:
            raise credentials_exception
    except JWTError:
        raise credentials_exception

    user = await UserRepository(db).get_by_email(email)
    if user is None:
        raise credentials_exception
    return user

CurrentUser = Annotated[User, Depends(get_current_user)]
```

### 分页和过滤

```python
from pydantic import BaseModel, Field

class PaginationParams(BaseModel):
    """分页参数"""
    page: int = Field(1, ge=1, description="页码")
    size: int = Field(20, ge=1, le=100, description="每页数量")

    @property
    def offset(self) -> int:
        return (self.page - 1) * self.size

class UserFilter(BaseModel):
    """用户过滤条件"""
    status: Status | None = None
    search: str | None = None
    created_after: datetime | None = None

class PageResponse(BaseModel, Generic[T]):
    """分页响应"""
    items: list[T]
    total: int
    page: int
    size: int
    pages: int

    @classmethod
    def create(cls, items: list[T], total: int, page: int, size: int) -> "PageResponse[T]":
        pages = (total + size - 1) // size
        return cls(items=items, total=total, page=page, size=size, pages=pages)

@router.get("", response_model=PageResponse[UserResponse])
async def list_users(
    pagination: Annotated[PaginationParams, Depends()],
    filter_params: Annotated[UserFilter, Depends()],
    service: UserServiceDep
) -> PageResponse[UserResponse]:
    """获取用户列表"""
    return await service.list_users(pagination, filter_params)
```

---

## 异步代码规范

```python
# 异步函数
async def get_user(user_id: int) -> Optional[User]:
    """异步获取用户"""
    async with get_session() as session:
        result = await session.execute(
            select(User).where(User.id == user_id)
        )
        return result.scalar_one_or_none()

# 异步上下文管理器
@asynccontextmanager
async def get_db_session() -> AsyncGenerator[AsyncSession, None]:
    """获取数据库会话"""
    async with async_session_maker() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()

# 异步迭代器
async def iter_users(batch_size: int = 100) -> AsyncGenerator[list[User], None]:
    """分批迭代用户"""
    offset = 0
    while True:
        users = await get_users_batch(offset, batch_size)
        if not users:
            break
        yield users
        offset += batch_size

# 并行执行
async def get_user_with_orders(user_id: int) -> UserWithOrders:
    """并行获取用户和订单"""
    user_task = get_user(user_id)
    orders_task = get_orders(user_id)

    user, orders = await asyncio.gather(user_task, orders_task)
    return UserWithOrders(user=user, orders=orders)
```

---

## 错误处理规范

```python
from fastapi import HTTPException, status
from typing import Self

class AppException(Exception):
    """应用基础异常"""
    def __init__(
        self,
        code: str,
        message: str,
        status_code: int = 500,
        details: dict | None = None
    ):
        self.code = code
        self.message = message
        self.status_code = status_code
        self.details = details or {}
        super().__init__(message)

    @classmethod
    def not_found(cls, resource: str, identifier: Any) -> Self:
        return cls(
            code="NOT_FOUND",
            message=f"{resource} with id '{identifier}' not found",
            status_code=404,
            details={"resource": resource, "id": str(identifier)}
        )

    @classmethod
    def validation_error(cls, errors: list[dict]) -> Self:
        return cls(
            code="VALIDATION_ERROR",
            message="Validation failed",
            status_code=422,
            details={"errors": errors}
        )

# 全局异常处理
@app.exception_handler(AppException)
async def app_exception_handler(request: Request, exc: AppException):
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "code": exc.code,
            "message": exc.message,
            "details": exc.details,
            "timestamp": datetime.utcnow().isoformat()
        }
    )

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    errors = [
        {"field": ".".join(str(loc) for loc in error["loc"]), "message": error["msg"]}
        for error in exc.errors()
    ]
    return JSONResponse(
        status_code=422,
        content={
            "code": "VALIDATION_ERROR",
            "message": "Request validation failed",
            "errors": errors,
            "timestamp": datetime.utcnow().isoformat()
        }
    )
```

---

## 导入规范

```python
# 导入顺序 (isort 标准)
# 1. 标准库
import os
import sys
from datetime import datetime
from typing import Optional, List

# 2. 第三方库
import pytest
from fastapi import FastAPI, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

# 3. 本地模块
from app.models import User
from app.services import UserService
from app.config import settings

# 避免使用 *
from typing import *  # 错误

# 使用明确的导入
from typing import Optional, List, Dict  # 正确
```

---

## 测试规范

```python
import pytest
from httpx import AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession

@pytest.mark.asyncio
async def test_create_user_success(
    client: AsyncClient,
    db_session: AsyncSession
):
    """测试创建用户成功"""
    # Arrange
    user_data = {
        "name": "John Doe",
        "email": "john@example.com"
    }

    # Act
    response = await client.post("/api/v1/users", json=user_data)

    # Assert
    assert response.status_code == 201
    data = response.json()
    assert data["name"] == "John Doe"
    assert data["email"] == "john@example.com"
    assert "id" in data

@pytest.mark.asyncio
async def test_create_user_duplicate_email(
    client: AsyncClient,
    existing_user: User
):
    """测试重复邮箱创建失败"""
    # Arrange
    user_data = {
        "name": "Jane Doe",
        "email": existing_user.email
    }

    # Act
    response = await client.post("/api/v1/users", json=user_data)

    # Assert
    assert response.status_code == 409

# 使用 pytest fixtures
@pytest.fixture
async def existing_user(db_session: AsyncSession) -> User:
    user = User(
        name="Existing User",
        email="existing@example.com",
        status=Status.ACTIVE
    )
    db_session.add(user)
    await db_session.commit()
    await db_session.refresh(user)
    return user

# 参数化测试
@pytest.mark.parametrize("invalid_email", [
    "",
    "invalid",
    "invalid@",
    "@example.com",
])
@pytest.mark.asyncio
async def test_create_user_invalid_email(
    client: AsyncClient,
    invalid_email: str
):
    """测试无效邮箱格式"""
    user_data = {
        "name": "Test User",
        "email": invalid_email
    }

    response = await client.post("/api/v1/users", json=user_data)

    assert response.status_code == 422
```

---

## 检查清单

### 代码风格

- [ ] 遵循 PEP 8 规范
- [ ] 使用类型注解
- [ ] 函数和类有文档字符串
- [ ] 使用 f-string 格式化字符串
- [ ] 导入顺序正确

### FastAPI 规范

- [ ] 使用依赖注入
- [ ] 响应模型定义完整
- [ ] 错误处理统一
- [ ] API 文档注解完整
- [ ] 使用异步函数

### 安全规范

- [ ] 输入验证完整
- [ ] 认证授权到位
- [ ] 敏感信息不暴露
- [ ] SQL 注入防护
