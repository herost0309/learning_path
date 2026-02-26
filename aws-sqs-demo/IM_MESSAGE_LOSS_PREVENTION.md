# IM实时消息系统防丢机制实现

## 概述

本实现针对实时IM系统场景，基于AWS SQS构建了一套完整的消息防丢机制。

## 核心设计原则

### 1. 先持久化后发送 (Outbox Pattern)
```
[客户端] -> [持久化到DB] -> [发送到SQS] -> [确认成功] -> [更新状态]
```

**优势**：
- 即使SQS发送失败，消息也已持久化，可以通过对账服务恢复
- 支持消息重放和追踪

### 2. 细粒度状态追踪

消息在生命周期中会经历以下状态转换：

```
RECEIVED_FROM_CLIENT  - 从客户端接收
       ↓
PERSISTED             - 已持久化到数据库
       ↓
SENDING_TO_SQS        - 正在发送到SQS
       ↓
SENT_TO_SQS           - 已发送到SQS
       ↓
RECEIVED_FROM_SQS     - 消费者从SQS接收
       ↓
PROCESSING            - 处理中
       ↓
PROCESSED             - 处理完成
       ↓
ACKNOWLEDGED          - 已确认（终态）
```

### 3. 幂等性保证

通过双重ID机制确保消息幂等性：
- **messageId**: 服务端生成的唯一ID
- **clientMessageId**: 客户端生成的去重ID

消费端会检查这两个ID，防止重复处理。

### 4. 手动确认模式

消费者在消息处理成功后才显式确认（acknowledge），确保：
- 处理失败的消息不会被删除
- SQS会自动重新投递未确认的消息

### 5. FIFO队列保证顺序

对于需要严格顺序保证的场景（如聊天消息），使用FIFO队列：
- 按 `conversationId` 分组，保证同一会话的消息顺序
- 使用去重ID避免重复投递

## 核心组件

### ImMessageProducer (消息生产者)

**关键功能**：
1. 消息持久化（发送前）
2. 本地缓存（失败恢复）
3. SQS发送（支持标准/FIFO队列）
4. 发送确认机制
5. 送达/已读回执

**代码流程**：
```java
public SendResult sendImMessage(ImMessage message) {
    // 1. 幂等性检查
    if (isDuplicateMessage(message)) {
        return SendResult.duplicate(...);
    }

    // 2. 保存到本地缓存
    localCache.put(messageId, message);

    // 3. 持久化到数据库
    ImMessagePersistence persistence = persistMessage(message);

    // 4. 发送到SQS
    SendMessageResponse response = sendToSqs(message, persistence);

    // 5. 更新状态
    updateStatus(persistence, SENT_TO_SQS);

    return SendResult.success(...);
}
```

### ImMessageConsumer (消息消费者)

**关键功能**：
1. 手动确认模式
2. 幂等性检查
3. 消息过期检测
4. 失败处理和DLQ
5. 投递给接收方

**代码流程**：
```java
public void consumeImMessage(String messageBody, ..., Acknowledgement acknowledgement) {
    // 1. 解析消息
    ImMessage message = parseMessage(messageBody);

    // 2. 幂等性检查
    if (isAlreadyProcessed(message)) {
        acknowledgement.acknowledge();
        return;
    }

    // 3. 检查过期
    if (message.isExpired()) {
        markMessageExpired(messageId);
        acknowledgement.acknowledge();
        return;
    }

    // 4. 处理消息
    processImMessage(message);

    // 5. 确认消息
    acknowledgement.acknowledge();
}
```

### ImMessageReconciliationService (对账服务)

**关键功能**：
1. 定时检查超时消息
2. 自动重试可恢复消息
3. 标记无法恢复的消息
4. 统计监控

**超时检查**：
```java
@Scheduled(fixedDelay = 5 * 60 * 1000)
public void performReconciliation() {
    // 1. 检查待发送超时
    checkPendingTimeout();  // 5分钟

    // 2. 检查已发送但未确认超时
    checkSentTimeout();     // 10分钟

    // 3. 检查处理中超时
    checkProcessingTimeout(); // 15分钟

    // 4. 检查DLQ消息
    checkDlqMessages();
}
```

## 消息防丢保障机制

### 场景1：发送阶段失败

| 失败点 | 恢复机制 |
|-------|---------|
| 持久化失败 | 本地缓存回滚，返回错误给客户端 |
| SQS发送失败 | 对账服务检测PENDING状态超时消息，自动重试 |
| 网络超时 | 本地缓存保留，重试机制 |

### 场景2：消费阶段失败

| 失败点 | 恢复机制 |
|-------|---------|
| 消费者崩溃 | SQS可见性超时后自动重新投递 |
| 处理异常 | 不确认消息，SQS自动重试 |
| 超过最大重试 | 进入DLQ，对账服务处理 |

### 场景3：消息丢失检测

对账服务会定期检查以下异常状态：

1. **PENDING超时**: 持久化后未发送
2. **SENT超时**: 已发送但未被消费
3. **PROCESSING超时**: 处理中但未完成
4. **DLQ堆积**: 死信队列中的消息

对于可恢复的消息，自动重试；对于无法恢复的，标记为丢失并告警。

## API接口

### 发送消息
```http
POST /api/im/messages/send
Content-Type: application/json

{
  "clientMessageId": "client-123",
  "conversationId": "conv-456",
  "senderId": "user-001",
  "receiverId": "user-002",
  "messageType": "TEXT",
  "content": "Hello!",
  "priority": "NORMAL"
}
```

### 查询消息状态
```http
GET /api/im/messages/{messageId}/status
```

### 发送送达回执
```http
POST /api/im/messages/{messageId}/delivered?receiverId=user-002
```

### 手动触发对账
```http
POST /api/im/messages/reconciliation
```

## 监控指标

建议监控以下指标：

1. **消息积压**: 各状态消息数量
2. **消息延迟**: 从发送到确认的时间
3. **重试率**: 重试消息占比
4. **丢失率**: 丢失消息占比
5. **DLQ数量**: 死信队列消息数

## 最佳实践

### 1. 客户端实现
```javascript
// 客户端发送消息
async function sendMessage(content) {
    const clientMessageId = generateUUID();

    // 先本地保存
    saveToLocal(clientMessageId, content);

    try {
        const response = await fetch('/api/im/messages/send', {
            method: 'POST',
            body: JSON.stringify({
                clientMessageId,
                content,
                // ...
            })
        });

        if (!response.success) {
            // 加入重试队列
            addToRetryQueue(clientMessageId);
        }
    } catch (e) {
        // 网络失败，加入重试队列
        addToRetryQueue(clientMessageId);
    }
}
```

### 2. 消费者幂等性
```java
// 使用Redis实现分布式锁
if (redis.setnx("msg:processed:" + messageId, "1", 300)) {
    // 处理消息
} else {
    // 已处理，跳过
}
```

### 3. FIFO队列配置
- 同一会话的消息使用相同的 MessageGroupId
- 使用 clientMessageId 作为 DeduplicationId

## 配置说明

```yaml
im:
  message:
    max-retry: 5              # 最大重试次数
    timeout-seconds: 30       # 发送超时

  reconciliation:
    enabled: true
    pending-timeout-minutes: 5     # 待发送超时
    sent-timeout-minutes: 10       # 已发送超时
    processing-timeout-minutes: 15 # 处理中超时

aws:
  sqs:
    im-queue-url: https://sqs.../im-messages
    im-queue-fifo-url: https://sqs.../im-messages.fifo
```

## 总结

本实现通过以下机制确保实时IM系统消息不丢失：

1. **持久化优先**: 先保存再发送
2. **状态追踪**: 细粒度状态管理
3. **幂等处理**: 双重ID去重
4. **手动确认**: 处理成功才删除
5. **对账恢复**: 定时检测和恢复
6. **顺序保证**: FIFO队列
7. **回执机制**: 送达/已读确认

这套机制可以在各种异常情况下保证消息的可靠投递。
