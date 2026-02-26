# Genesys Cloud API 完整文档

## 目录

- [1. API 概述](#1-api-概述)
- [2. 认证](#2-认证)
- [3. 平台 API](#3-平台-api)
- [4. 路由 API](#4-路由-api)
- [5. 会话 API](#5-会话-api)
- [6. 用户 API](#6-用户-api)
- [7. 队列 API](#7-队列-api)
- [8. 外呼 API](#8-外呼-api)
- [9. 录音 API](#9-录音-api)
- [10. 统计 API](#10-统计-api)
- [11. WebRTC API](#11-webrtc-api)
- [12. 通知 API](#12-通知-api)
- [13. OAuth API](#13-oauth-api)
- [14. 组织 API](#14-组织-api)
- [15. 错误处理](#15-错误处理)

---

## 1. API 概述

### 1.1 基本信息

| 项目 | 值 |
|-----|-----|
| API 版本 | v2 |
| 基础 URL | `https://api.{region}.genesys.com` |
| 协议 | HTTPS |
| 数据格式 | JSON |
| 字符编码 | UTF-8 |

### 1.2 支持的区域

| 区域 | API URL |
|-----|---------|
| 美国 (us-east) | `https://api.usw2.pure.cloud` |
| 欧洲 (eu-west) | `https://api.mypurecloud.ie` |
| 亚太 (ap-northeast-1) | `https://api.apne2.pure.cloud` |
| 日本 (jp-east-1) | `https://api.jp1.pure.cloud` |
| 加拿大 (ca-central) | `https://api.cac1.pure.cloud` |

### 1.3 请求头

```http
Authorization: Bearer {access_token}
Content-Type: application/json
Accept: application/json
```

### 1.4 响应格式

成功响应:
```json
{
  "status": "success",
  "data": { ... }
}
```

错误响应:
```json
{
  "status": "error",
  "code": "ERROR_CODE",
  "message": "Error description"
}
```

---

## 2. 认证

### 2.1 OAuth 2.0 授权

#### 2.1.1 获取访问令牌 (Client Credentials)

```http
POST https://login.{region}.genesys.com/oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
&client_id={client_id}
&client_secret={client_secret}
```

响应:
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

#### 2.1.2 获取访问令牌 (Authorization Code)

```http
POST https://login.{region}.genesys.com/oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code={authorization_code}
&client_id={client_id}
&client_secret={client_secret}
&redirect_uri={redirect_uri}
```

#### 2.1.3 刷新令牌

```http
POST https://login.{region}.genesys.com/oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token
&refresh_token={refresh_token}
&client_id={client_id}
&client_secret={client_secret}
```

### 2.2 认证代码示例

#### JavaScript/Node.js

```javascript
const axios = require('axios');

class GenesysAuth {
  constructor(config) {
    this.clientId = config.clientId;
    this.clientSecret = config.clientSecret;
    this.environment = config.environment;
    this.accessToken = null;
    this.tokenExpiry = null;
  }

  async getAccessToken() {
    if (this.accessToken && this.tokenExpiry && Date.now() < this.tokenExpiry) {
      return this.accessToken;
    }

    const response = await axios.post(
      `https://login.${this.environment}.genesys.com/oauth/token`,
      new URLSearchParams({
        grant_type: 'client_credentials',
        client_id: this.clientId,
        client_secret: this.clientSecret
      }),
      {
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        }
      }
    );

    this.accessToken = response.data.access_token;
    this.tokenExpiry = Date.now() + (response.data.expires_in - 60) * 1000;

    return this.accessToken;
  }

  async getHeaders() {
    const token = await this.getAccessToken();
    return {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
      'Accept': 'application/json'
    };
  }
}
```

#### Python

```python
import requests
from datetime import datetime, timedelta

class GenesysAuth:
    def __init__(self, client_id, client_secret, environment):
        self.client_id = client_id
        self.client_secret = client_secret
        self.environment = environment
        self.access_token = None
        self.token_expiry = None

    def get_access_token(self):
        if self.access_token and self.token_expiry and datetime.now() < self.token_expiry:
            return self.access_token

        response = requests.post(
            f"https://login.{self.environment}.genesys.com/oauth/token",
            data={
                'grant_type': 'client_credentials',
                'client_id': self.client_id,
                'client_secret': self.client_secret
            }
        )

        response.raise_for_status()
        data = response.json()
        self.access_token = data['access_token']
        self.token_expiry = datetime.now() + timedelta(seconds=data['expires_in'] - 60)

        return self.access_token

    def get_headers(self):
        return {
            'Authorization': f'Bearer {self.get_access_token()}',
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        }
```

---

## 3. 平台 API

### 3.1 获取组织信息

```http
GET https://api.{region}.genesys.com/api/v2/organizations/me
```

响应:
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "name": "My Organization",
  "domains": ["example.com"],
  "defaultLanguage": "en-US",
  "thirdPartyOrganizationName": "",
  "version": 1,
  "state": "active",
  "featureIds": [],
  "dateCreated": "2024-01-01T00:00:00.000Z",
  "thirdPartyUri": "",
  "apiLimits": {
    "apiUsage": 100,
    "apiLimit": 1000
  },
  "apiLimitsEnforced": false,
  "apiLimitsEnforcementDate": "2024-12-31T23:59:59.000Z"
}
```

### 3.2 获取区域信息

```http
GET https://api.{region}.genesys.com/api/v2/regions/{regionId}
```

参数:
| 参数 | 类型 | 描述 |
|-----|------|------|
| regionId | string | 区域 ID |

响应:
```json
{
  "id": "us-east-1",
  "name": "US East (N. Virginia)",
  "baseUri": "https://api.usw2.pure.cloud",
  "loginUri": "https://login.usw2.pure.cloud"
}
```

### 3.3 获取服务信息

```http
GET https://api.{region}.genesys.com/api/v2/services
```

响应:
```json
{
  "entities": [
    {
      "id": "a1b2c3d4",
      "name": "routing",
      "serviceClassification": "routing",
      "enabled": true
    },
    {
      "id": "e5f6g7h8",
      "name": "telephony",
      "serviceClassification": "telephony",
      "enabled": true
    }
  ]
}
```

### 3.4 健康检查

```http
GET https://api.{region}.genesys.com/api/v2/status
```

响应:
```json
{
  "service": "Genesys Cloud API",
  "status": "operational",
  "version": "2.0.0",
  "timestamp": "2024-02-19T00:00:00.000Z"
}
```

---

## 4. 路由 API

### 4.1 队列管理

#### 4.1.1 获取所有队列

```http
GET https://api.{region}.genesys.com/api/v2/routing/queues
```

查询参数:
| 参数 | 类型 | 描述 |
|-----|------|------|
| pageSize | integer | 每页数量 (1-100) |
| pageNumber | integer | 页码 |
| name | string | 队列名称过滤 |
| divisionId | string | 部门 ID 过滤 |
| sortBy | string | 排序字段 (name, memberCount) |
| sortOrder | string | 排序方向 (asc, desc) |

响应:
```json
{
  "entities": [
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "name": "Support Queue",
      "description": "Customer support queue",
      "version": 1,
      "dateCreated": "2024-01-01T00:00:00.000Z",
      "dateModified": "2024-01-15T00:00:00.000Z",
      "modifiedBy": "admin@example.com",
      "createdBy": "admin@example.com",
      "division": {
        "id": "div-123",
        "name": "Support Division",
        "selfUri": "/api/v2/organizations/divisions/div-123"
      },
      "memberCount": 10,
      "mediaSettings": {
        "mediaType": "voice",
        "alertingTimeoutSeconds": 30,
        "agentAlertTimeoutSeconds": 30
      },
      "acdAutoAnswer": true,
      "selfUri": "/api/v2/routing/queues/a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    }
  ],
  "pageSize": 25,
  "pageNumber": 1,
  "pageCount": 10,
  "total": 250,
  "selfUri": "/api/v2/routing/queues?pageNumber=1&pageSize=25"
}
```

#### 4.1.2 获取队列详情

```http
GET https://api.{region}.genesys.com/api/v2/routing/queues/{queueId}
```

响应:
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "name": "Support Queue",
  "description": "Customer support queue",
  "version": 1,
  "dateCreated": "2024-01-01T00:00:00.000Z",
  "dateModified": "2024-01-15T00:00:00.000Z",
  "modifiedBy": "admin@example.com",
  "createdBy": "admin@example.com",
  "division": {
    "id": "div-123",
    "name": "Support Division"
  },
  "memberCount": 10,
  "mediaSettings": {
    "voice": {
      "alertingTimeoutSeconds": 30,
      "agentAlertTimeoutSeconds": 30
    },
    "chat": {
      "alertingTimeoutSeconds": 30,
      "agentAlertTimeoutSeconds": 30
    },
    "email": {
      "alertingTimeoutSeconds": 30,
      "agentAlertTimeoutSeconds": 30
    }
  },
  "acdAutoAnswer": true,
  "callingPartyName": "Customer",
  "callingPartyNumber": "",
  "outboundCallerId": {
    "name": "Support",
    "phoneNumber": "+1234567890"
  },
  "queueFlow": {
    "id": "flow-123",
    "name": "Support Inbound Flow"
  },
  "bullseye": {
    "ringAgents": true,
    "ringOffering": true
  },
  "skillEvaluationMethod": "BEST",
  "routingRules": [],
  "selfUri": "/api/v2/routing/queues/a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

#### 4.1.3 创建队列

```http
POST https://api.{region}.genesys.com/api/v2/routing/queues
Content-Type: application/json

{
  "name": "New Support Queue",
  "description": "New customer support queue",
  "divisionId": "div-123",
  "mediaSettings": {
    "voice": {
      "alertingTimeoutSeconds": 30
    }
  },
  "acdAutoAnswer": true
}
```

响应:
```json
{
  "id": "new-queue-id-123",
  "name": "New Support Queue",
  "description": "New customer support queue",
  "version": 1,
  "dateCreated": "2024-02-19T00:00:00.000Z",
  "selfUri": "/api/v2/routing/queues/new-queue-id-123"
}
```

#### 4.1.4 更新队列

```http
PUT https://api.{region}.genesys.com/api/v2/routing/queues/{queueId}
Content-Type: application/json

{
  "name": "Updated Queue Name",
  "description": "Updated description",
  "version": 1
}
```

#### 4.1.5 删除队列

```http
DELETE https://api.{region}.genesys.com/api/v2/routing/queues/{queueId}
```

### 4.2 路由技能组

#### 4.2.1 获取所有技能组

```http
GET https://api.{region}.genesys.com/api/v2/routing/skillgroups
```

响应:
```json
{
  "entities": [
    {
      "id": "sg-123",
      "name": "Support Team",
      "divisionId": "div-123",
      "description": "Support team skill group",
      "mediaSettings": {
        "voice": {},
        "chat": {},
        "email": {}
      },
      "skillIds": ["skill-1", "skill-2"],
      "memberIds": ["user-1", "user-2"],
      "dateCreated": "2024-01-01T00:00:00.000Z",
      "selfUri": "/api/v2/routing/skillgroups/sg-123"
    }
  ]
}
```

#### 4.2.2 创建技能组

```http
POST https://api.{region}.genesys.com/api/v2/routing/skillgroups
Content-Type: application/json

{
  "name": "New Skill Group",
  "divisionId": "div-123",
  "skillIds": ["skill-1", "skill-2"],
  "memberIds": ["user-1", "user-2"]
}
```

### 4.3 路由技能

#### 4.3.1 获取所有技能

```http
GET https://api.{region}.genesys.com/api/v2/routing/skills
```

响应:
```json
{
  "entities": [
    {
      "id": "skill-123",
      "name": "Technical Support",
      "description": "Technical support skill",
      "dateCreated": "2024-01-01T00:00:00.000Z",
      "dateModified": "2024-01-15T00:00:00.000Z",
      "userProficiency": [
        {
          "id": "user-1",
          "name": "John Doe",
          "proficiency": 5
        }
      ],
      "selfUri": "/api/v2/routing/skills/skill-123"
    }
  ]
}
```

#### 4.3.2 创建技能

```http
POST https://api.{region}.genesys.com/api/v2/routing/skills
Content-Type: application/json

{
  "name": "Customer Service",
  "description": "Customer service skill"
}
```

### 4.4 工作代码

#### 4.4.1 获取工作代码

```http
GET https://api.{region}.genesys.com/api/v2/routing/workcodes
```

响应:
```json
{
  "entities": [
    {
      "id": "wc-123",
      "name": "Meeting",
      "category": "Meeting",
      "duration": 30,
      "selfUri": "/api/v2/routing/workcodes/wc-123"
    }
  ]
}
```

### 4.5 语音路由

#### 4.5.1 发起外呼

```http
POST https://api.{region}.genesys.com/api/v2/conversations/calls
Content-Type: application/json

{
  "phoneNumber": "+1234567890",
  "callQueueId": "queue-123",
  "callerId": {
    "name": "Support",
    "phoneNumber": "+0987654321"
  },
  "data": {
    "conversationAttributes": {
      "customerId": "CUST001",
      "campaign": "Outbound"
    }
  }
}
```

响应:
```json
{
  "id": "conv-123",
  "selfUri": "/api/v2/conversations/calls/conv-123"
}
```

---

## 5. 会话 API

### 5.1 创建会话

#### 5.1.1 创建语音会话

```http
POST https://api.{region}.genesys.com/api/v2/conversations/calls
Content-Type: application/json

{
  "phoneNumber": "+1234567890",
  "callQueueId": "queue-123",
  "callerId": {
    "name": "Support",
    "phoneNumber": "+0987654321"
  },
  "data": {
    "conversationAttributes": {
      "customerId": "CUST001"
    }
  }
}
```

#### 5.1.2 创建聊天会话

```http
POST https://api.{region}.genesys.com/api/v2/conversations/chats
Content-Type: application/json

{
  "queueId": "queue-123",
  "memberInfo": {
    "id": "customer-123",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com"
  },
  "attributes": {
    "customerId": "CUST001"
  }
}
```

响应:
```json
{
  "id": "conv-123",
  "selfUri": "/api/v2/conversations/chats/conv-123"
}
```

#### 5.1.3 创建邮件会话

```http
POST https://api.{region}.genesys.com/api/v2/conversations/emails
Content-Type: application/json

{
  "queueId": "queue-123",
  "subject": "Support Request",
  "body": "I need help with my account",
  "direction": "inbound",
  "from": {
    "email": "customer@example.com",
    "name": "John Doe"
  },
  "to": [
    {
      "email": "support@example.com"
    }
  ]
}
```

### 5.2 获取会话列表

```http
GET https://api.{region}.genesys.com/api/v2/conversations
```

查询参数:
| 参数 | 类型 | 描述 |
|-----|------|------|
| pageSize | integer | 每页数量 |
| pageNumber | integer | 页码 |
| sortBy | string | 排序字段 |
| sortOrder | string | 排序方向 |
| userId | string | 用户 ID 过滤 |
| queueId | string | 队列 ID 过滤 |
| communicationType | string | 通信类型过滤 |
| state | string | 状态过滤 |

响应:
```json
{
  "entities": [
    {
      "id": "conv-123",
      "selfUri": "/api/v2/conversations/conv-123",
      "communicationId": "comm-123",
      "conversationIds": ["conv-123"],
      "startTime": "2024-02-19T10:00:00.000Z",
      "endTime": "2024-02-19T10:30:00.000Z",
      "participants": [
        {
          "id": "part-1",
          "name": "John Doe",
          "address": "+1234567890",
          "media": "voice",
          "purpose": "customer",
          "state": "active",
          "startTime": "2024-02-19T10:00:00.000Z",
          "endTime": null,
          "connectedTime": "2024-02-19T10:01:00.000Z",
          "wrapupRequired": false,
          "wrapupTimeoutSeconds": null,
          "direction": "inbound",
          "flows": [],
          "provider": "Edge"
        }
      ],
      "mediaType": "voice",
      "initialState": "active",
      "state": "active",
      "conversationDivision": {
        "id": "div-123",
        "name": "Support Division"
      },
      "conversationProperties": {}
    }
  ],
  "pageSize": 25,
  "pageNumber": 1,
  "pageCount": 10,
  "total": 250
}
```

### 5.3 获取会话详情

```http
GET https://api.{region}.genesys.com/api/v2/conversations/{conversationId}
```

响应:
```json
{
  "id": "conv-123",
  "selfUri": "/api/v2/conversations/conv-123",
  "communicationId": "comm-123",
  "conversationIds": ["conv-123"],
  "startTime": "2024-02-19T10:00:00.000Z",
  "endTime": "2024-02-19T10:30:00.000Z",
  "participants": [
    {
      "id": "part-1",
      "name": "John Doe",
      "address": "+1234567890",
      "media": "voice",
      "purpose": "customer",
      "state": "active",
      "startTime": "2024-02-19T10:00:00.000Z",
      "endTime": null,
      "connectedTime": "2024-02-19T10:01:00.000Z",
      "wrapupRequired": false,
      "wrapupTimeoutSeconds": null,
      "direction": "inbound",
      "provider": "Edge",
      "attributes": {
        "customerId": "CUST001"
      }
    },
    {
      "id": "part-2",
      "name": "Agent Smith",
      "address": "agent-123",
      "media": "voice",
      "purpose": "agent",
      "state": "active",
      "startTime": "2024-02-19T10:00:00.000Z",
      "endTime": null,
      "connectedTime": "2024-02-19T10:01:00.000Z",
      "wrapupRequired": true,
      "wrapupTimeoutSeconds": 120,
      "direction": "inbound",
      "provider": "Edge",
      "user": {
        "id": "agent-123",
        "name": "Agent Smith",
        "selfUri": "/api/v2/users/agent-123"
      },
      "routingStatus": {
        "status": "INTERACTING",
        "startTime": "2024-02-19T10:01:00.000Z"
      }
    }
  ],
  "mediaType": "voice",
  "initialState": "active",
  "state": "active",
  "conversationDivision": {
    "id": "div-123",
    "name": "Support Division"
  }
}
```

### 5.4 会话转接

#### 5.4.1 转接到队列

```http
POST https://api.{region}.genesys.com/api/v2/conversations/{conversationId}/participants/{participantId}/transfers
Content-Type: application/json

{
  "userId": null,
  "queueId": "queue-123",
  "priority": 5,
  "skills": [
    {
      "id": "skill-123",
      "proficiency": 5
    }
  ],
  "languageId": "en-US"
}
```

#### 5.4.2 转接到用户

```http
POST https://api.{region}.genesys.com/api/v2/conversations/{conversationId}/participants/{participantId}/transfers
Content-Type: application/json

{
  "userId": "user-123",
  "queueId": null,
  "priority": 5
}
```

### 5.5 会话保持

```http
POST https://api.{region}.genesys.com/api/v2/conversations/{conversationId}/participants/{participantId}/consults
Content-Type: application/json

{
  "speakTo": "DESTINATION",
  "userId": "user-456",
  "queueId": "queue-456",
  "priority": 5
}
```

### 5.6 结束会话

```http
DELETE https://api.{region}.genesys.com/api/v2/conversations/{conversationId}/participants/{participantId}
```

### 5.7 添加属性到会话

```http
POST https://api.{region}.genesys.com/api/v2/conversations/{conversationId}/attributes
Content-Type: application/json

{
  "customerId": "CUST001",
  "vipLevel": "GOLD",
  "accountNumber": "ACC123456"
}
```

### 5.8 更新会话参与者

```http
PATCH https://api.{region}.genesys.com/api/v2/conversations/{conversationId}/participants/{participantId}
Content-Type: application/json

{
  "state": "connected",
  "wrapupRequired": true,
  "wrapupTimeoutSeconds": 120
}
```

---

## 6. 用户 API

### 6.1 获取用户列表

```http
GET https://api.{region}.genesys.com/api/v2/users
```

查询参数:
| 参数 | 类型 | 描述 |
|-----|------|------|
| pageSize | integer | 每页数量 (1-100) |
| pageNumber | integer | 页码 |
| id | string[] | 用户 ID 列表过滤 |
| username | string | 用户名过滤 |
| firstName | string | 名字过滤 |
| lastName | string | 姓氏过滤 |
| email | string | 邮箱过滤 |
| departmentId | string | 部门 ID 过滤 |
| divisionId | string | 部门 ID 过滤 |
| skillId | string[] | 技能 ID 过滤 |
| presence | string[] | 在线状态过滤 |
| routingStatus | string[] | 路由状态过滤 |
| expand | string[] | 扩展信息 |
| state | string[] | 用户状态过滤 |
| sortBy | string | 排序字段 |
| sortOrder | string | 排序方向 |

响应:
```json
{
  "entities": [
    {
      "id": "user-123",
      "name": "John Doe",
      "username": "johndoe",
      "division": {
        "id": "div-123",
        "name": "Support Division",
        "selfUri": "/api/v2/organizations/divisions/div-123"
      },
      "email": "john.doe@example.com",
      "title": "Support Agent",
      "department": {
        "id": "dept-123",
        "name": "Customer Support"
      },
      "primaryContactInfo": [
        {
          "address": "+1234567890",
          "mediaType": "PHONE",
          "type": "WORK",
          "extension": "1234"
        }
      ],
      "addresses": [
        {
          "address": "john.doe@example.com",
          "mediaType": "EMAIL",
          "type": "WORK"
        }
      ],
      "state": "active",
      "acdAutoAnswer": true,
      "selfUri": "/api/v2/users/user-123"
    }
  ],
  "pageSize": 25,
  "pageNumber": 1,
  "pageCount": 10,
  "total": 250
}
```

### 6.2 获取用户详情

```http
GET https://api.{region}.genesys.com/api/v2/users/{userId}
```

查询参数:
| 参数 | 类型 | 描述 |
|-----|------|------|
| expand | string[] | 扩展信息 (presence, routingStatus, authorization, profileSkills, station, location) |

响应:
```json
{
  "id": "user-123",
  "name": "John Doe",
  "username": "johndoe",
  "division": {
    "id": "div-123",
    "name": "Support Division"
  },
  "email": "john.doe@example.com",
  "title": "Support Agent",
  "department": {
    "id": "dept-123",
    "name": "Customer Support",
    "selfUri": "/api/v2/organizations/departments/dept-123"
  },
  "primaryContactInfo": [
    {
      "address": "+1234567890",
      "mediaType": "PHONE",
      "type": "WORK",
      "extension": "1234"
    }
  ],
  "addresses": [
    {
      "address": "john.doe@example.com",
      "mediaType": "EMAIL",
      "type": "WORK"
    }
  ],
  "state": "active",
  "acdAutoAnswer": true,
  "acdSoftphone": true,
  "version": 1,
  "dateCreated": "2024-01-01T00:00:00.000Z",
  "dateModified": "2024-01-15T00:00:00.000Z",
  "selfUri": "/api/v2/users/user-123",
  "presence": {
    "id": "presence-123",
    "name": "Available",
    "source": "PURECLOUD",
    "primary": true,
    "selfUri": "/api/v2/users/user-123/presence"
  },
  "routingStatus": {
    "status": "IDLE",
    "startTime": "2024-02-19T10:00:00.000Z"
  },
  "station": {
    "id": "station-123",
    "name": "Station 1",
    "type": "PHYSICAL",
    "associatedUser": {
      "id": "user-123",
      "name": "John Doe"
    },
    "selfUri": "/api/v2/stations/station-123"
  },
  "location": {
    "id": "loc-123",
    "name": "Office",
    "selfUri": "/api/v2/locations/loc-123"
  }
}
```

### 6.3 创建用户

```http
POST https://api.{region}.genesys.com/api/v2/users
Content-Type: application/json

{
  "name": "Jane Smith",
  "username": "janesmith",
  "email": "jane.smith@example.com",
  "password": "SecurePassword123!",
  "divisionId": "div-123",
  "departmentId": "dept-123",
  "title": "Support Agent",
  "addresses": [
    {
      "address": "jane.smith@example.com",
      "mediaType": "EMAIL",
      "type": "WORK"
    },
    {
      "address": "+19876543210",
      "mediaType": "PHONE",
      "type": "WORK",
      "extension": "5678"
    }
  ]
}
```

### 6.4 更新用户

```http
PATCH https://api.{region}.genesys.com/api/v2/users/{userId}
Content-Type: application/json

{
  "name": "Jane Doe Smith",
  "title": "Senior Support Agent",
  "version": 1
}
```

### 6.5 删除用户

```http
DELETE https://api.{region}.genesys.com/api/v2/users/{userId}
```

### 6.6 用户在线状态

#### 6.6.1 获取用户在线状态

```http
GET https://api.{region}.genesys.com/api/v2/users/{userId}/presences
```

响应:
```json
{
  "id": "presence-123",
  "name": "Available",
  "source": "PURECLOUD",
  "primary": true,
  "definition": {
    "id": "def-123",
    "name": "Available",
    "systemPresence": "AVAILABLE",
    "selfUri": "/api/v2/presencedefinitions/def-123"
  },
  "selfUri": "/api/v2/users/user-123/presence"
}
```

#### 6.6.2 更新用户在线状态

```http
PATCH https://api.{region}.genesys.com/api/v2/users/{userId}/presences
Content-Type: application/json

{
  "presenceDefinition": {
    "id": "def-123"
  }
}
```

### 6.7 用户路由状态

#### 6.7.1 获取用户路由状态

```http
GET https://api.{region}.genesys.com/api/v2/users/{userId}/routingstatus
```

响应:
```json
{
  "userId": "user-123",
  "status": "IDLE",
  "startTime": "2024-02-19T10:00:00.000Z",
  "secondsInStatus": 3600
}
```

#### 6.7.2 设置用户路由状态

```http
PUT https://api.{region}.genesys.com/api/v2/users/{userId}/routingstatus
Content-Type: application/json

{
  "status": "OFF_QUEUE"
}
```

可用的路由状态:
| 状态 | 描述 |
|-----|------|
| `IDLE` | 空闲，可以接收呼叫 |
| `INTERACTING` | 正在与客户交互 |
| `NOT_RESPONDING` | 无应答 |
| `COMMUNICATING` | 正在通话中 |
| `OFF_QUEUE` | 已从队列中移除 |

### 6.8 用户技能

#### 6.8.1 获取用户技能

```http
GET https://api.{region}.genesys.com/api/v2/users/{userId}/skills
```

响应:
```json
{
  "entities": [
    {
      "id": "skill-123",
      "name": "Technical Support",
      "proficiency": 5,
      "selfUri": "/api/v2/routing/skills/skill-123"
    },
    {
      "id": "skill-456",
      "name": "Customer Service",
      "proficiency": 4,
      "selfUri": "/api/v2/routing/skills/skill-456"
    }
  ]
}
```

#### 6.8.2 更新用户技能

```http
POST https://api.{region}.genesys.com/api/v2/users/{userId}/skills
Content-Type: application/json

[
  {
    "id": "skill-123",
    "proficiency": 5
  },
  {
    "id": "skill-456",
    "proficiency": 3
  }
]
```

### 6.9 用户队列成员资格

#### 6.9.1 获取用户队列

```http
GET https://api.{region}.genesys.com/api/v2/users/{userId}/queues
```

响应:
```json
{
  "entities": [
    {
      "id": "queue-123",
      "name": "Support Queue",
      "joined": true,
      "selfUri": "/api/v2/routing/queues/queue-123"
    }
  ]
}
```

#### 6.9.2 添加用户到队列

```http
POST https://api.{region}.genesys.com/api/v2/users/{userId}/queues
Content-Type: application/json

[
  {
    "id": "queue-123",
    "joined": true
  }
]
```

---

## 7. 队列 API

### 7.1 队列统计

#### 7.1.1 获取队列实时统计

```http
GET https://api.{region}.genesys.com/api/v2/analytics/queues/{queueId}/observations
```

查询参数:
| 参数 | 类型 | 描述 |
|-----|------|------|
| filter | string | 过滤条件 |
| window | string | 统计窗口 (PT1H, PT6H, PT12H, P1D) |

响应:
```json
{
  "results": [
    {
      "metric": {
        "metricId": "n_wait",
        "metricName": "Waiting Interactions",
        "metricCategory": "INTERACTION",
        "selfUri": "/api/v2/analytics/metrics/n_wait"
      },
      "data": [
        {
          "qualifier": {
            "mediaType": "voice"
          },
          "stats": {
            "count": 5,
            "max": null,
            "min": null,
            "sum": null
          }
        }
      ]
    },
    {
      "metric": {
        "metricId": "t_talk",
        "metricName": "Average Talk Time",
        "metricCategory": "INTERACTION",
        "selfUri": "/api/v2/analytics/metrics/t_talk"
      },
      "data": [
        {
          "qualifier": {
            "mediaType": "voice"
          },
          "stats": {
            "count": 50,
            "max": 900,
            "min": 60,
            "sum": 18000
          }
        }
      ]
    }
  ],
  "selfUri": "/api/v2/analytics/queues/queue-123/observations"
}
```

#### 7.1.2 获取队列历史统计

```http
POST https://api.{region}.genesys.com/api/v2/analytics/queues/observations/query
Content-Type: application/json

{
  "interval": "2024-02-18T00:00:00.000Z/2024-02-19T00:00:00.000Z",
  "filter": {
    "type": "and",
    "clauses": [
      {
        "type": "dimension",
        "dimension": "queueId",
        "operator": "matches",
        "value": "queue-123"
      }
    ]
  },
  "metrics": [
    "n_waiting",
    "n_offered",
    "t_abandon"
  ],
  "groupBy": ["queueId", "mediaType"],
  "flattenMultivaluedDimensions": true
}
```

### 7.2 队列成员管理

#### 7.2.1 获取队列成员

```http
GET https://api.{region}.genesys.com/api/v2/routing/queues/{queueId}/members
```

响应:
```json
{
  "entities": [
    {
      "id": "user-123",
      "name": "John Doe",
      "joined": true,
      "memberBy": "admin@example.com",
      "rank": 10,
      "rankingPercentage": 0,
      "languageId": "en-US",
      "skills": [
        {
          "id": "skill-123",
          "name": "Technical Support",
          "proficiency": 5
        }
      ],
      "maximumCalls": 1,
      "secondaryStatus": "OFF_QUEUE",
      "selfUri": "/api/v2/users/user-123"
    }
  ],
  "pageSize": 100,
  "pageNumber": 1,
  "pageCount": 1,
  "total": 10
}
```

#### 7.2.2 添加成员到队列

```http
POST https://api.{region}.genesys.com/api/v2/routing/queues/{queueId}/members
Content-Type: application/json

{
  "userId": "user-123",
  "rank": 10,
  "languageId": "en-US",
  "skills": [
    {
      "id": "skill-123",
      "proficiency": 5
    }
  ],
  "maximumCalls": 1
}
```

#### 7.2.3 更新队列成员

```http
PATCH https://api.{region}.genesys.com/api/v2/routing/queues/{queueId}/members/{memberId}
Content-Type: application/json

{
  "rank": 15,
  "maximumCalls": 2
}
```

#### 7.2.4 删除队列成员

```http
DELETE https://api.{region}.genesys.com/api/v2/routing/queues/{queueId}/members/{memberId}
```

---

## 8. 外呼 API

### 8.1 外呼活动

#### 8.1.1 获取外呼活动列表

```http
GET https://api.{region}.genesys.com/api/v2/outbound/campaigns
```

响应:
```json
{
  "entities": [
    {
      "id": "campaign-123",
      "name": "Sales Campaign",
      "campaignStatus": "RUNNING",
      "queue": {
        "id": "queue-123",
        "name": "Sales Queue"
      },
      "progress": 50,
      "startTime": "2024-02-19T09:00:00.000Z",
      "endTime": "2024-02-19T18:00:00.000Z",
      "timeZone": "America/New_York",
      "selfUri": "/api/v2/outbound/campaigns/campaign-123"
    }
  ],
  "pageSize": 25,
  "pageNumber": 1
}
```

#### 8.1.2 创建外呼活动

```http
POST https://api.{region}.genesys.com/api/v2/outbound/campaigns
Content-Type: application/json

{
  "name": "New Campaign",
  "queueId": "queue-123",
  "campaignType": "OUTBOUND",
  "dialingMode": "AGENT",
  "progress": 0,
  "startTime": "2024-02-19T09:00:00.000Z",
  "endTime": "2024-02-19T18:00:00.000Z",
  "timeZone": "America/New_York",
  "contactListId": "list-123"
}
```

#### 8.1.3 更新外呼活动

```http
PUT https://api.{region}.genesys.com/api/v2/outbound/campaigns/{campaignId}
Content-Type: application/json

{
  "name": "Updated Campaign",
  "campaignStatus": "RUNNING"
}
```

### 8.2 外呼联系人列表

#### 8.2.1 获取联系人列表

```http
GET https://api.{region}.genesys.com/api/v2/outbound/contactlists
```

响应:
```json
{
  "entities": [
    {
      "id": "list-123",
      "name": "Customer List",
      "dateCreated": "2024-01-01T00:00:00.000Z",
      "dateModified": "2024-01-15T00:00:00.000Z",
      "divisionId": "div-123",
      "importStatus": "IMPORT_COMPLETE",
      "columnOrder": ["phone", "name", "email"],
      "retryPolicy": {
        "maxAttempts": 3,
        "retryDelayMinutes": 60
      },
      "selfUri": "/api/v2/outbound/contactlists/list-123"
    }
  ]
}
```

#### 8.2.2 创建联系人列表

```http
POST https://api.{region}.genesys.com/api/v2/outbound/contactlists
Content-Type: application/json

{
  "name": "New Contact List",
  "divisionId": "div-123",
  "columnOrder": ["phone", "name", "email"],
  "contactPhoneColumn": "phone",
  "contactNameColumn": "name"
}
```

#### 8.2.3 添加联系人

```http
POST https://api.{region}.genesys.com/api/v2/outbound/contactlists/{contactListId}/contacts
Content-Type: application/json

{
  "data": {
    "phone": "+1234567890",
    "name": "John Doe",
    "email": "john.doe@example.com"
  }
}
```

---

## 9. 录音 API

### 9.1 获取录音列表

```http
GET https://api.{region}.genesys.com/api/v2/recording/recordings
```

查询参数:
| 参数 | 类型 | 描述 |
|-----|------|------|
| pageSize | integer | 每页数量 |
| pageNumber | integer | 页码 |
| sortBy | string | 排序字段 |
| sortOrder | string | 排序方向 |
| conversationId | string | 会话 ID 过滤 |
| startTime | string | 开始时间过滤 |
| endTime | string | 结束时间过滤 |
| media | string | 媒体类型过滤 |
| pageId | string | 页面 ID |

响应:
```json
{
  "entities": [
    {
      "id": "rec-123",
      "name": "Recording_20240219_100000.mp3",
      "type": "AgentScreenRecording",
      "conversationId": "conv-123",
      "startTime": "2024-02-19T10:00:00.000Z",
      "endTime": "2024-02-19T10:30:00.000Z",
      "duration": 1800,
      "media": "audio",
      "contentUri": "/api/v2/recording/recordings/rec-123/content",
      "annotationsUri": "/api/v2/recording/recordings/rec-123/annotations",
      "downloadUri": "/api/v2/recording/recordings/rec-123/download",
      "deleteUri": "/api/v2/recording/recordings/rec-123",
      "selfUri": "/api/v2/recording/recordings/rec-123"
    }
  ],
  "pageSize": 25,
  "pageNumber": 1,
  "pageCount": 10,
  "total": 250
}
```

### 9.2 获取录音详情

```http
GET https://api.{region}.genesys.com/api/v2/recording/recordings/{recordingId}
```

### 9.3 下载录音

```http
GET https://api.{region}.genesys.com/api/v2/recording/recordings/{recordingId}/download
```

### 9.4 删除录音

```http
DELETE https://api.{region}.genesys.com/api/v2/recording/recordings/{recordingId}
```

---

## 10. 统计 API

### 10.1 用户统计

#### 10.1.1 获取用户实时统计

```http
GET https://api.{region}.genesys.com/api/v2/analytics/users/{userId}/observations
```

响应:
```json
{
  "results": [
    {
      "metric": {
        "metricId": "t_on_queue",
        "metricName": "Time On Queue",
        "metricCategory": "AGENT",
        "selfUri": "/api/v2/analytics/metrics/t_on_queue"
      },
      "data": [
        {
          "stats": {
            "count": null,
            "max": null,
            "min": null,
            "sum": 3600
          }
        }
      ]
    }
  ]
}
```

#### 10.1.2 获取用户历史统计

```http
POST https://api.{region}.genesys.com/api/v2/analytics/users/observations/query
Content-Type: application/json

{
  "interval": "2024-02-18T00:00:00.000Z/2024-02-19T00:00:00.000Z",
  "filter": {
    "type": "and",
    "clauses": [
      {
        "type": "dimension",
        "dimension": "userId",
        "operator": "matches",
        "value": "user-123"
      }
    ]
  },
  "metrics": [
    "t_on_queue",
    "t_talk",
    "n_handled"
  ]
}
```

### 10.2 队列统计

#### 10.2.1 获取队列等待时间统计

```http
GET https://api.{region}.genesys.com/api/v2/analytics/queues/{queueId}/observations
```

#### 10.2.2 获取队列服务等级

```http
GET https://api.{region}.genesys.com/api/v2/analytics/queues/{queueId}/observations
```

### 10.3 会话统计

#### 10.3.1 获取会话统计

```http
GET https://api.{region}.genesys.com/api/v2/analytics/conversations
```

查询参数:
| 参数 | 类型 | 描述 |
|-----|------|------|
| interval | string | 时间间隔 |
| orderBy | string | 排序字段 |
| intervalFormat | string | 间隔格式 |
| segment | string | 分段 |
| mediaType | string | 媒体类型 |

---

## 11. WebRTC API

### 11.1 获取用户会话

```http
GET https://api.{region}.genesys.com/api/v2/webrtc/users/{userId}/session
```

响应:
```json
{
  "id": "session-123",
  "selfUri": "/api/v2/webrtc/users/user-123/session/session-123",
  "state": "READY",
  "capabilities": [
    "audio",
    "video",
    "screen"
  ],
  "provider": "webrtc",
  "connectedTime": "2024-02-19T10:00:00.000Z"
}
```

### 11.2 发起 WebRTC 会话

```http
POST https://api.{region}.genesys.com/api/v2/webrtc/users/{userId}/session
Content-Type: application/json

{
  "participantId": "part-123",
  "callId": "call-123",
  "capabilities": ["audio", "video"]
}
```

---

## 12. 通知 API

### 12.1 订阅通知

```http
POST https://api.{region}.genesys.com/api/v2/notifications/channels
Content-Type: application/json

{
  "name": "My Channel",
  "expiresInMinutes": 60
}
```

响应:
```json
{
  "id": "channel-123",
  "name": "My Channel",
  "connectUri": "wss://streaming.{region}.genesys.com/v2/streaming/channel-123",
  "expiresInMinutes": 60,
  "expirationTime": "2024-02-19T11:00:00.000Z",
  "selfUri": "/api/v2/notifications/channels/channel-123"
}
```

### 12.2 添加主题订阅

```http
POST https://api.{region}.genesys.com/api/v2/notifications/channels/{channelId}/subscriptions
Content-Type: application/json

[
  {
    "id": "topic.v2.users.user-123.presence"
  },
  {
    "id": "topic.v2.users.user-123.conversations"
  },
  {
    "id": "topic.v2.routing.queues.queue-123.observations"
  }
]
```

### 12.3 获取频道信息

```http
GET https://api.{region}.genesys.com/api/v2/notifications/channels/{channelId}
```

### 12.4 删除频道

```http
DELETE https://api.{region}.genesys.com/api/v2/notifications/channels/{channelId}
```

### 12.5 可用主题

| 主题 ID | 描述 |
|---------|------|
| `topic.v2.users.{userId}.presence` | 用户在线状态变化 |
| `topic.v2.users.{userId}.conversations` | 用户会话变化 |
| `topic.v2.users.{userId}.routingstatus` | 用户路由状态变化 |
| `topic.v2.routing.queues.{queueId}.observations` | 队列统计观察 |
| `topic.v2.conversations.{conversationId}` | 会话事件 |
| `topic.v2.routing.queues` | 队列相关事件 |
| `topic.v2.presencedefinitions` | 在线状态定义 |

---

## 13. OAuth API

### 13.1 获取授权 URL

```http
GET https://login.{region}.genesys.com/oauth/authorize
```

查询参数:
| 参数 | 类型 | 必填 | 描述 |
|-----|------|------|------|
| client_id | string | 是 | 应用客户端 ID |
| redirect_uri | string | 是 | 重定向 URI |
| response_type | string | 是 | 响应类型 (code) |
| state | string | 否 | 状态值 |
| prompt | string | 否 | 提示类型 (login, consent) |

### 13.2 获取令牌

```http
POST https://login.{region}.genesys.com/oauth/token
```

#### 客户端凭据模式
```http
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
&client_id={client_id}
&client_secret={client_secret}
```

#### 授权码模式
```http
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code={authorization_code}
&client_id={client_id}
&client_secret={client_secret}
&redirect_uri={redirect_uri}
```

#### 密码模式
```http
Content-Type: application/x-www-form-urlencoded

grant_type=password
&client_id={client_id}
&client_secret={client_secret}
&username={username}
&password={password}
```

### 13.3 刷新令牌

```http
POST https://login.{region}.genesys.com/oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token
&refresh_token={refresh_token}
&client_id={client_id}
&client_secret={client_secret}
```

### 13.4 撤销令牌

```http
POST https://login.{region}.genesys.com/oauth/revoke
Content-Type: application/x-www-form-urlencoded

token={access_token_or_refresh_token}
&client_id={client_id}
&client_secret={client_secret}
```

---

## 14. 组织 API

### 14.1 获取组织信息

```http
GET https://api.{region}.genesys.com/api/v2/organizations/me
```

### 14.2 获取组织用户列表

```http
GET https://api.{region}.genesys.com/api/v2/organizations/me/users
```

### 14.3 获取部门信息

```http
GET https://api.{region}.genesys.com/api/v2/organizations/departments
```

响应:
```json
{
  "entities": [
    {
      "id": "dept-123",
      "name": "Customer Support",
      "description": "Customer Support Department",
      "memberCount": 50,
      "division": {
        "id": "div-123",
        "name": "Support Division"
      },
      "selfUri": "/api/v2/organizations/departments/dept-123"
    }
  ]
}
```

### 14.4 获取部门详情

```http
GET https://api.{region}.genesys.com/api/v2/organizations/departments/{departmentId}
```

### 14.5 创建部门

```http
POST https://api.{region}.genesys.com/api/v2/organizations/departments
Content-Type: application/json

{
  "name": "New Department",
  "description": "New department description",
  "divisionId": "div-123"
}
```

### 14.6 获取子部门

```http
GET https://api.{region}.genesys.com/api/v2/organizations/departments/{departmentId}/subdepartments
```

---

## 15. 错误处理

### 15.1 错误响应格式

```json
{
  "status": 400,
  "code": "INVALID_PARAMETER",
  "message": "The provided parameter is invalid.",
  "messageWithParams": "The provided parameter 'queueId' is invalid.",
  "messageParams": {
    "queueId": "queue-123"
  },
  "contextId": "context-123",
  "details": [
    {
      "field": "queueId",
      "errorCode": "NOT_FOUND",
      "message": "Queue not found"
    }
  ],
  "errors": []
}
```

### 15.2 常见错误代码

| 错误代码 | HTTP 状态 | 描述 |
|---------|----------|------|
| `INVALID_PARAMETER` | 400 | 无效的参数 |
| `MISSING_PARAMETER` | 400 | 缺少必需参数 |
| `UNAUTHORIZED` | 401 | 未授权 |
| `FORBIDDEN` | 403 | 权限不足 |
| `NOT_FOUND` | 404 | 资源不存在 |
| `METHOD_NOT_ALLOWED` | 405 | 方法不允许 |
| `CONFLICT` | 409 | 资源冲突 |
| `PRECONDITION_FAILED` | 412 | 前置条件失败 |
| `RATE_LIMIT_EXCEEDED` | 429 | 超出速率限制 |
| `INTERNAL_SERVER_ERROR` | 500 | 服务器内部错误 |
| `SERVICE_UNAVAILABLE` | 503 | 服务不可用 |

### 15.3 错误处理示例

```javascript
class GenesysAPIError extends Error {
  constructor(response, body) {
    super(body.message || 'Genesys API Error');
    this.name = 'GenesysAPIError';
    this.status = response.status;
    this.code = body.code;
    this.details = body.details;
  }
}

async function handleRequest() {
  try {
    const response = await fetch(url, options);

    if (!response.ok) {
      const body = await response.json();
      throw new GenesysAPIError(response, body);
    }

    return await response.json();
  } catch (error) {
    if (error instanceof GenesysAPIError) {
      console.error(`API Error: ${error.code} - ${error.message}`);
      // 处理特定错误
      if (error.code === 'RATE_LIMIT_EXCEEDED') {
        // 实现退避重试逻辑
      }
    } else {
      console.error('Network Error:', error.message);
    }
    throw error;
  }
}
```

---

## 附录

### A. 速率限制

| 层级 | 请求/分钟 |
|-----|----------|
| 基础 | 100 |
| 标准 | 400 |
| 高级 | 800 |
| 企业 | 1600 |

响应头:
```
X-RateLimit-Limit: 400
X-RateLimit-Remaining: 395
X-RateLimit-Reset: 1645276800
```

### B. WebSocket 连接示例

```javascript
const ws = new WebSocket(connectUri);

ws.onopen = () => {
  console.log('WebSocket connected');

  // 发送心跳
  setInterval(() => {
    ws.send(JSON.stringify({ type: 'ping' }));
  }, 30000);
};

ws.onmessage = (event) => {
  const message = JSON.parse(event.data);

  if (message.topicName) {
    console.log(`Received: ${message.topicName}`, message.eventBody);
  }
};

ws.onerror = (error) => {
  console.error('WebSocket error:', error);
};

ws.onclose = () => {
  console.log('WebSocket closed');
};
```

### C. 完整 SDK 示例

```javascript
const axios = require('axios');
const WebSocket = require('ws');

class GenesysClient {
  constructor(config) {
    this.config = config;
    this.auth = new GenesysAuth(config);
    this.api = new GenesysAPI(config, this.auth);
    this.notifications = new GenesysNotifications(config, this.auth);
  }
}

class GenesysAPI {
  constructor(config, auth) {
    this.baseUrl = `https://api.${config.environment}.genesys.com`;
    this.auth = auth;
  }

  async request(method, path, data = null) {
    const headers = await this.auth.getHeaders();
    const url = `${this.baseUrl}${path}`;

    const response = await axios({
      method,
      url,
      headers,
      data
    });

    return response.data;
  }

  // 用户 API
  async getUser(userId) {
    return this.request('GET', `/api/v2/users/${userId}`);
  }

  async updateUser(userId, data) {
    return this.request('PATCH', `/api/v2/users/${userId}`, data);
  }

  async getUsers(params = {}) {
    const queryString = new URLSearchParams(params).toString();
    return this.request('GET', `/api/v2/users?${queryString}`);
  }

  // 队列 API
  async getQueue(queueId) {
    return this.request('GET', `/api/v2/routing/queues/${queueId}`);
  }

  async getQueues(params = {}) {
    const queryString = new URLSearchParams(params).toString();
    return this.request('GET', `/api/v2/routing/queues?${queryString}`);
  }

  // 会话 API
  async getConversation(conversationId) {
    return this.request('GET', `/api/v2/conversations/${conversationId}`);
  }

  async createCall(data) {
    return this.request('POST', '/api/v2/conversations/calls', data);
  }

  async transfer(conversationId, participantId, data) {
    return this.request(
      'POST',
      `/api/v2/conversations/${conversationId}/participants/${participantId}/transfers`,
      data
    );
  }
}

class GenesysNotifications {
  constructor(config, auth) {
    this.config = config;
    this.auth = auth;
    this.channels = new Map();
  }

  async createChannel(options = {}) {
    const headers = await this.auth.getHeaders();
    const response = await axios.post(
      `https://api.${this.config.environment}.genesys.com/api/v2/notifications/channels`,
      {
        name: options.name || 'Default Channel',
        expiresInMinutes: options.expiresInMinutes || 60
      },
      { headers }
    );

    const channel = response.data;
    this.channels.set(channel.id, channel);
    return channel;
  }

  async subscribe(channelId, topics) {
    const headers = await this.auth.getHeaders();
    const response = await axios.post(
      `https://api.${this.config.environment}.genesys.com/api/v2/notifications/channels/${channelId}/subscriptions`,
      topics,
      { headers }
    );

    return response.data;
  }

  connect(channelId, onMessage) {
    const channel = this.channels.get(channelId);
    if (!channel) {
      throw new Error(`Channel ${channelId} not found`);
    }

    const ws = new WebSocket(channel.connectUri);

    ws.on('open', () => {
      console.log(`Connected to channel ${channelId}`);
    });

    ws.on('message', (data) => {
      const message = JSON.parse(data);
      if (onMessage) {
        onMessage(message);
      }
    });

    ws.on('error', (error) => {
      console.error('WebSocket error:', error);
    });

    return ws;
  }
}

// 使用示例
async function main() {
  const client = new GenesysClient({
    clientId: 'your-client-id',
    clientSecret: 'your-client-secret',
    environment: 'usw2'
  });

  // 获取用户信息
  const user = await client.api.getUser('user-123');
  console.log('User:', user);

  // 创建通知频道
  const channel = await client.notifications.createChannel();
  console.log('Channel:', channel);

  // 订阅主题
  await client.notifications.subscribe(channel.id, [
    { id: 'topic.v2.users.user-123.presence' }
  ]);

  // 连接 WebSocket
  client.notifications.connect(channel.id, (message) => {
    console.log('Received:', message);
  });
}

main().catch(console.error);
```

---

## 参考资源

- [Genesys Cloud Developer Center](https://developer.genesys.com)
- [Genesys Cloud API Reference](https://developer.genesys.com/devapps/api-explorer/)
- [OAuth 2.0 规范](https://oauth.net/2/)
- [WebSocket 协议](https://websockets.spec.whatwg.org/)
