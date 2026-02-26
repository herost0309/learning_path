# Genesys Cloud Routing API 完全指南

## 目录
- [概述](#概述)
- [API 端点概览](#api-端点概览)
- [认证](#认证)
- [核心功能模块](#核心功能模块)
- [最佳实践](#最佳实践)
- [Java 代码示例](#java-代码示例)
- [注意事项](#注意事项)

---

## 概述

Genesys Cloud Routing API 是一组 RESTful API，用于管理联系中心的路由功能，包括队列管理、技能路由、直接路由等。它是 Genesys Cloud 平台的核心组件之一。

**API 基础 URL**: `https://api.{region}.genesys.cloud/api/v2/routing`

**支持的区域**:
- 北美: `api.mypurecloud.com`
- 欧洲: `api.mypurecloud.ie`
- 亚太: `api.mypurecloud.com.au`
- 日本: `api.mypurecloud.jp`
- 德国: `api.mypurecloud.de`
- 加拿大: `api.cac1.pure.cloud`

---

## API 端点概览

### 1. 队列管理 (Queues)

| 端点 | 方法 | 功能描述 |
|------|------|----------|
| `/routing/queues` | GET | 获取所有队列列表 |
| `/routing/queues` | POST | 创建新队列 |
| `/routing/queues/{queueId}` | GET | 获取指定队列详情 |
| `/routing/queues/{queueId}` | PUT | 更新队列配置 |
| `/routing/queues/{queueId}` | DELETE | 删除队列 |
| `/routing/queues/{queueId}/members` | GET | 获取队列成员 |
| `/routing/queues/{queueId}/users` | POST | 添加用户到队列 |
| `/routing/queues/{queueId}/users/{userId}` | DELETE | 从队列移除用户 |
| `/routing/queues/{queueId}/wrapupcodes` | GET | 获取队列的结束语代码 |
| `/routing/queues/{queueId}/wrapupcodes` | POST | 为队列添加结束语代码 |

### 2. 技能管理 (Skills)

| 端点 | 方法 | 功能描述 |
|------|------|----------|
| `/routing/skills` | GET | 获取所有技能列表 |
| `/routing/skills` | POST | 创建新技能 |
| `/routing/skills/{skillId}` | GET | 获取指定技能详情 |
| `/routing/skills/{skillId}` | PUT | 更新技能 |
| `/routing/skills/{skillId}` | DELETE | 删除技能 |
| `/routing/skills/bulk` | POST | 批量创建技能 |

### 3. 结束语代码 (Wrap-up Codes)

| 端点 | 方法 | 功能描述 |
|------|------|----------|
| `/routing/wrapupcodes` | GET | 获取所有结束语代码 |
| `/routing/wrapupcodes` | POST | 创建结束语代码 |
| `/routing/wrapupcodes/{codeId}` | GET | 获取指定结束语代码 |
| `/routing/wrapupcodes/{codeId}` | PUT | 更新结束语代码 |
| `/routing/wrapupcodes/{codeId}` | DELETE | 删除结束语代码 |

### 4. 路由策略 (Routing Strategies)

| 端点 | 方法 | 功能描述 |
|------|------|----------|
| `/routing/strategies` | GET | 获取路由策略列表 |
| `/routing/strategies` | POST | 创建路由策略 |
| `/routing/strategies/{strategyId}` | GET | 获取指定策略 |
| `/routing/strategies/{strategyId}` | PUT | 更新路由策略 |
| `/routing/strategies/{strategyId}` | DELETE | 删除路由策略 |

### 5. 语言管理 (Languages)

| 端点 | 方法 | 功能描述 |
|------|------|----------|
| `/routing/languages` | GET | 获取所有语言列表 |
| `/routing/languages` | POST | 创建新语言 |
| `/routing/languages/{languageId}` | GET | 获取指定语言 |
| `/routing/languages/{languageId}` | PUT | 更新语言 |
| `/routing/languages/{languageId}` | DELETE | 删除语言 |

### 6. 直接路由 (Direct Routing)

| 端点 | 方法 | 功能描述 |
|------|------|----------|
| `/routing/directrouting` | GET | 获取直接路由配置 |
| `/routing/directrouting` | PUT | 更新直接路由设置 |

### 7. 队列组 (Queue Groups)

| 端点 | 方法 | 功能描述 |
|------|------|----------|
| `/routing/queues/groups` | GET | 获取队列组列表 |
| `/routing/queues/groups` | POST | 创建队列组 |
| `/routing/queues/groups/{groupId}` | GET | 获取指定队列组 |
| `/routing/queues/groups/{groupId}` | PUT | 更新队列组 |
| `/routing/queues/groups/{groupId}` | DELETE | 删除队列组 |

### 8. 短信地址 (SMS Addresses)

| 端点 | 方法 | 功能描述 |
|------|------|----------|
| `/routing/sms/addresses` | GET | 获取短信地址列表 |
| `/routing/sms/addresses` | POST | 创建短信地址 |
| `/routing/sms/addresses/{addressId}` | GET | 获取指定短信地址 |
| `/routing/sms/addresses/{addressId}` | DELETE | 删除短信地址 |

### 9. 邮件路由 (Email Routing)

| 端点 | 方法 | 功能描述 |
|------|------|----------|
| `/routing/email/routes` | GET | 获取邮件路由列表 |
| `/routing/email/routes` | POST | 创建邮件路由 |
| `/routing/email/routes/{routeId}` | GET | 获取指定邮件路由 |
| `/routing/email/routes/{routeId}` | PUT | 更新邮件路由 |
| `/routing/email/routes/{routeId}` | DELETE | 删除邮件路由 |

### 10. 利用率管理 (Utilization)

| 端点 | 方法 | 功能描述 |
|------|------|----------|
| `/routing/utilization` | GET | 获取利用率设置 |
| `/routing/utilization` | PUT | 更新利用率设置 |
| `/routing/utilization/labels` | GET | 获取利用率标签 |

---

## 认证

Genesys Cloud API 使用 OAuth 2.0 进行认证。

### 获取访问令牌

```http
POST https://login.{region}.genesys.cloud/oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&client_id={client_id}&client_secret={client_secret}
```

### 使用令牌访问 API

```http
GET https://api.{region}.genesys.cloud/api/v2/routing/queues
Authorization: Bearer {access_token}
```

---

## 核心功能模块

### 1. 队列 (Queues)

队列是 Genesys Cloud 路由的核心概念，用于管理和分发交互。

**队列属性**:
- `name`: 队列名称
- `description`: 队列描述
- `divisionId`: 所属部门 ID
- `acwWrapupPrompt`: 结束语提示设置
- `teamVisibility`: 团队可见性
- `callingPartyName`: 主叫方名称
- `callingPartyNumber`: 主叫方号码
- `mediaSettings`: 媒体设置（呼叫、聊天、邮件、消息等）
- `bullseye`: 靶心路由设置
- `scoredAgent`: 评分代理路由设置

### 2. 技能 (Skills)

技能用于将交互路由到具有特定能力的代理。

**技能属性**:
- `name`: 技能名称
- `description`: 技能描述
- `divisionId`: 所属部门 ID
- `state`: 技能状态 (active/inactive)

### 3. 结束语代码 (Wrap-up Codes)

结束语代码用于分类交互结束的原因。

**结束语代码属性**:
- `name`: 代码名称
- `divisionId`: 所属部门 ID

**系统预留结束语代码**:
- `ININ-OUTBOUND-AMBIGUOUS`
- `ININ-OUTBOUND-CAMPAIGN-FORCED-OFF`
- `ININ-OUTBOUND-DISCONNECT`
- `ININ-OUTBOUND-FAILED-TO-REACH-AGENT`
- `ININ-OUTBOUND-MACHINE`
- `ININ-OUTBOUND-PREVIEW-SKIPPED`
- `ININ-WRAP-UP-SKIPPED`
- `ININ-WRAP-UP-TIMEOUT`

---

## 最佳实践

### 1. 队列管理最佳实践

**命名规范**:
```
推荐格式: {部门}_{功能}_{优先级}
示例: Sales_Inbound_High, Support_Email_Normal
```

**队列配置建议**:
- 为每个队列设置合理的溢出目标
- 配置适当的等待音乐和公告
- 设置服务水平目标（SLA）
- 使用技能要求确保路由到合适的代理

**性能优化**:
- 避免创建过多小队列，考虑合并相似功能
- 使用队列组进行层次化管理
- 定期审查队列成员和技能配置

### 2. 技能路由最佳实践

**技能设计原则**:
- 技能应具体且可衡量
- 避免创建过多技能，保持简洁
- 使用技能组进行分类管理

**技能评估**:
- 定期评估代理技能等级
- 使用技能熟练度（proficiency）进行精确匹配
- 考虑使用技能放松（skill relaxation）处理长时间等待

### 3. 错误处理最佳实践

**重试策略**:
```java
// 指数退避重试
int maxRetries = 3;
long initialDelay = 1000; // 1秒

for (int i = 0; i < maxRetries; i++) {
    try {
        // API 调用
        break;
    } catch (ApiException e) {
        if (e.getCode() == 429) { // 速率限制
            long delay = initialDelay * (long) Math.pow(2, i);
            Thread.sleep(delay);
        } else {
            throw e;
        }
    }
}
```

### 4. API 调用最佳实践

**批量操作**:
- 使用批量 API 端点减少调用次数
- 批量大小建议：50-100 条记录

**分页处理**:
```java
// 分页获取队列
int pageSize = 100;
int pageNumber = 1;
boolean hasMore = true;

while (hasMore) {
    QueueEntityListing result = api.getRoutingQueues(pageSize, pageNumber);
    // 处理结果
    hasMore = result.getEntities().size() == pageSize;
    pageNumber++;
}
```

**缓存策略**:
- 缓存不常变化的数据（如技能列表、语言列表）
- 队列配置变更后及时更新缓存

### 5. 安全最佳实践

**权限管理**:
- 遵循最小权限原则
- 使用 `Routing > Queue > View/Search/Add/Edit/Delete` 权限
- 使用 `Routing > Skill > View/Add/Edit/Delete` 权限
- 使用 `Routing > Wrap-up Code > View/Add/Edit/Delete` 权限

**敏感数据保护**:
- 不要在日志中记录访问令牌
- 使用环境变量存储客户端凭据
- 定期轮换 API 密钥

---

## Java 代码示例

### 环境配置

**Maven 依赖**:
```xml
<dependency>
    <groupId>com.mypurecloud</groupId>
    <artifactId>purecloud-platform-sdk-v2</artifactId>
    <version>161.0.0</version>
</dependency>
```

**Gradle 依赖**:
```gradle
implementation 'com.mypurecloud:purecloud-platform-sdk-v2:161.0.0'
```

### 基础配置类

```java
package com.genesys.routing.config;

import com.mypurecloud.sdk.v2.ApiClient;
import com.mypurecloud.sdk.v2.ApiClient.Builder;
import com.mypurecloud.sdk.v2.api.RoutingApi;
import com.mypurecloud.sdk.v2.api.UsersApi;

/**
 * Genesys Cloud API 配置类
 */
public class GenesysConfig {

    private static final String REGION = "mypurecloud.com"; // 根据实际区域修改
    private static ApiClient apiClient;
    private static RoutingApi routingApi;
    private static UsersApi usersApi;

    /**
     * 初始化 API 客户端
     * @param clientId 客户端 ID
     * @param clientSecret 客户端密钥
     */
    public static void initialize(String clientId, String clientSecret) {
        // 创建 API 客户端
        apiClient = Builder.standard()
                .withBasePath("https://api." + REGION)
                .withClientCredentials(clientId, clientSecret)
                .build();

        // 初始化 API 实例
        routingApi = new RoutingApi();
        usersApi = new UsersApi();
    }

    public static ApiClient getApiClient() {
        return apiClient;
    }

    public static RoutingApi getRoutingApi() {
        return routingApi;
    }

    public static UsersApi getUsersApi() {
        return usersApi;
    }
}
```

### 队列管理示例

```java
package com.genesys.routing.examples;

import com.mypurecloud.sdk.v2.api.RoutingApi;
import com.mypurecloud.sdk.v2.model.*;
import com.mypurecloud.sdk.v2.ApiException;

import java.util.*;

/**
 * 队列管理示例
 */
public class QueueManagementExample {

    private final RoutingApi routingApi;

    public QueueManagementExample() {
        this.routingApi = new RoutingApi();
    }

    /**
     * 创建队列
     * @param name 队列名称
     * @param divisionId 部门 ID
     * @return 创建的队列
     */
    public Queue createQueue(String name, String divisionId) {
        try {
            // 构建队列配置
            Queue queue = new Queue();
            queue.setName(name);
            queue.setDivisionId(divisionId);

            // 配置呼叫媒体设置
            MediaSettings callSettings = new MediaSettings();
            callSettings.setEnableAutoDialOnAgentReady(true);
            callSettings.setServiceLevelPercentage(80.0);
            callSettings.setServiceLevelDurationMs(20000L); // 20秒

            // 配置聊天媒体设置
            MediaSettings chatSettings = new MediaSettings();
            chatSettings.setServiceLevelPercentage(90.0);
            chatSettings.setServiceLevelDurationMs(30000L); // 30秒

            // 设置媒体设置
            Map<String, MediaSettings> mediaSettings = new HashMap<>();
            mediaSettings.put("call", callSettings);
            mediaSettings.put("chat", chatSettings);
            queue.setMediaSettings(mediaSettings);

            // 设置结束语提示
            queue.setAcwWrapupPrompt("MANDATORY_TIMEOUT");

            // 创建队列
            Queue createdQueue = routingApi.postRoutingQueues(queue);
            System.out.println("队列创建成功: " + createdQueue.getId());
            return createdQueue;

        } catch (ApiException e) {
            System.err.println("创建队列失败: " + e.getMessage());
            throw new RuntimeException("创建队列失败", e);
        }
    }

    /**
     * 获取所有队列
     * @param pageSize 每页数量
     * @return 队列列表
     */
    public List<Queue> getAllQueues(int pageSize) {
        List<Queue> allQueues = new ArrayList<>();
        int pageNumber = 1;
        boolean hasMore = true;

        try {
            while (hasMore) {
                QueueEntityListing result = routingApi.getRoutingQueues(
                    pageSize,      // pageSize
                    pageNumber,    // pageNumber
                    null,          // sortBy
                    null,          // sortOrder
                    null           // name
                );

                if (result.getEntities() != null) {
                    allQueues.addAll(result.getEntities());
                    hasMore = result.getEntities().size() == pageSize;
                    pageNumber++;
                } else {
                    hasMore = false;
                }
            }

            System.out.println("共获取 " + allQueues.size() + " 个队列");
            return allQueues;

        } catch (ApiException e) {
            System.err.println("获取队列列表失败: " + e.getMessage());
            throw new RuntimeException("获取队列列表失败", e);
        }
    }

    /**
     * 获取队列详情
     * @param queueId 队列 ID
     * @return 队列详情
     */
    public Queue getQueueById(String queueId) {
        try {
            return routingApi.getRoutingQueue(queueId);
        } catch (ApiException e) {
            System.err.println("获取队列详情失败: " + e.getMessage());
            throw new RuntimeException("获取队列详情失败", e);
        }
    }

    /**
     * 更新队列配置
     * @param queueId 队列 ID
     * @param updatedQueue 更新的队列配置
     * @return 更新后的队列
     */
    public Queue updateQueue(String queueId, Queue updatedQueue) {
        try {
            return routingApi.putRoutingQueue(queueId, updatedQueue);
        } catch (ApiException e) {
            System.err.println("更新队列失败: " + e.getMessage());
            throw new RuntimeException("更新队列失败", e);
        }
    }

    /**
     * 删除队列
     * @param queueId 队列 ID
     */
    public void deleteQueue(String queueId) {
        try {
            routingApi.deleteRoutingQueue(queueId);
            System.out.println("队列删除成功: " + queueId);
        } catch (ApiException e) {
            System.err.println("删除队列失败: " + e.getMessage());
            throw new RuntimeException("删除队列失败", e);
        }
    }

    /**
     * 添加用户到队列
     * @param queueId 队列 ID
     * @param userId 用户 ID
     * @param priority 优先级 (1-5)
     */
    public void addUserToQueue(String queueId, String userId, int priority) {
        try {
            QueueMember member = new QueueMember();
            member.setUserId(userId);
            member.setPriority(priority);
            member.setRingDurationMs(30000L); // 振铃时长30秒

            List<QueueMember> members = Collections.singletonList(member);
            routingApi.postRoutingQueueUsers(queueId, members);

            System.out.println("用户 " + userId + " 已添加到队列 " + queueId);

        } catch (ApiException e) {
            System.err.println("添加用户到队列失败: " + e.getMessage());
            throw new RuntimeException("添加用户到队列失败", e);
        }
    }

    /**
     * 从队列移除用户
     * @param queueId 队列 ID
     * @param userId 用户 ID
     */
    public void removeUserFromQueue(String queueId, String userId) {
        try {
            routingApi.deleteRoutingQueueUser(queueId, userId);
            System.out.println("用户 " + userId + " 已从队列 " + queueId + " 移除");
        } catch (ApiException e) {
            System.err.println("从队列移除用户失败: " + e.getMessage());
            throw new RuntimeException("从队列移除用户失败", e);
        }
    }

    /**
     * 获取队列成员
     * @param queueId 队列 ID
     * @return 队列成员列表
     */
    public List<QueueMember> getQueueMembers(String queueId) {
        List<QueueMember> allMembers = new ArrayList<>();
        int pageNumber = 1;
        int pageSize = 100;
        boolean hasMore = true;

        try {
            while (hasMore) {
                QueueMemberEntityListing result = routingApi.getRoutingQueueMembers(
                    queueId,
                    pageSize,
                    pageNumber,
                    null,  // joined
                    null,  // name
                    null,  // profileSkills
                    null,  // sortBy
                    null   // sortOrder
                );

                if (result.getEntities() != null) {
                    allMembers.addAll(result.getEntities());
                    hasMore = result.getEntities().size() == pageSize;
                    pageNumber++;
                } else {
                    hasMore = false;
                }
            }

            return allMembers;

        } catch (ApiException e) {
            System.err.println("获取队列成员失败: " + e.getMessage());
            throw new RuntimeException("获取队列成员失败", e);
        }
    }
}
```

### 技能管理示例

```java
package com.genesys.routing.examples;

import com.mypurecloud.sdk.v2.api.RoutingApi;
import com.mypurecloud.sdk.v2.api.UsersApi;
import com.mypurecloud.sdk.v2.model.*;
import com.mypurecloud.sdk.v2.ApiException;

import java.util.*;

/**
 * 技能管理示例
 */
public class SkillManagementExample {

    private final RoutingApi routingApi;
    private final UsersApi usersApi;

    public SkillManagementExample() {
        this.routingApi = new RoutingApi();
        this.usersApi = new UsersApi();
    }

    /**
     * 创建技能
     * @param name 技能名称
     * @param description 技能描述
     * @param divisionId 部门 ID
     * @return 创建的技能
     */
    public RoutingSkill createSkill(String name, String description, String divisionId) {
        try {
            RoutingSkill skill = new RoutingSkill();
            skill.setName(name);
            skill.setDescription(description);
            skill.setDivisionId(divisionId);
            skill.setState("active");

            RoutingSkill createdSkill = routingApi.postRoutingSkills(skill);
            System.out.println("技能创建成功: " + createdSkill.getId());
            return createdSkill;

        } catch (ApiException e) {
            System.err.println("创建技能失败: " + e.getMessage());
            throw new RuntimeException("创建技能失败", e);
        }
    }

    /**
     * 获取所有技能
     * @return 技能列表
     */
    public List<RoutingSkill> getAllSkills() {
        List<RoutingSkill> allSkills = new ArrayList<>();
        int pageNumber = 1;
        int pageSize = 100;
        boolean hasMore = true;

        try {
            while (hasMore) {
                SkillEntityListing result = routingApi.getRoutingSkills(
                    pageSize,
                    pageNumber,
                    null,  // name
                    null   // state
                );

                if (result.getEntities() != null) {
                    allSkills.addAll(result.getEntities());
                    hasMore = result.getEntities().size() == pageSize;
                    pageNumber++;
                } else {
                    hasMore = false;
                }
            }

            System.out.println("共获取 " + allSkills.size() + " 个技能");
            return allSkills;

        } catch (ApiException e) {
            System.err.println("获取技能列表失败: " + e.getMessage());
            throw new RuntimeException("获取技能列表失败", e);
        }
    }

    /**
     * 为用户分配技能
     * @param userId 用户 ID
     * @param skillId 技能 ID
     * @param proficiency 熟练度 (1.0-5.0)
     */
    public void assignSkillToUser(String userId, String skillId, Double proficiency) {
        try {
            // 获取当前用户的技能
            User user = usersApi.getUser(userId, null);
            List<UserRoutingSkill> currentSkills = user.getRoutingSkills();
            if (currentSkills == null) {
                currentSkills = new ArrayList<>();
            }

            // 检查是否已存在该技能
            boolean skillExists = currentSkills.stream()
                .anyMatch(s -> s.getId().equals(skillId));

            if (skillExists) {
                System.out.println("用户已拥有该技能");
                return;
            }

            // 添加新技能
            UserRoutingSkill userSkill = new UserRoutingSkill();
            userSkill.setId(skillId);
            userSkill.setProficiency(proficiency);
            currentSkills.add(userSkill);

            // 更新用户技能
            User updateUser = new User();
            updateUser.setRoutingSkills(currentSkills);
            usersApi.patchUser(userId, updateUser);

            System.out.println("技能 " + skillId + " 已分配给用户 " + userId);

        } catch (ApiException e) {
            System.err.println("分配技能失败: " + e.getMessage());
            throw new RuntimeException("分配技能失败", e);
        }
    }

    /**
     * 更新用户技能熟练度
     * @param userId 用户 ID
     * @param skillId 技能 ID
     * @param newProficiency 新熟练度
     */
    public void updateUserSkillProficiency(String userId, String skillId, Double newProficiency) {
        try {
            // 获取用户当前技能
            User user = usersApi.getUser(userId, null);
            List<UserRoutingSkill> skills = user.getRoutingSkills();

            if (skills == null || skills.isEmpty()) {
                throw new RuntimeException("用户没有任何技能");
            }

            // 更新指定技能的熟练度
            for (UserRoutingSkill skill : skills) {
                if (skill.getId().equals(skillId)) {
                    skill.setProficiency(newProficiency);
                    break;
                }
            }

            // 保存更新
            User updateUser = new User();
            updateUser.setRoutingSkills(skills);
            usersApi.patchUser(userId, updateUser);

            System.out.println("技能熟练度已更新");

        } catch (ApiException e) {
            System.err.println("更新技能熟练度失败: " + e.getMessage());
            throw new RuntimeException("更新技能熟练度失败", e);
        }
    }

    /**
     * 批量创建技能
     * @param skillNames 技能名称列表
     * @param divisionId 部门 ID
     * @return 创建的技能列表
     */
    public List<RoutingSkill> bulkCreateSkills(List<String> skillNames, String divisionId) {
        try {
            List<RoutingSkill> skills = new ArrayList<>();

            for (String name : skillNames) {
                RoutingSkill skill = new RoutingSkill();
                skill.setName(name);
                skill.setDivisionId(divisionId);
                skill.setState("active");
                skills.add(skill);
            }

            BulkResponse response = routingApi.postRoutingSkillsBulk(skills);

            System.out.println("批量创建完成，成功: " + response.getResult().size());
            return skills;

        } catch (ApiException e) {
            System.err.println("批量创建技能失败: " + e.getMessage());
            throw new RuntimeException("批量创建技能失败", e);
        }
    }
}
```

### 结束语代码示例

```java
package com.genesys.routing.examples;

import com.mypurecloud.sdk.v2.api.RoutingApi;
import com.mypurecloud.sdk.v2.model.*;
import com.mypurecloud.sdk.v2.ApiException;

import java.util.*;

/**
 * 结束语代码管理示例
 */
public class WrapUpCodeExample {

    private final RoutingApi routingApi;

    public WrapUpCodeExample() {
        this.routingApi = new RoutingApi();
    }

    /**
     * 创建结束语代码
     * @param name 代码名称
     * @param divisionId 部门 ID
     * @return 创建的结束语代码
     */
    public WrapupCode createWrapUpCode(String name, String divisionId) {
        try {
            WrapupCode code = new WrapupCode();
            code.setName(name);
            code.setDivisionId(divisionId);

            WrapupCode createdCode = routingApi.postRoutingWrapupcodes(code);
            System.out.println("结束语代码创建成功: " + createdCode.getId());
            return createdCode;

        } catch (ApiException e) {
            System.err.println("创建结束语代码失败: " + e.getMessage());
            throw new RuntimeException("创建结束语代码失败", e);
        }
    }

    /**
     * 获取所有结束语代码
     * @return 结束语代码列表
     */
    public List<WrapupCode> getAllWrapUpCodes() {
        List<WrapupCode> allCodes = new ArrayList<>();
        int pageNumber = 1;
        int pageSize = 100;
        boolean hasMore = true;

        try {
            while (hasMore) {
                WrapupCodeListing result = routingApi.getRoutingWrapupcodes(
                    pageSize,
                    pageNumber,
                    null,  // name
                    null   // divisionId
                );

                if (result.getEntities() != null) {
                    allCodes.addAll(result.getEntities());
                    hasMore = result.getEntities().size() == pageSize;
                    pageNumber++;
                } else {
                    hasMore = false;
                }
            }

            return allCodes;

        } catch (ApiException e) {
            System.err.println("获取结束语代码失败: " + e.getMessage());
            throw new RuntimeException("获取结束语代码失败", e);
        }
    }

    /**
     * 为队列添加结束语代码
     * @param queueId 队列 ID
     * @param wrapUpCodeIds 结束语代码 ID 列表
     */
    public void addWrapUpCodesToQueue(String queueId, List<String> wrapUpCodeIds) {
        try {
            List<WrapUpCodeReference> codes = new ArrayList<>();

            for (String codeId : wrapUpCodeIds) {
                WrapUpCodeReference ref = new WrapUpCodeReference();
                ref.setId(codeId);
                codes.add(ref);
            }

            routingApi.postRoutingQueueWrapupcodes(queueId, codes);
            System.out.println("已为队列 " + queueId + " 添加 " + codes.size() + " 个结束语代码");

        } catch (ApiException e) {
            System.err.println("为队列添加结束语代码失败: " + e.getMessage());
            throw new RuntimeException("为队列添加结束语代码失败", e);
        }
    }

    /**
     * 获取队列的结束语代码
     * @param queueId 队列 ID
     * @return 结束语代码列表
     */
    public List<WrapupCode> getQueueWrapUpCodes(String queueId) {
        try {
            WrapupCodeListing result = routingApi.getRoutingQueueWrapupcodes(queueId);
            return result.getEntities();

        } catch (ApiException e) {
            System.err.println("获取队列结束语代码失败: " + e.getMessage());
            throw new RuntimeException("获取队列结束语代码失败", e);
        }
    }
}
```

### 完整使用示例

```java
package com.genesys.routing.examples;

import com.genesys.routing.config.GenesysConfig;
import com.mypurecloud.sdk.v2.model.*;

/**
 * 完整使用示例
 */
public class CompleteExample {

    public static void main(String[] args) {
        // 配置认证信息
        String clientId = System.getenv("GENESYS_CLIENT_ID");
        String clientSecret = System.getenv("GENESYS_CLIENT_SECRET");

        // 初始化 SDK
        GenesysConfig.initialize(clientId, clientSecret);

        // 创建示例
        QueueManagementExample queueExample = new QueueManagementExample();
        SkillManagementExample skillExample = new SkillManagementExample();
        WrapUpCodeExample wrapUpExample = new WrapUpCodeExample();

        try {
            // 1. 创建技能
            RoutingSkill salesSkill = skillExample.createSkill(
                "Sales_Skill",
                "销售技能",
                "your-division-id"
            );

            // 2. 创建结束语代码
            WrapupCode resolvedCode = wrapUpExample.createWrapUpCode(
                "Issue_Resolved",
                "your-division-id"
            );

            // 3. 创建队列
            Queue queue = queueExample.createQueue(
                "Sales_Inbound_Queue",
                "your-division-id"
            );

            // 4. 为队列添加结束语代码
            wrapUpExample.addWrapUpCodesToQueue(
                queue.getId(),
                Collections.singletonList(resolvedCode.getId())
            );

            // 5. 添加用户到队列
            queueExample.addUserToQueue(queue.getId(), "user-id-here", 3);

            // 6. 为用户分配技能
            skillExample.assignSkillToUser("user-id-here", salesSkill.getId(), 4.5);

            System.out.println("配置完成！");

        } catch (Exception e) {
            System.err.println("执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

### 异步操作示例

```java
package com.genesys.routing.examples;

import com.mypurecloud.sdk.v2.api.RoutingApi;
import com.mypurecloud.sdk.v2.model.*;
import com.mypurecloud.sdk.v2.ApiException;

import java.util.concurrent.*;
import java.util.*;

/**
 * 异步操作示例
 */
public class AsyncExample {

    private final RoutingApi routingApi;
    private final ExecutorService executorService;

    public AsyncExample() {
        this.routingApi = new RoutingApi();
        this.executorService = Executors.newFixedThreadPool(10);
    }

    /**
     * 异步获取多个队列的详情
     * @param queueIds 队列 ID 列表
     * @return CompletableFuture 列表
     */
    public List<CompletableFuture<Queue>> getQueuesAsync(List<String> queueIds) {
        List<CompletableFuture<Queue>> futures = new ArrayList<>();

        for (String queueId : queueIds) {
            CompletableFuture<Queue> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return routingApi.getRoutingQueue(queueId);
                } catch (ApiException e) {
                    throw new CompletionException(e);
                }
            }, executorService);

            futures.add(future);
        }

        return futures;
    }

    /**
     * 批量异步更新队列成员
     * @param queueId 队列 ID
     * @param userIds 用户 ID 列表
     * @param priority 优先级
     */
    public CompletableFuture<Void> bulkUpdateQueueMembersAsync(
            String queueId,
            List<String> userIds,
            int priority) {

        return CompletableFuture.runAsync(() -> {
            try {
                List<QueueMember> members = new ArrayList<>();

                for (String userId : userIds) {
                    QueueMember member = new QueueMember();
                    member.setUserId(userId);
                    member.setPriority(priority);
                    members.add(member);
                }

                routingApi.postRoutingQueueUsers(queueId, members);

            } catch (ApiException e) {
                throw new CompletionException(e);
            }
        }, executorService);
    }

    /**
     * 使用回调处理异步结果
     */
    public void getQueueWithCallback(String queueId,
            Consumer<Queue> onSuccess,
            Consumer<Throwable> onError) {

        CompletableFuture.supplyAsync(() -> {
            try {
                return routingApi.getRoutingQueue(queueId);
            } catch (ApiException e) {
                throw new CompletionException(e);
            }
        }, executorService)
        .thenAccept(onSuccess)
        .exceptionally(throwable -> {
            onError.accept(throwable);
            return null;
        });
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

---

## 注意事项

### 1. API 限制

**速率限制**:
- Genesys Cloud API 有严格的速率限制
- 超出限制会返回 HTTP 429 错误
- 建议实现指数退避重试机制

**常见速率限制**:
| API 类别 | 限制 |
|---------|------|
| 读取操作 | 300 请求/分钟 |
| 写入操作 | 150 请求/分钟 |
| 批量操作 | 30 请求/分钟 |

### 2. 数据一致性

**注意事项**:
- API 调用是最终一致的
- 创建/更新后可能需要几秒钟才能在查询中看到
- 建议在关键操作后添加适当的延迟

### 3. 错误处理

**常见错误代码**:
| HTTP 状态码 | 描述 | 处理建议 |
|------------|------|----------|
| 400 | 请求参数错误 | 检查请求体格式 |
| 401 | 认证失败 | 刷新访问令牌 |
| 403 | 权限不足 | 检查用户权限 |
| 404 | 资源不存在 | 验证资源 ID |
| 409 | 资源冲突 | 检查资源是否已存在 |
| 429 | 速率限制 | 实现退避重试 |
| 500 | 服务器错误 | 重试或联系支持 |

### 4. 性能优化

**批量操作建议**:
- 使用批量 API 减少网络开销
- 单次批量操作建议不超过 100 条记录
- 对于大量数据，分批次处理

**分页查询**:
- 使用合适的页面大小（50-100）
- 避免在高峰期执行大量分页查询
- 考虑使用增量同步

### 5. 安全注意事项

**认证安全**:
- 定期轮换 OAuth 客户端凭据
- 使用短期访问令牌
- 不要在代码中硬编码凭据

**权限管理**:
- 遵循最小权限原则
- 使用专用 API 用户
- 定期审计权限配置

### 6. 分区 (Division) 管理

**重要提示**:
- 所有路由资源都属于一个分区
- 确保用户有访问相应分区的权限
- 创建资源时必须指定正确的分区 ID

### 7. 测试建议

**开发环境**:
- 使用 Genesys Cloud 开发环境进行测试
- 避免在生产环境直接测试新代码
- 使用模拟数据进行单元测试

### 8. 版本兼容性

**SDK 版本**:
- 定期更新 SDK 以获取最新功能
- 注意版本变更的破坏性更改
- 查看 SDK 发布说明了解变更

---

## 参考资源

- [Genesys Cloud Developer Center](https://developer.genesys.cloud/)
- [Genesys Cloud API Reference](https://developer.genesys.cloud/api/)
- [Genesys Cloud SDK Documentation](https://developer.genesys.cloud/platform/api-sdk-documentation)
- [Genesys Cloud Resource Center](https://help.mypurecloud.com/)

---

*文档生成日期: 2026-02-25*
