# Genesys Cloud Analytics & Data Management API 分析与最佳实践

## 目录
- [概述](#概述)
- [API 架构](#api-架构)
- [认证机制](#认证机制)
- [核心分析端点](#核心分析端点)
- [数据导出机制](#数据导出机制)
- [最佳实践](#最佳实践)
- [重要注意事项](#重要注意事项)
- [代码示例](#代码示例)

---

## 概述

Genesys Cloud Analytics & Data Management API 是 Genesys Cloud 平台的核心组件，提供企业级联系中心数据分析和数据管理能力。

### 主要功能

| 功能模块 | 描述 |
|---------|------|
| **对话分析** | 聚合和详细的对话数据查询 |
| **用户分析** | 代理活动指标和性能分析 |
| **队列分析** | 队列统计和等待时间分析 |
| **转录分析** | 语音和文本转录数据分析 |
| **调查分析** | 客户满意度调查数据 |
| **数据导出** | 异步批量数据导出 |
| **实时监控** | 实时 KPI 和告警 |

### API 版本信息

- **当前版本**: Platform API v2
- **基础 URL**: `https://api.{region}.mypurecloud.com/api/v2/`
- **区域端点**:
  - 美国: `api.mypurecloud.com`
  - 欧洲: `api.mypurecloud.ie`, `api.mypurecloud.de`
  - 亚太: `api.mypurecloud.com.au`, `api.mypurecloud.jp`

---

## API 架构

### 设计原则

Genesys Cloud 采用 **API-First** 架构设计：

1. **开放云平台**: 原生 API 支持扩展性和集成
2. **事件数据平台**: 多阶段数据生命周期管理
3. **RESTful 设计**: 标准的 REST 接口规范
4. **OAuth 2.0 安全**: 行业标准的认证授权机制

### 数据层次结构

```
Organization (组织)
    └── Conversations (对话)
            ├── Segments (段)
            ├── Participants (参与者)
            └── Sessions (会话)
    └── Users (用户)
    └── Queues (队列)
    └── Analytics (分析数据)
```

---

## 认证机制

### OAuth 2.0 授权类型

| 授权类型 | 使用场景 |
|---------|---------|
| **Client Credentials** | 服务间认证、后台任务 |
| **Authorization Code** | 用户上下文、Web 应用 |
| **Implicit** (已弃用) | 单页应用 (不推荐) |

### Client Credentials Grant (推荐用于服务集成)

#### 获取访问令牌

**端点**: `https://login.{region}/oauth/token`

**请求示例**:
```http
POST /oauth/token HTTP/1.1
Host: login.mypurecloud.com
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&client_id={CLIENT_ID}&client_secret={CLIENT_SECRET}
```

**响应示例**:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "analytics:readonly users:readonly"
}
```

#### 区域登录 URL

| 区域 | 登录 URL |
|------|----------|
| 美国 | `login.mypurecloud.com` |
| 德国 | `login.mypurecloud.de` |
| 日本 | `login.mypurecloud.jp` |
| 爱尔兰 | `login.mypurecloud.ie` |
| 澳大利亚 | `login.mypurecloud.com.au` |

### SDK 认证示例

**Python SDK**:
```python
import PureCloudPlatformClientV2
import os

apiClient = PureCloudPlatformClientV2.api_client.ApiClient()
apiClient.get_client_credentials_token(
    os.environ['GENESYS_CLOUD_CLIENT_ID'],
    os.environ['GENESYS_CLOUD_CLIENT_SECRET']
)
```

**Java SDK**:
```java
ApiClient apiClient = ApiClient.Builder.standard().build();
apiClient.authorizeClientCredentials(clientId, clientSecret);
```

---

## 核心分析端点

### 对话分析 API

#### 1. 聚合查询
**端点**: `POST /api/v2/analytics/conversations/aggregates/query`

**功能**: 查询聚合的对话统计数据

**请求体结构**:
```json
{
  "interval": "2024-01-01T00:00:00Z/2024-01-31T23:59:59Z",
  "granularity": "P1D",
  "groupBy": ["queueId", "userId"],
  "filter": {
    "type": "and",
    "predicates": [
      {
        "type": "dimension",
        "dimension": "mediaType",
        "operator": "matches",
        "value": "voice"
      }
    ]
  },
  "metrics": ["tConversationComplete", "tHandle", "nOffered"]
}
```

**参数说明**:

| 参数 | 类型 | 描述 |
|------|------|------|
| `interval` | string | ISO 8601 时间间隔 (开始/结束) |
| `granularity` | string | 聚合粒度 (P1D=天, PT1H=小时) |
| `groupBy` | array | 分组维度 |
| `filter` | object | 过滤条件 |
| `metrics` | array | 指标列表 |

#### 2. 详细查询
**端点**: `POST /api/v2/analytics/conversations/details/query`

**功能**: 查询详细的对话记录

#### 3. 异步任务查询
**端点**: `POST /api/v2/analytics/conversations/details/jobs`

**功能**: 创建异步作业获取大量对话详情

### 其他分析端点

| 端点 | 描述 |
|------|------|
| `/api/v2/analytics/users/aggregates/query` | 用户活动聚合 |
| `/api/v2/analytics/queues/aggregates/query` | 队列统计聚合 |
| `/api/v2/analytics/transcripts/aggregates/query` | 转录数据聚合 |
| `/api/v2/analytics/surveys/aggregates/query` | 调查数据聚合 |
| `/api/v2/analytics/agentstatus/aggregates/query` | 代理状态聚合 |

### ⚠️ 弃用通知

| 端点 | 状态 | 替代方案 |
|------|------|----------|
| `/api/v2/analytics/conversations/transcripts/query` | 2025年6月5日移除 | 使用 `transcripts/aggregates/query` |

---

## 数据导出机制

### 异步导出模式

Genesys Cloud 支持异步数据导出，适用于大量数据场景：

#### 导出流程

```
1. 创建导出作业 → 获取 Job ID
        ↓
2. 轮询作业状态 → 等待完成
        ↓
3. 获取下载链接 → 下载数据
```

#### 作业状态值

| 状态 | 描述 |
|------|------|
| `PENDING` | 等待处理 |
| `PROCESSING` | 正在处理 |
| `COMPLETED` | 已完成 |
| `FAILED` | 失败 |

### 导出限制

| 限制项 | 值 |
|--------|-----|
| 并发作业数 | 最多 5 个/集群 |
| 单次查询时间范围 | 建议不超过 30 天 |
| 作业间隔 | 建议 30分钟 - 1小时 |

---

## 最佳实践

### 1. 认证与安全

- **安全存储凭证**: 使用环境变量或密钥管理服务存储 Client ID 和 Secret
- **令牌缓存**: 缓存访问令牌直至过期，避免频繁请求
- **最小权限原则**: 只申请必要的 OAuth scopes
- **区域选择**: 选择最近的区域端点减少延迟

```python
# 推荐：使用环境变量
import os
client_id = os.environ.get('GENESYS_CLOUD_CLIENT_ID')
client_secret = os.environ.get('GENESYS_CLOUD_CLIENT_SECRET')
```

### 2. API 调用优化

- **批量处理**: 使用批量 API 而非单个请求
- **连接复用**: 使用 HTTP 连接池
- **异步操作**: 大数据量使用异步导出 API
- **合理分页**: 实现游标或偏移分页

### 3. 查询优化

- **限制时间范围**: 单次查询时间范围不超过 30 天
- **精确过滤**: 使用精确的过滤条件减少返回数据量
- **选择必要指标**: 只请求需要的指标字段
- **适当粒度**: 根据需求选择合适的聚合粒度

### 4. 错误处理

```python
import time
import random

def api_call_with_retry(func, max_retries=3):
    for attempt in range(max_retries):
        try:
            return func()
        except RateLimitError as e:
            wait_time = (2 ** attempt) + random.random()
            time.sleep(wait_time)
        except ServerError as e:
            if attempt == max_retries - 1:
                raise
            time.sleep(2 ** attempt)
```

### 5. 数据处理

- **增量同步**: 使用时间戳增量获取数据
- **数据验证**: 验证数据完整性和一致性
- **错误数据**: 处理缺失值和异常值
- **存储优化**: 压缩存储历史数据

---

## 重要注意事项

### ⚠️ MediaType 参数变更

从 2021年2月10日起，分析 API 中的 `mediaType` 参数：
- **旧值**: `call`
- **新值**: `voice`

```json
// 正确用法
{
  "filter": {
    "predicates": [{
      "dimension": "mediaType",
      "value": "voice"  // 使用 "voice" 而非 "call"
    }]
  }
}
```

### ⚠️ 速率限制

- Genesys Cloud 实施严格的 API 速率限制
- 监控响应头: `X-RateLimit-Remaining`, `Retry-After`
- 收到 HTTP 429 时实现指数退避

### ⚠️ 数据延迟

- 分析数据可能存在 5-15 分钟延迟
- 实时数据与历史数据来源不同
- 考虑数据一致性窗口

### ⚠️ 分页处理

- 大数据集使用 cursor-based 分页
- 处理分页令牌过期情况
- 记录断点支持断点续传

### ⚠️ 并发控制

- 限制并发请求数
- 避免同时发起大量导出作业
- 实现请求队列管理

---

## 代码示例

### Python 完整示例

```python
import PureCloudPlatformClientV2
from PureCloudPlatformClientV2.rest import ApiException
import os
import time
from datetime import datetime, timedelta

class GenesysAnalyticsClient:
    def __init__(self, client_id=None, client_secret=None, region='mypurecloud.com'):
        self.client_id = client_id or os.environ.get('GENESYS_CLOUD_CLIENT_ID')
        self.client_secret = client_secret or os.environ.get('GENESYS_CLOUD_CLIENT_SECRET')
        self.region = region

        # 配置 API 客户端
        PureCloudPlatformClientV2.configuration.host = f'https://api.{region}'
        self.api_client = PureCloudPlatformClientV2.api_client.ApiClient()

        # 认证
        self._authenticate()

    def _authenticate(self):
        """使用 Client Credentials 认证"""
        self.api_client.get_client_credentials_token(
            self.client_id,
            self.client_secret
        )

    def query_conversation_aggregates(self, interval_start, interval_end,
                                       group_by=['queueId'], metrics=None):
        """查询对话聚合数据"""
        analytics_api = PureCloudPlatformClientV2.AnalyticsApi(self.api_client)

        # 构建查询体
        body = {
            'interval': f'{interval_start.isoformat()}Z/{interval_end.isoformat()}Z',
            'granularity': 'P1D',
            'groupBy': group_by,
            'metrics': metrics or ['nOffered', 'tHandle']
        }

        try:
            response = analytics_api.post_analytics_conversations_aggregates_query(body)
            return response
        except ApiException as e:
            if e.status == 429:
                # 速率限制，等待后重试
                time.sleep(60)
                return self.query_conversation_aggregates(
                    interval_start, interval_end, group_by, metrics
                )
            raise

    def create_export_job(self, query_body):
        """创建异步导出作业"""
        analytics_api = PureCloudPlatformClientV2.AnalyticsApi(self.api_client)

        # 创建作业
        job = analytics_api.post_analytics_conversations_details_jobs(query_body)
        job_id = job.job_id

        # 轮询状态
        while True:
            status = analytics_api.get_analytics_conversations_details_job(job_id)
            if status.state == 'COMPLETED':
                return status
            elif status.state == 'FAILED':
                raise Exception(f'Export job failed: {status.error_message}')
            time.sleep(10)  # 等待 10 秒后再次检查

# 使用示例
if __name__ == '__main__':
    client = GenesysAnalyticsClient()

    # 查询最近7天的聚合数据
    end_date = datetime.utcnow()
    start_date = end_date - timedelta(days=7)

    result = client.query_conversation_aggregates(
        start_date,
        end_date,
        group_by=['queueId', 'mediaType'],
        metrics=['nOffered', 'nHandled', 'tHandle', 'tWait']
    )

    print(f'Results: {result}')
```

### cURL 示例

```bash
# 获取访问令牌
curl -X POST "https://login.mypurecloud.com/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET"

# 查询对话聚合数据
curl -X POST "https://api.mypurecloud.com/api/v2/analytics/conversations/aggregates/query" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "interval": "2024-01-01T00:00:00Z/2024-01-07T23:59:59Z",
    "granularity": "P1D",
    "groupBy": ["queueId"],
    "metrics": ["nOffered", "tHandle"]
  }'
```

---

## 参考资源

### 官方文档
- [Genesys Developer Center](https://developer.genesys.cloud/)
- [Platform API Reference](https://developer.genesys.cloud/platform/api/)
- [Genesys Cloud Help Center](https://help.mypurecloud.com/)

### SDK 资源
- [Python SDK](https://github.com/MyPureCloud/platform-client-sdk-python)
- [Java SDK](https://github.com/MyPureCloud/platform-client-sdk-java)
- [.NET SDK](https://github.com/MyPureCloud/platform-client-sdk-dotnet)

### 相关指南
- [OAuth Authorization Guide](https://developer.genesys.cloud/authorization/platform-auth/guides/oauth-auth-code-guide)
- [API Usage View](https://help.mypurecloud.com/articles/api-usage-view/)

---

## 更新日志

| 日期 | 版本 | 变更内容 |
|------|------|----------|
| 2026-02-25 | 1.0 | 初始版本 |

---

*本文档基于 Genesys Cloud Platform API v2 编写，最后更新于 2026年2月*
