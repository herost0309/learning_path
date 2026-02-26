# Genesys Cloud 通信数字 API - 最佳实践指南

## 概述

Genesys Cloud 是一个 API 优先的微服务平台，每月处理超过 50 亿次 API 请求。本文档概述了设计和使用 Genesys Cloud Communication Digital API 的最佳实践。

**官方文档**: [Genesys Cloud 开发者中心](https://developer.genesys.cloud/commdigital/)

---

## 目录

1. [身份认证](#身份认证)
2. [速率限制](#速率限制)
3. [错误处理](#错误处理)
4. [分页处理](#分页处理)
5. [数字通信渠道](#数字通信渠道)
6. [SDK 使用指南](#sdk-使用指南)
7. [通用最佳实践](#通用最佳实践)
8. [完整示例代码](#完整示例代码)

---

## 身份认证

### OAuth 2.0 认证方式概述

Genesys Cloud 支持多种 OAuth 2.0 认证流程：

| 认证类型 | 适用场景 | 用户交互 |
|---------|---------|---------|
| **Client Credentials** | 服务间调用、后台任务 | 无 |
| **Authorization Code** | Web 应用、需要用户授权 | 有 |
| **Implicit** | 单页应用 (SPA) | 有 |
| **Password** | 受信任的应用（不推荐） | 有 |

### Client Credentials Grant（客户端凭证模式）

适用于服务器到服务器的认证场景（无用户交互）。

#### 所需凭证
- **Client ID** - 从 Genesys Cloud 组织获取
- **Client Secret** - 从 Genesys Cloud 组织获取

#### Python SDK 示例

```python
import PureCloudPlatformClientV2
import os
from typing import Optional

class GenesysAuthClient:
    """Genesys Cloud 认证客户端"""

    def __init__(self, region: str = "mypurecloud.com"):
        """
        初始化认证客户端

        Args:
            region: Genesys Cloud 区域，如 mypurecloud.com, mypurecloud.ie 等
        """
        self.region = region
        self.api_client = PureCloudPlatformClientV2.api_client.ApiClient()
        self.api_client.set_host(f"api.{region}")

    def authenticate_with_client_credentials(
        self,
        client_id: str,
        client_secret: str
    ) -> bool:
        """
        使用客户端凭证进行认证

        Args:
            client_id: OAuth 客户端 ID
            client_secret: OAuth 客户端密钥

        Returns:
            认证是否成功
        """
        try:
            self.api_client.get_client_credentials_token(
                client_id,
                client_secret
            )
            return True
        except Exception as e:
            print(f"认证失败: {e}")
            return False

    def get_valid_client(self) -> PureCloudPlatformClientV2.api_client.ApiClient:
        """获取已认证的 API 客户端"""
        return self.api_client


# 使用示例
if __name__ == "__main__":
    auth_client = GenesysAuthClient(region="mypurecloud.com")

    success = auth_client.authenticate_with_client_credentials(
        client_id=os.environ.get('GENESYS_CLOUD_CLIENT_ID'),
        client_secret=os.environ.get('GENESYS_CLOUD_CLIENT_SECRET')
    )

    if success:
        print("认证成功！")
        api_client = auth_client.get_valid_client()
```

#### Node.js SDK 示例

```javascript
const platformClient = require('platformClient');
const genesysCloud = platformClient.ApiClient.instance;

// 设置区域
genesysCloud.setEnvironment(platformClient.PureCloudRegionHosts.us_east_1);

// 使用客户端凭证认证
async function authenticate(clientId, clientSecret) {
    try {
        await genesysCloud.loginClientCredentialsGrant(clientId, clientSecret);
        console.log('认证成功！');
        return genesysCloud;
    } catch (error) {
        console.error('认证失败:', error);
        throw error;
    }
}

// 使用示例
const clientId = process.env.GENESYS_CLOUD_CLIENT_ID;
const clientSecret = process.env.GENESYS_CLOUD_CLIENT_SECRET;

authenticate(clientId, clientSecret)
    .then(() => console.log('准备调用 API'))
    .catch(err => console.error('初始化失败', err));
```

#### C# / .NET SDK 示例

```csharp
using Genesys.Cloud.Client.Api;
using Genesys.Cloud.Client.Client;
using Genesys.Cloud.Client.Model;
using System;

public class GenesysAuthService
{
    private readonly string _clientId;
    private readonly string _clientSecret;
    private readonly string _region;

    public GenesysAuthService(string clientId, string clientSecret, string region = "mypurecloud.com")
    {
        _clientId = clientId;
        _clientSecret = clientSecret;
        _region = region;
    }

    public bool Authenticate()
    {
        try
        {
            // 配置 API 客户端
            Configuration.Default.ApiClient.SetBasePath($"https://api.{_region}");

            // 获取访问令牌
            var accessToken = Configuration.Default.ApiClient.GetAccessToken(
                _clientId,
                _clientSecret
            );

            // 设置默认授权头
            Configuration.Default.AccessToken = accessToken;

            Console.WriteLine("认证成功！");
            return true;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"认证失败: {ex.Message}");
            return false;
        }
    }
}

// 使用示例
var authService = new GenesysAuthService(
    Environment.GetEnvironmentVariable("GENESYS_CLOUD_CLIENT_ID"),
    Environment.GetEnvironmentVariable("GENESYS_CLOUD_CLIENT_SECRET"),
    "mypurecloud.com"
);

authService.Authenticate();
```

#### Java SDK 示例

```java
import com.mypurecloud.sdk.v2.ApiClient;
import com.mypurecloud.sdk.v2.ApiException;
import com.mypurecloud.sdk.v2.Configuration;
import com.mypurecloud.sdk.v2.PureCloudRegionHosts;

public class GenesysAuthService {

    private final String clientId;
    private final String clientSecret;
    private final PureCloudRegionHosts region;

    public GenesysAuthService(String clientId, String clientSecret, PureCloudRegionHosts region) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.region = region;
    }

    public ApiClient authenticate() throws ApiException {
        // 创建 API 客户端
        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setBasePath(region.getApiHost());

        // 使用客户端凭证获取令牌
        apiClient.getAccessToken(clientId, clientSecret);

        System.out.println("认证成功！");
        return apiClient;
    }

    public static void main(String[] args) {
        String clientId = System.getenv("GENESYS_CLOUD_CLIENT_ID");
        String clientSecret = System.getenv("GENESYS_CLOUD_CLIENT_SECRET");

        GenesysAuthService service = new GenesysAuthService(
            clientId,
            clientSecret,
            PureCloudRegionHosts.us_east_1
        );

        try {
            ApiClient client = service.authenticate();
            // 现在可以使用 client 调用 API
        } catch (ApiException e) {
            System.err.println("认证失败: " + e.getMessage());
        }
    }
}
```

### OAuth 客户端配置

在 Genesys Cloud 中创建 OAuth 客户端时：

| 配置项 | 说明 | 示例 |
|-------|------|-----|
| **Authorization Type** | 授权类型 | Code Authorization |
| **Scopes** | 权限范围 | `audits:readonly`, `users:readonly`, `conversations:read` |
| **Redirect URIs** | 重定向地址 | 根据应用需求配置 |

#### 常用权限范围 (Scopes)

```
# 用户相关
users:readonly          # 只读用户信息
users:write            # 写入用户信息

# 会话相关
conversations:read     # 读取会话信息
conversations:write    # 创建/修改会话

# 消息相关
messages:read          # 读取消息
messages:write         # 发送消息

# 审计相关
audits:readonly        # 读取审计日志

# 通知相关
notifications:read     # 读取通知
notifications:write    # 创建通知订阅
```

### 安全最佳实践

```python
import os
from typing import Optional
import keyring  # 需要安装: pip install keyring

class SecureCredentialManager:
    """安全凭证管理器"""

    SERVICE_NAME = "GenesysCloud"

    @classmethod
    def store_credentials(cls, client_id: str, client_secret: str) -> None:
        """安全存储凭证到系统密钥环"""
        keyring.set_password(cls.SERVICE_NAME, "client_id", client_id)
        keyring.set_password(cls.SERVICE_NAME, "client_secret", client_secret)

    @classmethod
    def get_credentials(cls) -> tuple[Optional[str], Optional[str]]:
        """从系统密钥环获取凭证"""
        try:
            client_id = keyring.get_password(cls.SERVICE_NAME, "client_id")
            client_secret = keyring.get_password(cls.SERVICE_NAME, "client_secret")
            return client_id, client_secret
        except Exception as e:
            print(f"获取凭证失败: {e}")
            return None, None

    @classmethod
    def delete_credentials(cls) -> None:
        """删除存储的凭证"""
        try:
            keyring.delete_password(cls.SERVICE_NAME, "client_id")
            keyring.delete_password(cls.SERVICE_NAME, "client_secret")
        except Exception:
            pass


# 使用环境变量（推荐用于容器化环境）
def get_credentials_from_env() -> tuple[str, str]:
    """从环境变量获取凭证"""
    client_id = os.environ.get('GENESYS_CLOUD_CLIENT_ID')
    client_secret = os.environ.get('GENESYS_CLOUD_CLIENT_SECRET')

    if not client_id or not client_secret:
        raise ValueError("请设置环境变量 GENESYS_CLOUD_CLIENT_ID 和 GENESYS_CLOUD_CLIENT_SECRET")

    return client_id, client_secret
```

#### 安全检查清单

- [ ] 使用环境变量或密钥管理器存储凭证
- [ ] 永远不要将凭证提交到版本控制系统
- [ ] 只请求应用所需的最小权限范围
- [ ] 定期轮换凭证
- [ ] 在令牌过期前实现刷新逻辑
- [ ] 在日志中屏蔽敏感信息

---

## 速率限制

### 理解速率限制

Genesys Cloud 对 API 调用实施速率限制以确保平台稳定性。当超过限制时，API 将返回 **HTTP 429 (Too Many Requests)** 响应。

### 速率限制响应头

监控以下响应头：

| 响应头 | 说明 | 示例值 |
|-------|------|-------|
| `RateLimit-Remaining` | 当前时间窗口内剩余请求数 | `95` |
| `RateLimit-Reset` | 速率限制重置时间（Unix 时间戳） | `1708915200` |
| `Retry-After` | 建议等待秒数 | `30` |
| `X-RateLimit-Limit` | 时间窗口内的总请求数限制 | `100` |

### 处理速率限制

#### 指数退避策略（Python）

```python
import time
import random
import functools
from typing import Callable, TypeVar, Any
import requests

T = TypeVar('T')

class RateLimitHandler:
    """速率限制处理器"""

    def __init__(
        self,
        max_retries: int = 5,
        base_delay: float = 1.0,
        max_delay: float = 60.0,
        jitter: bool = True
    ):
        """
        初始化速率限制处理器

        Args:
            max_retries: 最大重试次数
            base_delay: 基础延迟秒数
            max_delay: 最大延迟秒数
            jitter: 是否添加随机抖动
        """
        self.max_retries = max_retries
        self.base_delay = base_delay
        self.max_delay = max_delay
        self.jitter = jitter

    def calculate_delay(self, attempt: int, retry_after: int = None) -> float:
        """计算重试延迟时间"""
        if retry_after:
            return float(retry_after)

        # 指数退避
        delay = min(self.base_delay * (2 ** attempt), self.max_delay)

        # 添加随机抖动，避免惊群效应
        if self.jitter:
            delay = delay + random.uniform(0, 1)

        return delay

    def execute_with_retry(self, func: Callable[[], requests.Response]) -> requests.Response:
        """
        执行函数并在遇到速率限制时自动重试

        Args:
            func: 返回 Response 对象的函数

        Returns:
            Response 对象

        Raises:
            Exception: 超过最大重试次数
        """
        for attempt in range(self.max_retries):
            response = func()

            if response.status_code == 429:
                retry_after = response.headers.get('Retry-After')
                delay = self.calculate_delay(
                    attempt,
                    int(retry_after) if retry_after else None
                )

                print(f"触发速率限制，等待 {delay:.2f} 秒后重试 (尝试 {attempt + 1}/{self.max_retries})")
                time.sleep(delay)
                continue

            return response

        raise Exception(f"超过最大重试次数 ({self.max_retries})")


# 装饰器方式使用
def with_rate_limit_retry(max_retries: int = 5):
    """速率限制重试装饰器"""
    handler = RateLimitHandler(max_retries=max_retries)

    def decorator(func: Callable[..., T]) -> Callable[..., T]:
        @functools.wraps(func)
        def wrapper(*args, **kwargs) -> T:
            return handler.execute_with_retry(lambda: func(*args, **kwargs))
        return wrapper

    return decorator


# 使用示例
@with_rate_limit_retry(max_retries=3)
def call_genesys_api(url: str, headers: dict) -> requests.Response:
    """调用 Genesys API"""
    return requests.get(url, headers=headers)
```

#### Node.js 速率限制处理

```javascript
const axios = require('axios');

class RateLimitHandler {
    constructor(options = {}) {
        this.maxRetries = options.maxRetries || 5;
        this.baseDelay = options.baseDelay || 1000; // 毫秒
        this.maxDelay = options.maxDelay || 60000;
    }

    /**
     * 计算重试延迟
     */
    calculateDelay(attempt, retryAfter = null) {
        if (retryAfter) {
            return retryAfter * 1000;
        }

        // 指数退避
        let delay = Math.min(
            this.baseDelay * Math.pow(2, attempt),
            this.maxDelay
        );

        // 添加随机抖动
        delay = delay + Math.random() * 1000;

        return delay;
    }

    /**
     * 执行请求并在需要时重试
     */
    async executeWithRetry(requestFn) {
        for (let attempt = 0; attempt < this.maxRetries; attempt++) {
            try {
                const response = await requestFn();
                return response;
            } catch (error) {
                if (error.response && error.response.status === 429) {
                    const retryAfter = error.response.headers['retry-after'];
                    const delay = this.calculateDelay(attempt, retryAfter);

                    console.log(`速率限制，等待 ${delay}ms 后重试 (尝试 ${attempt + 1}/${this.maxRetries})`);
                    await this.sleep(delay);
                    continue;
                }
                throw error;
            }
        }
        throw new Error(`超过最大重试次数 (${this.maxRetries})`);
    }

    sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
}

// 使用示例
async function callGenesysApi(url, accessToken) {
    const handler = new RateLimitHandler({ maxRetries: 3 });

    return handler.executeWithRetry(() =>
        axios.get(url, {
            headers: {
                'Authorization': `Bearer ${accessToken}`,
                'Content-Type': 'application/json'
            }
        })
    );
}
```

### 客户端速率限制

使用令牌桶算法实现本地速率限制：

```python
import time
from threading import Lock
from typing import Optional

class TokenBucket:
    """
    令牌桶算法实现
    适用于客户端主动限流
    """

    def __init__(self, rate: float, capacity: int):
        """
        初始化令牌桶

        Args:
            rate: 每秒添加的令牌数
            capacity: 桶的最大容量
        """
        self.rate = rate
        self.capacity = capacity
        self.tokens = capacity
        self.last_update = time.time()
        self.lock = Lock()

    def _refill(self) -> None:
        """重新填充令牌"""
        now = time.time()
        elapsed = now - self.last_update
        self.tokens = min(
            self.capacity,
            self.tokens + elapsed * self.rate
        )
        self.last_update = now

    def consume(self, tokens: int = 1) -> bool:
        """
        尝试消费指定数量的令牌

        Args:
            tokens: 要消费的令牌数

        Returns:
            是否成功消费
        """
        with self.lock:
            self._refill()

            if self.tokens >= tokens:
                self.tokens -= tokens
                return True
            return False

    def wait_for_token(self, tokens: int = 1, timeout: float = None) -> bool:
        """
        等待直到有足够的令牌可用

        Args:
            tokens: 需要的令牌数
            timeout: 最大等待时间（秒）

        Returns:
            是否成功获取令牌
        """
        start_time = time.time()

        while True:
            if self.consume(tokens):
                return True

            if timeout is not None:
                elapsed = time.time() - start_time
                if elapsed >= timeout:
                    return False

            # 计算需要等待的时间
            with self.lock:
                tokens_needed = tokens - self.tokens
                wait_time = tokens_needed / self.rate

            time.sleep(min(wait_time, 0.1))

    def get_available_tokens(self) -> float:
        """获取当前可用令牌数"""
        with self.lock:
            self._refill()
            return self.tokens


class GenesysRateLimiter:
    """Genesys API 速率限制器"""

    # Genesys Cloud 常见速率限制配置
    DEFAULT_CONFIGS = {
        'default': {'rate': 10, 'capacity': 100},      # 默认: 10 请求/秒
        'conversations': {'rate': 30, 'capacity': 300}, # 会话: 30 请求/秒
        'users': {'rate': 10, 'capacity': 100},        # 用户: 10 请求/秒
        'analytics': {'rate': 5, 'capacity': 50},      # 分析: 5 请求/秒
    }

    def __init__(self, config: dict = None):
        """
        初始化速率限制器

        Args:
            config: 自定义配置，格式为 {'endpoint_type': {'rate': x, 'capacity': y}}
        """
        config = config or self.DEFAULT_CONFIGS
        self.buckets = {}

        for endpoint_type, settings in config.items():
            self.buckets[endpoint_type] = TokenBucket(
                rate=settings['rate'],
                capacity=settings['capacity']
            )

    def acquire(self, endpoint_type: str = 'default', tokens: int = 1) -> bool:
        """
        获取指定端点类型的令牌

        Args:
            endpoint_type: 端点类型
            tokens: 需要的令牌数

        Returns:
            是否成功获取
        """
        bucket = self.buckets.get(endpoint_type, self.buckets['default'])
        return bucket.wait_for_token(tokens)


# 使用示例
rate_limiter = GenesysRateLimiter()

def make_api_call(endpoint: str, endpoint_type: str = 'default'):
    """进行受速率限制保护的 API 调用"""
    rate_limiter.acquire(endpoint_type)
    # 执行实际的 API 调用
    return requests.get(endpoint)
```

### 速率限制最佳实践

| 策略 | 说明 |
|-----|------|
| **缓存响应** | 尽可能缓存 API 响应以减少调用次数 |
| **批量处理** | 如果 API 支持，使用批量操作 |
| **按需获取** | 只获取实际需要的数据 |
| **使用 Webhook** | 考虑使用 Webhook 替代轮询 |
| **监控使用量** | 设置告警监控使用模式 |

---

## 错误处理

### HTTP 状态码

| HTTP 状态码 | 说明 | 处理策略 |
|-----------|------|---------|
| **200** | 成功 | 处理响应数据 |
| **201** | 已创建 | 资源创建成功 |
| **204** | 无内容 | 成功但无响应体 |
| **400** | 请求错误 | 验证请求参数 |
| **401** | 未授权 | 刷新令牌或重新认证 |
| **403** | 禁止访问 | 验证用户角色/权限 |
| **404** | 未找到 | 资源不存在 |
| **409** | 冲突 | 资源状态冲突 |
| **429** | 请求过多 | 实现指数退避 |
| **500** | 服务器内部错误 | 重试并记录日志 |
| **502/503** | 服务不可用 | 重试并记录日志 |

### 错误响应结构

```json
{
  "status": 400,
  "code": "invalid.parameter",
  "message": "参数值无效",
  "contextId": "abc123-def456-ghi789",
  "details": [
    {
      "field": "phoneNumber",
      "message": "电话号码格式无效"
    },
    {
      "field": "messageBody",
      "message": "消息体不能为空"
    }
  ],
  "errors": [
    {
      "field": "toAddress",
      "message": "收件人地址必填",
      "code": "required"
    }
  ]
}
```

### 完整错误处理示例

```python
import json
import logging
from dataclasses import dataclass
from typing import Optional, List, Dict, Any
from enum import Enum
import requests

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger('genesys_api')


class ErrorCode(Enum):
    """Genesys Cloud 错误码枚举"""
    INVALID_PARAMETER = "invalid.parameter"
    UNAUTHORIZED = "unauthorized"
    FORBIDDEN = "forbidden"
    NOT_FOUND = "not_found"
    RATE_LIMITED = "rate_limited"
    INTERNAL_ERROR = "internal_error"
    CONFLICT = "conflict"


@dataclass
class GenesysError:
    """Genesys Cloud 错误详情"""
    status: int
    code: str
    message: str
    context_id: Optional[str] = None
    details: List[Dict[str, Any]] = None

    @classmethod
    def from_response(cls, response: requests.Response) -> 'GenesysError':
        """从响应创建错误对象"""
        try:
            data = response.json()
            return cls(
                status=data.get('status', response.status_code),
                code=data.get('code', 'unknown'),
                message=data.get('message', 'Unknown error'),
                context_id=data.get('contextId'),
                details=data.get('details', [])
            )
        except json.JSONDecodeError:
            return cls(
                status=response.status_code,
                code='unknown',
                message=response.text or 'Unknown error'
            )


class GenesysAPIException(Exception):
    """Genesys API 异常基类"""

    def __init__(self, error: GenesysError):
        self.error = error
        super().__init__(f"[{error.code}] {error.message}")

    def __str__(self):
        return f"GenesysAPIException(status={self.error.status}, code={self.error.code}, message={self.error.message})"


class AuthenticationException(GenesysAPIException):
    """认证异常"""
    pass


class RateLimitException(GenesysAPIException):
    """速率限制异常"""

    def __init__(self, error: GenesysError, retry_after: int = None):
        super().__init__(error)
        self.retry_after = retry_after


class ValidationException(GenesysAPIException):
    """验证异常"""
    pass


class NotFoundException(GenesysAPIException):
    """资源未找到异常"""
    pass


class ServerException(GenesysAPIException):
    """服务器异常"""
    pass


class ErrorHandler:
    """统一的错误处理器"""

    @staticmethod
    def handle_response(response: requests.Response) -> Any:
        """
        处理 API 响应

        Args:
            response: HTTP 响应对象

        Returns:
            解析后的响应数据

        Raises:
            各种 GenesysAPIException
        """
        status_code = response.status_code

        # 成功响应
        if status_code in [200, 201]:
            return response.json() if response.content else None

        if status_code == 204:
            return None

        # 创建错误对象
        error = GenesysError.from_response(response)

        # 记录错误日志
        logger.error(f"API 错误: status={status_code}, code={error.code}, message={error.message}")

        # 根据状态码抛出相应异常
        if status_code == 401:
            raise AuthenticationException(error)

        elif status_code == 403:
            raise AuthenticationException(error)

        elif status_code == 404:
            raise NotFoundException(error)

        elif status_code == 429:
            retry_after = response.headers.get('Retry-After')
            raise RateLimitException(
                error,
                retry_after=int(retry_after) if retry_after else None
            )

        elif status_code == 400:
            raise ValidationException(error)

        elif status_code >= 500:
            raise ServerException(error)

        else:
            raise GenesysAPIException(error)


# 使用示例
def get_conversation_safely(api_client, conversation_id: str):
    """安全获取会话信息"""
    try:
        response = api_client.get(f"/api/v2/conversations/{conversation_id}")
        return ErrorHandler.handle_response(response)

    except AuthenticationException as e:
        logger.error(f"认证失败，请检查令牌: {e}")
        # 尝试刷新令牌
        raise

    except RateLimitException as e:
        logger.warning(f"触发速率限制，建议等待 {e.retry_after} 秒")
        # 实现重试逻辑
        raise

    except NotFoundException as e:
        logger.info(f"会话不存在: {conversation_id}")
        return None

    except ValidationException as e:
        logger.error(f"参数验证失败: {e.error.details}")
        raise

    except ServerException as e:
        logger.error(f"服务器错误: {e}")
        # 实现重试逻辑
        raise
```

### Node.js 错误处理示例

```javascript
const axios = require('axios');

// 错误类型定义
class GenesysAPIError extends Error {
    constructor(status, code, message, details = []) {
        super(message);
        this.name = 'GenesysAPIError';
        this.status = status;
        this.code = code;
        this.details = details;
    }
}

class AuthenticationError extends GenesysAPIError {
    constructor(code, message, details) {
        super(401, code, message, details);
        this.name = 'AuthenticationError';
    }
}

class RateLimitError extends GenesysAPIError {
    constructor(code, message, retryAfter, details) {
        super(429, code, message, details);
        this.name = 'RateLimitError';
        this.retryAfter = retryAfter;
    }
}

class ValidationError extends GenesysAPIError {
    constructor(code, message, details) {
        super(400, code, message, details);
        this.name = 'ValidationError';
    }
}

// 错误处理器
class ErrorHandler {
    static handle(error) {
        if (!error.response) {
            // 网络错误
            throw new GenesysAPIError(0, 'network_error', error.message);
        }

        const { status, data } = error.response;
        const code = data?.code || 'unknown';
        const message = data?.message || error.message;
        const details = data?.details || [];

        console.error(`API 错误: status=${status}, code=${code}, message=${message}`);

        switch (status) {
            case 401:
            case 403:
                throw new AuthenticationError(code, message, details);

            case 400:
                throw new ValidationError(code, message, details);

            case 404:
                return null; // 资源未找到，返回 null

            case 429:
                const retryAfter = error.response.headers['retry-after'];
                throw new RateLimitError(code, message, retryAfter, details);

            case 500:
            case 502:
            case 503:
                throw new GenesysAPIError(status, 'server_error', message, details);

            default:
                throw new GenesysAPIError(status, code, message, details);
        }
    }
}

// 使用示例
async function getConversation(accessToken, conversationId) {
    try {
        const response = await axios.get(
            `https://api.mypurecloud.com/api/v2/conversations/${conversationId}`,
            {
                headers: {
                    'Authorization': `Bearer ${accessToken}`,
                    'Content-Type': 'application/json'
                }
            }
        );
        return response.data;
    } catch (error) {
        return ErrorHandler.handle(error);
    }
}
```

---

## 分页处理

### 分页参数

| 参数 | 说明 | 示例 |
|-----|------|-----|
| `pageSize` / `limit` | 每页项目数 | `?pageSize=100` |
| `pageNumber` / `page` | 当前页码 | `?pageNumber=2` |
| `cursor` / `nextPageToken` | 游标（用于游标分页） | `?cursor=abc123` |

### 响应结构

```json
{
  "entities": [
    { "id": "1", "name": "项目 1" },
    { "id": "2", "name": "项目 2" }
  ],
  "pageSize": 100,
  "pageNumber": 1,
  "total": 500,
  "pageCount": 5,
  "firstUri": "/api/v2/resource?pageSize=100&pageNumber=1",
  "nextUri": "/api/v2/resource?pageSize=100&pageNumber=2",
  "previousUri": null,
  "lastUri": "/api/v2/resource?pageSize=100&pageNumber=5",
  "selfUri": "/api/v2/resource?pageSize=100&pageNumber=1"
}
```

### Python 分页处理器

```python
from typing import List, Dict, Any, Callable, Optional
from dataclasses import dataclass
import time

@dataclass
class PageResult:
    """分页结果"""
    entities: List[Dict[str, Any]]
    page_number: int
    page_size: int
    total: int
    page_count: int
    has_next: bool
    next_uri: Optional[str]


class PaginationHelper:
    """分页辅助类"""

    def __init__(
        self,
        api_client,
        page_size: int = 100,
        max_pages: int = None,
        delay_between_pages: float = 0.1
    ):
        """
        初始化分页辅助类

        Args:
            api_client: API 客户端
            page_size: 每页大小
            max_pages: 最大页数（None 表示无限制）
            delay_between_pages: 页间延迟（秒）
        """
        self.api_client = api_client
        self.page_size = page_size
        self.max_pages = max_pages
        self.delay_between_pages = delay_between_pages

    def get_page(self, endpoint: str, page_number: int = 1) -> PageResult:
        """
        获取单页数据

        Args:
            endpoint: API 端点
            page_number: 页码

        Returns:
            PageResult 对象
        """
        response = self.api_client.get(
            endpoint,
            params={
                'pageSize': self.page_size,
                'pageNumber': page_number
            }
        )

        data = response.json()

        return PageResult(
            entities=data.get('entities', []),
            page_number=data.get('pageNumber', page_number),
            page_size=data.get('pageSize', self.page_size),
            total=data.get('total', 0),
            page_count=data.get('pageCount', 0),
            has_next=bool(data.get('nextUri')),
            next_uri=data.get('nextUri')
        )

    def get_all_pages(
        self,
        endpoint: str,
        processor: Callable[[Dict[str, Any]], Any] = None
    ) -> List[Any]:
        """
        获取所有页面的数据

        Args:
            endpoint: API 端点
            processor: 可选的数据处理函数

        Returns:
            所有数据的列表
        """
        all_items = []
        page_number = 1
        pages_fetched = 0

        while True:
            # 检查是否达到最大页数
            if self.max_pages and pages_fetched >= self.max_pages:
                break

            # 获取当前页
            page_result = self.get_page(endpoint, page_number)

            # 处理数据
            if processor:
                processed_items = [processor(item) for item in page_result.entities]
                all_items.extend(processed_items)
            else:
                all_items.extend(page_result.entities)

            pages_fetched += 1

            # 检查是否还有更多页
            if not page_result.has_next:
                break

            page_number += 1

            # 页间延迟，避免触发速率限制
            if self.delay_between_pages > 0:
                time.sleep(self.delay_between_pages)

        return all_items

    def iterate_pages(self, endpoint: str):
        """
        迭代器方式遍历所有页面

        Args:
            endpoint: API 端点

        Yields:
            每页的数据
        """
        page_number = 1

        while True:
            page_result = self.get_page(endpoint, page_number)
            yield page_result

            if not page_result.has_next:
                break

            page_number += 1

            if self.delay_between_pages > 0:
                time.sleep(self.delay_between_pages)


# 使用示例
def example_usage():
    """分页使用示例"""

    # 初始化分页辅助类
    pagination = PaginationHelper(
        api_client=my_api_client,
        page_size=50,
        max_pages=10,  # 最多获取 10 页
        delay_between_pages=0.2
    )

    # 方式 1: 获取所有数据
    all_users = pagination.get_all_pages('/api/v2/users')
    print(f"获取到 {len(all_users)} 个用户")

    # 方式 2: 使用处理函数
    def extract_user_info(user):
        return {
            'id': user.get('id'),
            'name': user.get('name'),
            'email': user.get('email')
        }

    user_summaries = pagination.get_all_pages(
        '/api/v2/users',
        processor=extract_user_info
    )

    # 方式 3: 使用迭代器
    for page in pagination.iterate_pages('/api/v2/users'):
        print(f"第 {page.page_number} 页，共 {len(page.entities)} 条记录")
        for user in page.entities:
            process_user(user)


# 异步分页处理（适用于大量数据）
import asyncio
from concurrent.futures import ThreadPoolExecutor

class AsyncPaginationHelper:
    """异步分页辅助类"""

    def __init__(self, api_client, page_size: int = 100, max_concurrent: int = 5):
        self.api_client = api_client
        self.page_size = page_size
        self.max_concurrent = max_concurrent

    async def get_all_pages_async(self, endpoint: str) -> List[Dict]:
        """异步获取所有页面"""
        # 首先获取第一页以确定总页数
        first_page = await self._get_page_async(endpoint, 1)
        total_pages = first_page.get('pageCount', 1)

        all_items = list(first_page.get('entities', []))

        # 并发获取剩余页面
        semaphore = asyncio.Semaphore(self.max_concurrent)

        async def fetch_page(page_num):
            async with semaphore:
                page = await self._get_page_async(endpoint, page_num)
                return page.get('entities', [])

        tasks = [
            fetch_page(page_num)
            for page_num in range(2, total_pages + 1)
        ]

        results = await asyncio.gather(*tasks)

        for items in results:
            all_items.extend(items)

        return all_items

    async def _get_page_async(self, endpoint: str, page_number: int) -> Dict:
        """异步获取单页"""
        loop = asyncio.get_event_loop()

        with ThreadPoolExecutor() as executor:
            response = await loop.run_in_executor(
                executor,
                lambda: self.api_client.get(
                    endpoint,
                    params={'pageSize': self.page_size, 'pageNumber': page_number}
                )
            )

        return response.json()
```

### 分页最佳实践

1. **始终处理分页** - 即使结果集较小，API 也可能分页
2. **使用响应中的 `nextUri`** - 不要手动计算页码
3. **检测数据结束** - 返回条目少于请求数量或 `nextUri` 为空
4. **处理速率限制 (429)** - 在页面之间实现重试逻辑
5. **验证游标令牌** - 无效或过期的游标通常返回 400 错误

---

## 数字通信渠道

### 支持的渠道

Genesys Cloud Communication Digital API 支持多种渠道：

| 渠道 | 说明 | 典型用途 |
|-----|------|---------|
| **SMS** | 短信 | 通知、验证码、营销 |
| **Email** | 电子邮件 | 通知、报表、营销 |
| **Web Chat** | 网页聊天 | 客服支持 |
| **WhatsApp** | WhatsApp Business | 客户沟通 |
| **Apple Business Chat** | 苹果商务聊天 | iOS 用户支持 |
| **Facebook Messenger** | FB 消息 | 社交媒体客服 |
| **Twitter/X** | 推特消息 | 社交媒体客服 |

### 常用 API 端点

```
# 会话管理
GET    /api/v2/conversations                           # 列出会话
GET    /api/v2/conversations/{conversationId}          # 获取会话详情
POST   /api/v2/conversations                           # 创建会话
PATCH  /api/v2/conversations/{conversationId}          # 更新会话
DELETE /api/v2/conversations/{conversationId}          # 删除会话

# 消息管理
GET    /api/v2/conversations/{conversationId}/messages # 获取消息列表
POST   /api/v2/conversations/{conversationId}/messages # 发送消息

# 数字渠道集成
GET    /api/v2/conversations/messaging/integrations    # 列出集成
POST   /api/v2/conversations/messaging/integrations    # 创建集成
GET    /api/v2/conversations/messaging/integrations/{integrationId}  # 获取集成详情

# 消息模板
GET    /api/v2/conversations/messaging/templates       # 获取消息模板
POST   /api/v2/conversations/messaging/templates       # 创建消息模板
```

### Python 消息发送示例

```python
import PureCloudPlatformClientV2
from PureCloudPlatformClientV2 import ConversationsApi, MessagingApi
from typing import Optional, List, Dict, Any
from dataclasses import dataclass
from enum import Enum


class MessageType(Enum):
    """消息类型枚举"""
    SMS = "sms"
    EMAIL = "email"
    WHATSAPP = "whatsapp"
    WEBCHAT = "web-messaging"


@dataclass
class MessageRequest:
    """消息请求"""
    message_type: MessageType
    to_address: str
    from_address: str
    body: str
    subject: Optional[str] = None  # 用于邮件
    attachments: Optional[List[Dict]] = None


class GenesysMessagingClient:
    """Genesys Cloud 消息客户端"""

    def __init__(self, api_client):
        """
        初始化消息客户端

        Args:
            api_client: 已认证的 API 客户端
        """
        self.api_client = api_client
        self.conversations_api = ConversationsApi(api_client)
        self.messaging_api = MessagingApi(api_client)

    def send_sms(
        self,
        to_phone: str,
        from_phone: str,
        message: str
    ) -> Dict[str, Any]:
        """
        发送 SMS 短信

        Args:
            to_phone: 接收方电话号码（E.164 格式）
            from_phone: 发送方电话号码
            message: 消息内容

        Returns:
            API 响应
        """
        body = {
            "smsAddress": {
                "type": "sms",
                "address": {
                    "type": "phoneNumber",
                    "phoneNumber": from_phone
                }
            },
            "toAddresses": [
                {
                    "type": "phoneNumber",
                    "phoneNumber": to_phone
                }
            ],
            "messageText": message
        }

        response = self.conversations_api.post_conversations_messages(body)
        return response.to_dict()

    def send_email(
        self,
        to_email: str,
        from_email: str,
        subject: str,
        body: str,
        html_body: str = None,
        attachments: List[Dict] = None
    ) -> Dict[str, Any]:
        """
        发送电子邮件

        Args:
            to_email: 收件人邮箱
            from_email: 发件人邮箱
            subject: 邮件主题
            body: 纯文本正文
            html_body: HTML 正文（可选）
            attachments: 附件列表（可选）

        Returns:
            API 响应
        """
        email_body = {
            "emailAddress": {
                "type": "email",
                "address": {
                    "type": "email",
                    "email": from_email
                }
            },
            "toAddresses": [
                {
                    "type": "email",
                    "email": to_email
                }
            ],
            "subject": subject,
            "messageText": body
        }

        if html_body:
            email_body["htmlBody"] = html_body

        if attachments:
            email_body["attachments"] = attachments

        response = self.conversations_api.post_conversations_emails(email_body)
        return response.to_dict()

    def send_whatsapp_message(
        self,
        to_phone: str,
        message: str,
        integration_id: str = None
    ) -> Dict[str, Any]:
        """
        发送 WhatsApp 消息

        Args:
            to_phone: 接收方电话号码（E.164 格式）
            message: 消息内容
            integration_id: WhatsApp 集成 ID

        Returns:
            API 响应
        """
        body = {
            "messageBody": message,
            "toAddress": {
                "type": "phoneNumber",
                "phoneNumber": to_phone
            },
            "messagingProduct": "whatsapp"
        }

        if integration_id:
            body["integrationId"] = integration_id

        response = self.messaging_api.post_conversations_messages(body)
        return response.to_dict()

    def get_conversation_messages(
        self,
        conversation_id: str,
        page_size: int = 50
    ) -> List[Dict]:
        """
        获取会话中的所有消息

        Args:
            conversation_id: 会话 ID
            page_size: 每页消息数

        Returns:
            消息列表
        """
        all_messages = []
        page_number = 1

        while True:
            response = self.conversations_api.get_conversations_conversation_id_messages(
                conversation_id,
                page_size=page_size,
                page_number=page_number
            )

            messages = response.entities
            all_messages.extend([msg.to_dict() for msg in messages])

            if len(messages) < page_size or not response.next_uri:
                break

            page_number += 1

        return all_messages


# 使用示例
def send_notification():
    """发送通知示例"""
    # 初始化客户端
    auth_client = GenesysAuthClient()
    auth_client.authenticate_with_client_credentials(client_id, client_secret)

    messaging = GenesysMessagingClient(auth_client.get_valid_client())

    # 发送 SMS
    sms_result = messaging.send_sms(
        to_phone="+8613800138000",
        from_phone="+8613900139000",
        message="您的验证码是: 123456，5分钟内有效。"
    )
    print(f"SMS 发送成功: {sms_result['id']}")

    # 发送邮件
    email_result = messaging.send_email(
        to_email="customer@example.com",
        from_email="noreply@company.com",
        subject="订单确认",
        body="您的订单已确认，订单号: #12345",
        html_body="<h1>订单确认</h1><p>您的订单号: <strong>#12345</strong></p>"
    )
    print(f"邮件发送成功: {email_result['id']}")
```

### Node.js 消息发送示例

```javascript
const platformClient = require('platformClient');

class GenesysMessagingClient {
    constructor(apiClient) {
        this.apiClient = apiClient;
        this.conversationsApi = new platformClient.ConversationsApi(apiClient);
        this.messagingApi = new platformClient.MessagingApi(apiClient);
    }

    /**
     * 发送 SMS 短信
     */
    async sendSMS(toPhone, fromPhone, message) {
        const body = {
            smsAddress: {
                type: 'sms',
                address: {
                    type: 'phoneNumber',
                    phoneNumber: fromPhone
                }
            },
            toAddresses: [{
                type: 'phoneNumber',
                phoneNumber: toPhone
            }],
            messageText: message
        };

        try {
            const response = await this.conversationsApi.postConversationsMessages(body);
            return response;
        } catch (error) {
            console.error('发送 SMS 失败:', error);
            throw error;
        }
    }

    /**
     * 发送电子邮件
     */
    async sendEmail(toEmail, fromEmail, subject, body, htmlBody = null, attachments = null) {
        const emailBody = {
            emailAddress: {
                type: 'email',
                address: {
                    type: 'email',
                    email: fromEmail
                }
            },
            toAddresses: [{
                type: 'email',
                email: toEmail
            }],
            subject: subject,
            messageText: body
        };

        if (htmlBody) {
            emailBody.htmlBody = htmlBody;
        }

        if (attachments) {
            emailBody.attachments = attachments;
        }

        try {
            const response = await this.conversationsApi.postConversationsEmails(emailBody);
            return response;
        } catch (error) {
            console.error('发送邮件失败:', error);
            throw error;
        }
    }

    /**
     * 获取会话消息
     */
    async getConversationMessages(conversationId, pageSize = 50) {
        const allMessages = [];
        let pageNumber = 1;

        while (true) {
            const response = await this.conversationsApi.getConversationsConversationIdMessages(
                conversationId,
                {
                    pageSize: pageSize,
                    pageNumber: pageNumber
                }
            );

            allMessages.push(...response.entities);

            if (response.entities.length < pageSize || !response.nextUri) {
                break;
            }

            pageNumber++;
        }

        return allMessages;
    }
}

// 使用示例
async function main() {
    // 认证
    const client = platformClient.ApiClient.instance;
    client.setEnvironment(platformClient.PureCloudRegionHosts.us_east_1);

    await client.loginClientCredentialsGrant(
        process.env.GENESYS_CLOUD_CLIENT_ID,
        process.env.GENESYS_CLOUD_CLIENT_SECRET
    );

    // 创建消息客户端
    const messaging = new GenesysMessagingClient(client);

    // 发送 SMS
    const smsResult = await messaging.sendSMS(
        '+8613800138000',
        '+8613900139000',
        '您的验证码是: 123456'
    );
    console.log('SMS 发送成功:', smsResult.id);

    // 发送邮件
    const emailResult = await messaging.sendEmail(
        'customer@example.com',
        'noreply@company.com',
        '订单确认',
        '您的订单已确认',
        '<h1>订单确认</h1>'
    );
    console.log('邮件发送成功:', emailResult.id);
}

main().catch(console.error);
```

### Webhook 集成

使用 Webhook 替代轮询来获取实时更新：

```python
from flask import Flask, request, jsonify
import json
import hmac
import hashlib
import logging

app = Flask(__name__)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Webhook 签名验证密钥
WEBHOOK_SECRET = "your-webhook-secret"

class WebhookHandler:
    """Webhook 事件处理器"""

    SUPPORTED_EVENTS = [
        "v2.conversations.message.create",
        "v2.conversations.message.update",
        "v2.conversations.create",
        "v2.conversations.update",
        "v2.conversations.delete"
    ]

    @staticmethod
    def verify_signature(payload: bytes, signature: str, secret: str) -> bool:
        """验证 Webhook 签名"""
        expected_signature = hmac.new(
            secret.encode(),
            payload,
            hashlib.sha256
        ).hexdigest()

        return hmac.compare_digest(f"sha256={expected_signature}", signature)

    @staticmethod
    def handle_message_created(event_data: dict):
        """处理消息创建事件"""
        conversation_id = event_data.get('conversationId')
        message_id = event_data.get('messageId')
        sender = event_data.get('sender')
        body = event_data.get('body')

        logger.info(f"新消息 - 会话: {conversation_id}, 发送者: {sender}")
        logger.info(f"消息内容: {body[:100]}..." if len(body) > 100 else f"消息内容: {body}")

        # 在这里添加业务逻辑，如：
        # - 自动回复
        # - 转发给坐席
        # - 触发工作流

    @staticmethod
    def handle_conversation_created(event_data: dict):
        """处理会话创建事件"""
        conversation_id = event_data.get('conversationId')
        participants = event_data.get('participants', [])

        logger.info(f"新会话创建: {conversation_id}")
        logger.info(f"参与者数量: {len(participants)}")

    @staticmethod
    def handle_conversation_updated(event_data: dict):
        """处理会话更新事件"""
        conversation_id = event_data.get('conversationId')
        status = event_data.get('status')

        logger.info(f"会话更新: {conversation_id}, 状态: {status}")


@app.route('/webhook/genesys', methods=['POST'])
def handle_webhook():
    """处理 Genesys Cloud Webhook"""

    # 获取请求体和签名
    payload = request.get_data()
    signature = request.headers.get('X-Genesys-Signature', '')

    # 验证签名
    if not WebhookHandler.verify_signature(payload, signature, WEBHOOK_SECRET):
        logger.warning("无效的 Webhook 签名")
        return jsonify({"error": "Invalid signature"}), 401

    # 解析事件
    try:
        event = json.loads(payload)
    except json.JSONDecodeError:
        return jsonify({"error": "Invalid JSON"}), 400

    event_type = event.get('topicName') or event.get('eventTopic')
    event_data = event.get('eventBody', event.get('body', {}))

    logger.info(f"收到 Webhook 事件: {event_type}")

    # 分发到对应的处理器
    if event_type == "v2.conversations.message.create":
        WebhookHandler.handle_message_created(event_data)
    elif event_type == "v2.conversations.create":
        WebhookHandler.handle_conversation_created(event_data)
    elif event_type == "v2.conversations.update":
        WebhookHandler.handle_conversation_updated(event_data)
    else:
        logger.warning(f"未处理的事件类型: {event_type}")

    return jsonify({"status": "received"}), 200


@app.route('/health', methods=['GET'])
def health_check():
    """健康检查端点"""
    return jsonify({"status": "healthy"}), 200


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080, debug=True)
```

### Webhook 配置

```python
import PureCloudPlatformClientV2
from PureCloudPlatformClientV2 import NotificationsApi

def create_webhook_subscription(api_client, webhook_url: str, events: list):
    """
    创建 Webhook 订阅

    Args:
        api_client: 已认证的 API 客户端
        webhook_url: Webhook 接收地址
        events: 订阅的事件列表
    """
    notifications_api = NotificationsApi(api_client)

    # 创建通知订阅
    body = {
        "name": "My Webhook Subscription",
        "description": "订阅会话和消息事件",
        "callbackUrl": webhook_url,
        "eventTypes": events,
        "enabled": True
    }

    response = notifications_api.post_notifications_webhook_subscriptions(body)
    return response


# 使用示例
events_to_subscribe = [
    "v2.conversations.message.create",
    "v2.conversations.message.update",
    "v2.conversations.create",
    "v2.conversations.update"
]

subscription = create_webhook_subscription(
    api_client=api_client,
    webhook_url="https://your-server.com/webhook/genesys",
    events=events_to_subscribe
)

print(f"Webhook 订阅已创建: {subscription.id}")
```

---

## SDK 使用指南

### 可用的 SDK

Genesys Cloud 为多种语言提供官方 SDK：

| 语言 | GitHub 仓库 | 安装方式 |
|-----|------------|---------|
| **Python** | [platform-client-sdk-python](https://github.com/MyPureCloud/platform-client-sdk-python) | `pip install purecloudplatformclientv2` |
| **Node.js** | [platform-client-sdk-node](https://github.com/MyPureCloud/platform-client-sdk-node) | `npm install platform-client` |
| **.NET** | [platform-client-sdk-dotnet](https://github.com/MyPureCloud/platform-client-sdk-dotnet) | `dotnet add package PureCloudPlatform.Client.V2` |
| **Java** | [platform-client-sdk-java](https://github.com/MyPureCloud/platform-client-sdk-java) | Maven/Gradle |
| **Ruby** | [platform-client-sdk-ruby](https://github.com/MyPureCloud/platform-client-sdk-ruby) | `gem install purecloudplatformclientv2` |

### SDK 最佳实践

1. **复用 API 客户端** - 创建一个客户端实例并复用
2. **处理令牌刷新** - SDK 自动处理，但需要实现错误处理
3. **使用适当的权限范围** - 只请求所需的权限
4. **启用日志** - 配置日志以便调试
5. **设置超时** - 根据使用场景配置适当的超时时间

### 区域配置

| 区域 | 区域代码 | API 主机 |
|-----|---------|---------|
| 美国东部 | `us_east_1` | api.mypurecloud.com |
| 美国西部 | `us_west_2` | api.usw2.pure.cloud |
| 欧洲（爱尔兰） | `eu_west_1` | api.mypurecloud.ie |
| 欧洲（法兰克福） | `eu_central_1` | api.mypurecloud.de |
| 亚太（悉尼） | `ap_southeast_2` | api.mypurecloud.com.au |
| 亚太（东京） | `ap_northeast_1` | api.mypurecloud.jp |
| 加拿大 | `ca_central_1` | api.cac1.pure.cloud |

---

## 通用最佳实践

### API 设计原则

1. **无状态操作** - 每个请求应包含所有必要信息
2. **幂等性** - 设计操作使其可以安全重试
3. **版本控制** - 使用 API 版本控制处理破坏性变更
4. **一致的错误响应** - 返回一致的错误结构

### 性能优化

| 策略 | 说明 |
|-----|------|
| **缓存** | 缓存频繁访问的数据 |
| **批量处理** | 尽可能合并多个操作 |
| **压缩** | 对请求/响应体使用 gzip |
| **连接池** | 复用 HTTP 连接 |
| **异步操作** | 对长时间运行的操作使用异步端点 |

### 日志和监控

```python
import logging
import time
from functools import wraps
from typing import Callable

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('genesys_api')


def log_api_call(func: Callable) -> Callable:
    """API 调用日志装饰器"""

    @wraps(func)
    def wrapper(*args, **kwargs):
        start_time = time.time()
        method = func.__name__

        try:
            result = func(*args, **kwargs)
            duration_ms = (time.time() - start_time) * 1000

            logger.info(
                f"API 调用成功 - 方法: {method}, 耗时: {duration_ms:.2f}ms"
            )

            return result

        except Exception as e:
            duration_ms = (time.time() - start_time) * 1000

            logger.error(
                f"API 调用失败 - 方法: {method}, 耗时: {duration_ms:.2f}ms, "
                f"错误: {str(e)}"
            )

            raise

    return wrapper


class APIMetricsCollector:
    """API 指标收集器"""

    def __init__(self):
        self.metrics = {
            'total_calls': 0,
            'successful_calls': 0,
            'failed_calls': 0,
            'total_duration_ms': 0,
            'errors': {}
        }

    def record_call(self, success: bool, duration_ms: float, error: str = None):
        """记录 API 调用"""
        self.metrics['total_calls'] += 1
        self.metrics['total_duration_ms'] += duration_ms

        if success:
            self.metrics['successful_calls'] += 1
        else:
            self.metrics['failed_calls'] += 1
            if error:
                self.metrics['errors'][error] = self.metrics['errors'].get(error, 0) + 1

    def get_summary(self) -> dict:
        """获取指标摘要"""
        avg_duration = (
            self.metrics['total_duration_ms'] / self.metrics['total_calls']
            if self.metrics['total_calls'] > 0 else 0
        )

        return {
            'total_calls': self.metrics['total_calls'],
            'success_rate': (
                self.metrics['successful_calls'] / self.metrics['total_calls'] * 100
                if self.metrics['total_calls'] > 0 else 0
            ),
            'average_duration_ms': avg_duration,
            'error_breakdown': self.metrics['errors']
        }


# 全局指标收集器
metrics = APIMetricsCollector()


@log_api_call
def call_genesys_api_with_metrics(api_client, method: str, endpoint: str, **kwargs):
    """带指标收集的 API 调用"""
    start_time = time.time()

    try:
        response = getattr(api_client, method)(endpoint, **kwargs)
        duration_ms = (time.time() - start_time) * 1000
        metrics.record_call(success=True, duration_ms=duration_ms)
        return response

    except Exception as e:
        duration_ms = (time.time() - start_time) * 1000
        metrics.record_call(
            success=False,
            duration_ms=duration_ms,
            error=type(e).__name__
        )
        raise
```

### 安全注意事项

- **合规认证** - Genesys Cloud 支持 PCI-DSS, SOC 2, HIPAA, GDPR
- **加密敏感数据** - 对传输中和静态数据进行加密
- **审计日志** - 启用审计日志以满足合规要求
- **访问控制** - 使用基于角色的访问控制 (RBAC)
- **API 密钥轮换** - 定期轮换凭证

> **注意**: 审计事件发送到目标时可能有长达 30 分钟的延迟。这可以在账户级别进行自定义。

---

## 完整示例代码

### 示例 1: 批量发送 SMS 通知

```python
import PureCloudPlatformClientV2
from PureCloudPlatformClientV2 import ConversationsApi
import os
import time
from typing import List, Dict
import csv

class BulkSMSNotificationService:
    """批量 SMS 通知服务"""

    def __init__(self, client_id: str, client_secret: str, from_phone: str):
        """
        初始化服务

        Args:
            client_id: OAuth 客户端 ID
            client_secret: OAuth 客户端密钥
            from_phone: 发送方电话号码
        """
        self.from_phone = from_phone
        self.api_client = PureCloudPlatformClientV2.api_client.ApiClient()
        self.api_client.get_client_credentials_token(client_id, client_secret)
        self.conversations_api = ConversationsApi(self.api_client)

        # 速率限制器
        self.request_interval = 0.1  # 100ms 间隔

    def send_single_sms(self, to_phone: str, message: str) -> Dict:
        """发送单条 SMS"""
        body = {
            "smsAddress": {
                "type": "sms",
                "address": {
                    "type": "phoneNumber",
                    "phoneNumber": self.from_phone
                }
            },
            "toAddresses": [{
                "type": "phoneNumber",
                "phoneNumber": to_phone
            }],
            "messageText": message
        }

        response = self.conversations_api.post_conversations_messages(body)
        return response.to_dict()

    def send_bulk_sms(
        self,
        recipients: List[Dict[str, str]],
        message_template: str
    ) -> Dict:
        """
        批量发送 SMS

        Args:
            recipients: 收件人列表，格式为 [{'phone': 'xxx', 'name': 'xxx'}, ...]
            message_template: 消息模板，支持 {name} 占位符

        Returns:
            发送结果统计
        """
        results = {
            'total': len(recipients),
            'success': 0,
            'failed': 0,
            'errors': []
        }

        for recipient in recipients:
            phone = recipient.get('phone')
            name = recipient.get('name', '')

            # 替换模板占位符
            message = message_template.format(name=name)

            try:
                response = self.send_single_sms(phone, message)
                results['success'] += 1
                print(f"✓ 发送成功: {phone}")
            except Exception as e:
                results['failed'] += 1
                results['errors'].append({
                    'phone': phone,
                    'error': str(e)
                })
                print(f"✗ 发送失败: {phone} - {e}")

            # 速率限制
            time.sleep(self.request_interval)

        return results

    def send_from_csv(
        self,
        csv_file: str,
        message_template: str,
        phone_column: str = 'phone',
        name_column: str = 'name'
    ) -> Dict:
        """
        从 CSV 文件批量发送

        Args:
            csv_file: CSV 文件路径
            message_template: 消息模板
            phone_column: 电话号码列名
            name_column: 姓名列名

        Returns:
            发送结果统计
        """
        recipients = []

        with open(csv_file, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                recipients.append({
                    'phone': row[phone_column],
                    'name': row.get(name_column, '')
                })

        return self.send_bulk_sms(recipients, message_template)


# 使用示例
if __name__ == "__main__":
    service = BulkSMSNotificationService(
        client_id=os.environ['GENESYS_CLOUD_CLIENT_ID'],
        client_secret=os.environ['GENESYS_CLOUD_CLIENT_SECRET'],
        from_phone="+8613900139000"
    )

    # 方式 1: 直接传入列表
    recipients = [
        {'phone': '+8613800138000', 'name': '张三'},
        {'phone': '+8613800138001', 'name': '李四'},
        {'phone': '+8613800138002', 'name': '王五'},
    ]

    template = "您好 {name}，您的订单已发货，请注意查收。"

    results = service.send_bulk_sms(recipients, template)
    print(f"\n发送完成: 成功 {results['success']}/{results['total']}")
```

### 示例 2: 会话监控服务

```python
import PureCloudPlatformClientV2
from PureCloudPlatformClientV2 import ConversationsApi, UsersApi
from datetime import datetime, timedelta
from typing import List, Dict, Optional
import threading
import time


class ConversationMonitorService:
    """会话监控服务"""

    def __init__(self, api_client):
        """初始化监控服务"""
        self.api_client = api_client
        self.conversations_api = ConversationsApi(api_client)
        self.users_api = UsersApi(api_client)
        self._running = False
        self._monitor_thread = None

    def get_active_conversations(self) -> List[Dict]:
        """获取所有活跃会话"""
        response = self.conversations_api.get_conversations()
        return [conv.to_dict() for conv in response.entities]

    def get_conversation_details(self, conversation_id: str) -> Dict:
        """获取会话详情"""
        response = self.conversations_api.get_conversations_conversation_id(conversation_id)
        return response.to_dict()

    def get_waiting_conversations(self) -> List[Dict]:
        """获取等待中的会话"""
        active_conversations = self.get_active_conversations()

        waiting = []
        for conv in active_conversations:
            for participant in conv.get('participants', []):
                if participant.get('purpose') == 'customer':
                    # 检查是否在等待
                    if participant.get('calls', []):
                        for call in participant['calls']:
                            if call.get('state') == 'alerting':
                                waiting.append(conv)
                                break

        return waiting

    def get_conversation_metrics(self) -> Dict:
        """获取会话指标"""
        active = self.get_active_conversations()

        metrics = {
            'total_active': len(active),
            'by_channel': {},
            'waiting_count': 0,
            'average_duration_seconds': 0
        }

        total_duration = 0

        for conv in active:
            # 按渠道统计
            conv_type = conv.get('conversationType', 'unknown')
            metrics['by_channel'][conv_type] = metrics['by_channel'].get(conv_type, 0) + 1

            # 计算总时长
            start_time = conv.get('conversationStart')
            if start_time:
                start = datetime.fromisoformat(start_time.replace('Z', '+00:00'))
                duration = (datetime.now(start.tzinfo) - start).total_seconds()
                total_duration += duration

        if metrics['total_active'] > 0:
            metrics['average_duration_seconds'] = total_duration / metrics['total_active']

        # 等待中数量
        metrics['waiting_count'] = len(self.get_waiting_conversations())

        return metrics

    def start_monitoring(self, interval_seconds: int = 60, callback=None):
        """
        开始监控

        Args:
            interval_seconds: 检查间隔（秒）
            callback: 每次检查后的回调函数
        """
        self._running = True

        def monitor_loop():
            while self._running:
                try:
                    metrics = self.get_conversation_metrics()
                    print(f"\n[{datetime.now().isoformat()}] 会话监控报告")
                    print(f"  活跃会话总数: {metrics['total_active']}")
                    print(f"  等待中会话: {metrics['waiting_count']}")
                    print(f"  平均时长: {metrics['average_duration_seconds']:.1f}秒")
                    print("  按渠道分布:")
                    for channel, count in metrics['by_channel'].items():
                        print(f"    - {channel}: {count}")

                    if callback:
                        callback(metrics)

                except Exception as e:
                    print(f"监控出错: {e}")

                time.sleep(interval_seconds)

        self._monitor_thread = threading.Thread(target=monitor_loop, daemon=True)
        self._monitor_thread.start()
        print("会话监控已启动...")

    def stop_monitoring(self):
        """停止监控"""
        self._running = False
        if self._monitor_thread:
            self._monitor_thread.join(timeout=5)
        print("会话监控已停止")


# 使用示例
if __name__ == "__main__":
    # 初始化
    api_client = PureCloudPlatformClientV2.api_client.ApiClient()
    api_client.get_client_credentials_token(
        os.environ['GENESYS_CLOUD_CLIENT_ID'],
        os.environ['GENESYS_CLOUD_CLIENT_SECRET']
    )

    monitor = ConversationMonitorService(api_client)

    # 定义回调函数
    def on_metrics_update(metrics):
        if metrics['waiting_count'] > 5:
            print("⚠️ 警告: 等待会话超过5个！")

    # 开始监控
    monitor.start_monitoring(interval_seconds=30, callback=on_metrics_update)

    # 运行一段时间后停止
    try:
        time.sleep(300)  # 监控5分钟
    except KeyboardInterrupt:
        pass
    finally:
        monitor.stop_monitoring()
```

---

## 资源链接

### 官方文档
- [Genesys Cloud 开发者中心](https://developer.genesys.cloud/)
- [API 文档](https://developer.genesys.cloud/api/)
- [通信数字 API](https://developer.genesys.cloud/commdigital/)

### SDK 和工具
- [Python SDK](https://github.com/MyPureCloud/platform-client-sdk-python)
- [Node.js SDK](https://github.com/MyPureCloud/platform-client-sdk-node)
- [.NET SDK](https://github.com/MyPureCloud/platform-client-sdk-dotnet)
- [Java SDK](https://github.com/MyPureCloud/platform-client-sdk-java)
- [Ruby SDK](https://github.com/MyPureCloud/platform-client-sdk-ruby)

### 社区
- [Genesys 开发者社区](https://developer.genesys.cloud/devfoundry)
- [Genesys AppFoundry](https://appfoundry.genesys.com/)

### 相关服务
- [AWS Genesys Cloud 集成](https://docs.aws.amazon.com/lexv2/latest/dg/contact-center-genesys.html)
- [Amazon Lex V2 集成](https://docs.aws.amazon.com/lexv2/latest/dg/contact-center-genesys.html)

---

*最后更新: 2026年2月*
