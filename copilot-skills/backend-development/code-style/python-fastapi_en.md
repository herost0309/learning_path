# Python FastAPI Code Style Skill

> Ensure Python FastAPI code follows PEP 8 and modern Python best practices

## Trigger Conditions

- Command: `/python-style`
- File Changes: `**/*.py`

---

## Naming Conventions

### Variables and Functions

```python
# Use snake_case
user_name = "John"           # Correct
userName = "John"            # Wrong

def calculate_total_price(items: list[Item]) -> Decimal:
    """Calculate total price of items"""
    pass

# Private variables use single underscore prefix
_internal_value = 42

# Avoid name conflicts using trailing underscore
class_ = "User"  # Avoid class keyword conflict
```

### Class Naming

```python
# Use PascalCase
class UserService:
    pass

class HttpRequestHandler:
    pass

# Exception classes
class UserNotFoundError(Exception):
    pass

# Abstract base classes
class BaseRepository(ABC):
    @abstractmethod
    def get_by_id(self, id: int) -> Model:
        pass
```

### Constants

```python
# Module-level constants use UPPER_SNAKE_CASE
MAX_CONNECTIONS = 100
DEFAULT_TIMEOUT = 30.0
API_VERSION = "v1"

# Enums
class Status(str, Enum):
    ACTIVE = "active"
    INACTIVE = "inactive"
    PENDING = "pending"
```

---

## Type Annotation Standards

### Function Type Annotations

```python
from typing import Optional, List, Dict, Any, Callable
from collections.abc import Sequence

# Basic type annotations
def get_user(user_id: int) -> Optional[User]:
    """Get user by ID"""
    pass

# Collection type annotations (Python 3.9+)
def get_users(ids: list[int]) -> dict[int, User]:
    pass

# Complex types
def process_data(
    items: Sequence[dict[str, Any]],
    processor: Callable[[dict], Result]
) -> list[Result]:
    pass

# Use TypeVar for generics
T = TypeVar('T')

def first_or_none(items: Sequence[T]) -> Optional[T]:
    return items[0] if items else None
```

### Pydantic Models

```python
from pydantic import BaseModel, Field, EmailStr, validator
from datetime import datetime
from decimal import Decimal

class UserCreate(BaseModel):
    """User creation request"""
    name: str = Field(..., min_length=2, max_length=50, description="User name")
    email: EmailStr = Field(..., description="User email")
    age: int | None = Field(None, ge=0, le=150, description="User age")

    @validator('name')
    def name_must_not_contain_numbers(cls, v):
        if any(char.isdigit() for char in v):
            raise ValueError('Name cannot contain numbers')
        return v

class UserResponse(BaseModel):
    """User response"""
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

## Docstring Standards (Google Style)

```python
def create_user(
    name: str,
    email: str,
    role: Role = Role.USER
) -> User:
    """Create a new user.

    Creates a new user with the provided information and performs necessary validation.

    Args:
        name: User name, cannot be empty, length 2-50 characters.
        email: User email, must be a valid email format.
        role: User role, defaults to regular user.

    Returns:
        Newly created user object with generated ID and timestamp.

    Raises:
        ValidationError: When email format is invalid or name doesn't meet requirements.
        DuplicateEmailError: When email already exists.

    Example:
        >>> user = create_user("John", "john@example.com")
        >>> user.name
        'John'
        >>> user.id
        1
    """
    pass

class UserRepository:
    """User data access layer.

    Provides CRUD operations interface for users, supports async database access.

    Attributes:
        session: SQLAlchemy async session.

    Example:
        >>> async with get_session() as session:
        ...     repo = UserRepository(session)
        ...     user = await repo.get_by_id(1)
    """

    def __init__(self, session: AsyncSession) -> None:
        """Initialize user repository.

        Args:
            session: SQLAlchemy async session.
        """
        self._session = session
```

---

## FastAPI Best Practices

### Route Definition

```python
from fastapi import APIRouter, Depends, HTTPException, status
from typing import Annotated

router = APIRouter(prefix="/api/v1/users", tags=["Users"])

@router.get(
    "/{user_id}",
    response_model=UserResponse,
    summary="Get user by ID",
    description="Get user details for the specified ID",
    responses={
        200: {"description": "Successfully retrieved user"},
        404: {"description": "User not found"},
    }
)
async def get_user(
    user_id: Annotated[int, Path(ge=1, description="User ID")],
    current_user: Annotated[User, Depends(get_current_user)],
    service: Annotated[UserService, Depends(get_user_service)]
) -> UserResponse:
    """Get user details"""
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
    summary="Create user"
)
async def create_user(
    request: UserCreate,
    service: Annotated[UserService, Depends(get_user_service)]
) -> UserResponse:
    """Create a new user"""
    return await service.create(request)
```

### Dependency Injection

```python
from functools import lru_cache
from typing import Annotated

# Database session dependency
async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with async_session_maker() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise

DbSession = Annotated[AsyncSession, Depends(get_db)]

# Service dependency
def get_user_service(db: DbSession) -> UserService:
    return UserService(db)

UserServiceDep = Annotated[UserService, Depends(get_user_service)]

# Current user dependency
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

### Pagination and Filtering

```python
from pydantic import BaseModel, Field

class PaginationParams(BaseModel):
    """Pagination parameters"""
    page: int = Field(1, ge=1, description="Page number")
    size: int = Field(20, ge=1, le=100, description="Items per page")

    @property
    def offset(self) -> int:
        return (self.page - 1) * self.size

class UserFilter(BaseModel):
    """User filter criteria"""
    status: Status | None = None
    search: str | None = None
    created_after: datetime | None = None

class PageResponse(BaseModel, Generic[T]):
    """Paginated response"""
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
    """Get user list"""
    return await service.list_users(pagination, filter_params)
```

---

## Async Code Standards

```python
# Async functions
async def get_user(user_id: int) -> Optional[User]:
    """Get user asynchronously"""
    async with get_session() as session:
        result = await session.execute(
            select(User).where(User.id == user_id)
        )
        return result.scalar_one_or_none()

# Async context manager
@asynccontextmanager
async def get_db_session() -> AsyncGenerator[AsyncSession, None]:
    """Get database session"""
    async with async_session_maker() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()

# Async iterator
async def iter_users(batch_size: int = 100) -> AsyncGenerator[list[User], None]:
    """Iterate users in batches"""
    offset = 0
    while True:
        users = await get_users_batch(offset, batch_size)
        if not users:
            break
        yield users
        offset += batch_size

# Parallel execution
async def get_user_with_orders(user_id: int) -> UserWithOrders:
    """Get user and orders in parallel"""
    user_task = get_user(user_id)
    orders_task = get_orders(user_id)

    user, orders = await asyncio.gather(user_task, orders_task)
    return UserWithOrders(user=user, orders=orders)
```

---

## Error Handling Standards

```python
from fastapi import HTTPException, status
from typing import Self

class AppException(Exception):
    """Application base exception"""
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

# Global exception handler
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

## Import Standards

```python
# Import order (isort standard)
# 1. Standard library
import os
import sys
from datetime import datetime
from typing import Optional, List

# 2. Third-party libraries
import pytest
from fastapi import FastAPI, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

# 3. Local modules
from app.models import User
from app.services import UserService
from app.config import settings

# Avoid using *
from typing import *  # Wrong

# Use explicit imports
from typing import Optional, List, Dict  # Correct
```

---

## Testing Standards

```python
import pytest
from httpx import AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession

@pytest.mark.asyncio
async def test_create_user_success(
    client: AsyncClient,
    db_session: AsyncSession
):
    """Test successful user creation"""
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
    """Test duplicate email creation failure"""
    # Arrange
    user_data = {
        "name": "Jane Doe",
        "email": existing_user.email
    }

    # Act
    response = await client.post("/api/v1/users", json=user_data)

    # Assert
    assert response.status_code == 409

# Using pytest fixtures
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

# Parameterized tests
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
    """Test invalid email format"""
    user_data = {
        "name": "Test User",
        "email": invalid_email
    }

    response = await client.post("/api/v1/users", json=user_data)

    assert response.status_code == 422
```

---

## Checklist

### Code Style

- [ ] Follow PEP 8 standards
- [ ] Use type annotations
- [ ] Functions and classes have docstrings
- [ ] Use f-string for string formatting
- [ ] Correct import order

### FastAPI Standards

- [ ] Use dependency injection
- [ ] Complete response model definitions
- [ ] Unified error handling
- [ ] Complete API documentation annotations
- [ ] Use async functions

### Security Standards

- [ ] Complete input validation
- [ ] Proper authentication and authorization
- [ ] No exposed sensitive information
- [ ] SQL injection protection
