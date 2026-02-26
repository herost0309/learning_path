# AWS SQS 开发完全指南

## 目录

- [一、SQS 基础概念](#一sqs-基础概念)
- [二、Acknowledgement 机制详解](#二acknowledgement-机制详解)
- [三、@Header 注解使用](#三header-注解使用)
- [四、DLQ (Dead Letter Queue) 详解](#四dlq-dead-letter-queue-详解)
- [五、最佳实践](#五最佳实践)
- [六、参考文档](#六参考文档)

---

## 一、SQS 基础概念

### 1.1 队列类型

| 类型 | 特点 | 适用场景 |
|------|------|----------|
| **标准队列 (Standard)** | 高吞吐量、至少一次投递、最佳顺序 | 高并发、允许偶尔重复 |
| **FIFO 队列** | 严格顺序、精确一次投递、3000 TPS | 订单处理、金融交易 |

### 1.2 消息生命周期

```
┌─────────────────────────────────────────────────────────────────┐
│                        消息生命周期                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   Producer                    SQS                     Consumer  │
│      │                         │                          │     │
│      │  1. SendMessage         │                          │     │
│      │────────────────────────▶│                          │     │
│      │                         │                          │     │
│      │                         │  2. ReceiveMessage       │     │
│      │                         │◀─────────────────────────│     │
│      │                         │                          │     │
│      │                         │     [Visibility Timeout] │     │
│      │                         │                          │     │
│      │                         │  3. DeleteMessage        │     │
│      │                         │◀─────────────────────────│     │
│      │                         │      (Acknowledge)       │     │
│      │                         │                          │     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 1.3 关键参数说明

| 参数 | 默认值 | 范围 | 说明 |
|------|--------|------|------|
| VisibilityTimeout | 30s | 0s - 12h | 消息被消费后对其他消费者不可见的时间 |
| ReceiveMessageWaitTimeSeconds | 0s | 0s - 20s | 长轮询等待时间 |
| MessageRetentionPeriod | 4天 | 1分钟 - 14天 | 消息在队列中保留的最长时间 |
| DelaySeconds | 0s | 0s - 15分钟 | 消息发送后的延迟时间 |
| MaxReceiveCount | - | 1 - 1000 | 消息最大接收次数（用于DLQ） |

---

## 二、Acknowledgement 机制详解

### 2.1 原始设计原理

SQS 的 Acknowledgement 机制基于**显式确认**原则：

1. **消息不会自动删除** - 消费者接收消息后，消息进入 "in-flight" 状态
2. **Visibility Timeout** - 在此期间，消息对其他消费者不可见
3. **必须显式删除** - 消费者处理完成后必须调用 DeleteMessage API
4. **超时自动恢复** - 若未在 Visibility Timeout 内确认，消息重新可见

### 2.2 消息状态流转图

```
                    ┌──────────────────────────────────────┐
                    │                                      │
                    ▼                                      │
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐  │
│  新消息  │───▶│ Available│───▶│In-flight│───▶│ Deleted │  │
└─────────┘    └─────────┘    └─────────┘    └─────────┘  │
                    ▲               │                      │
                    │               │ Acknowledge          │
                    │               │ (Delete)             │
                    │               ▼                      │
                    │        ┌─────────────┐               │
                    │        │   成功处理   │               │
                    │        └─────────────┘               │
                    │                                      │
                    │               ┌─────────────┐        │
                    │               │   处理失败   │        │
                    │               └─────────────┘        │
                    │                       │              │
                    │                       │ Visibility   │
                    │                       │ Timeout      │
                    └───────────────────────┘              │
                                                          │
                                    (或 maxReceiveCount   │
                                     达到后转入 DLQ)      │
```

### 2.3 Spring Cloud AWS 三种确认模式

#### 模式对比

| 模式 | 行为 | 适用场景 |
|------|------|----------|
| `ALWAYS` (自动) | 方法执行成功后自动确认 | 简单场景，失败即重试 |
| `MANUAL` (手动) | 需要代码显式调用 acknowledge() | 复杂业务逻辑，精确控制 |
| `BATCH` (批量) | 批量消息一起确认 | 高吞吐量场景 |

#### 代码示例

```java
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import io.awspring.cloud.sqs.listener.acknowledgement.AcknowledgeMode;
import org.springframework.stereotype.Component;

@Component
public class SqsMessageListener {

    // ==================== 模式1: 自动确认 (默认) ====================
    @SqsListener(value = "auto-ack-queue", acknowledgementMode = AcknowledgeMode.ALWAYS)
    public void handleWithAutoAck(String message) {
        // 方法正常执行完成后，Spring Cloud AWS 自动调用 DeleteMessage
        // 如果抛出异常，消息不会被删除，将在 Visibility Timeout 后重新可见
        processMessage(message);
    }

    // ==================== 模式2: 手动确认 ====================
    @SqsListener(value = "manual-ack-queue", acknowledgementMode = AcknowledgeMode.MANUAL)
    public void handleWithManualAck(String message, Acknowledgement acknowledgement) {
        try {
            // 执行业务逻辑
            processMessage(message);

            // 业务成功，显式确认 - 消息将从队列删除
            acknowledgement.acknowledge();

        } catch (RetryableException e) {
            // 可重试异常 - 不确认，消息将重新可见
            log.warn("可重试错误，消息将重新投递: {}", e.getMessage());
            // 不调用 acknowledge() 即可

        } catch (NonRetryableException e) {
            // 不可重试异常 - 确认删除，避免进入DLQ
            log.error("不可重试错误，跳过消息: {}", e.getMessage());
            acknowledgement.acknowledge();
            // 同时记录到错误表供后续处理
            saveToErrorTable(message, e);
        }
    }

    // ==================== 模式3: 批量处理与确认 ====================
    @SqsListener(value = "batch-queue", acknowledgementMode = AcknowledgeMode.MANUAL)
    public void handleBatch(List<String> messages, Acknowledgement acknowledgement) {
        List<String> successMessages = new ArrayList<>();
        List<String> failedMessages = new ArrayList<>();

        for (String message : messages) {
            try {
                processMessage(message);
                successMessages.add(message);
            } catch (Exception e) {
                failedMessages.add(message);
            }
        }

        // 批量确认成功处理的消息
        if (!successMessages.isEmpty()) {
            acknowledgement.acknowledge();
        }

        // 失败的消息不确认，将重新可见
        log.warn("处理完成: 成功={}, 失败={}", successMessages.size(), failedMessages.size());
    }
}
```

### 2.4 Visibility Timeout 设置指南

```java
@Configuration
public class SqsConfig {

    @Bean
    public SqsMessageListenerContainerFactory<String> containerFactory(
            SqsAsyncClient sqsAsyncClient) {
        return SqsMessageListenerContainerFactory.<String>builder()
                .sqsAsyncClient(sqsAsyncClient)
                // Visibility Timeout 应该大于最大处理时间
                .visibilityTimeout(Duration.ofSeconds(60))
                .build();
    }
}
```

**设置原则**：
- Visibility Timeout > 最大消息处理时间
- 建议设置为处理时间的 **1.5 - 2 倍**
- 可以在运行时通过 `ChangeMessageVisibility` API 延长

```java
// 运行时延长 Visibility Timeout
@SqsListener(value = "long-processing-queue", acknowledgementMode = AcknowledgeMode.MANUAL)
public void handleLongProcess(String message, Acknowledgement acknowledgement) {
    // 处理需要较长时间
    // Spring Cloud AWS 会自动处理心跳延长
    try {
        longRunningProcess(message);
        acknowledgement.acknowledge();
    } catch (Exception e) {
        // 处理失败
    }
}
```

---

## 三、@Header 注解使用

### 3.1 基本用法

```java
@Component
public class HeaderExampleListener {

    // ==================== 获取单个消息属性 ====================
    @SqsListener("my-queue")
    public void handleWithSingleHeader(
            @Payload String body,
            @Header("messageType") String messageType,
            @Header("version") Integer version) {
        log.info("收到消息: type={}, version={}", messageType, version);
    }

    // ==================== 获取所有消息属性 ====================
    @SqsListener("my-queue")
    public void handleWithAllHeaders(
            @Payload String body,
            Headers headers) {
        String traceId = headers.get("traceId");
        String source = headers.get("source");
        log.info("traceId={}, source={}", traceId, source);
    }

    // ==================== 获取系统属性 ====================
    @SqsListener("my-queue")
    public void handleWithSystemAttributes(
            @Payload String body,
            @Header(value = "SenderId", type = MessageSystemAttribute.class) String senderId,
            @Header(value = "SentTimestamp", type = MessageSystemAttribute.class) Long sentTime,
            @Header(value = "ApproximateReceiveCount", type = MessageSystemAttribute.class) Integer receiveCount) {
        log.info("发送者: {}, 时间: {}, 接收次数: {}", senderId, sentTime, receiveCount);
    }

    // ==================== 可选 Header ====================
    @SqsListener("my-queue")
    public void handleWithOptionalHeader(
            @Payload String body,
            @Header(value = "optionalHeader", required = false) String optionalValue) {
        if (optionalValue != null) {
            log.info("可选Header值: {}", optionalValue);
        }
    }
}
```

### 3.2 发送消息时设置 Header

```java
@Service
public class MessageSenderService {

    private final SqsTemplate sqsTemplate;

    // ==================== 方式1: 使用 MessageBuilder ====================
    public void sendWithMessageBuilder() {
        Message<String> message = MessageBuilder
                .withPayload("{\"orderId\": \"12345\"}")
                .setHeader("messageType", "ORDER_CREATED")
                .setHeader("version", 1)
                .setHeader("traceId", UUID.randomUUID().toString())
                .setHeader("source", "order-service")
                .setHeader("contentType", "application/json")
                .build();

        sqsTemplate.send("orders-queue", message);
    }

    // ==================== 方式2: 使用 fluent API ====================
    public void sendWithFluentApi() {
        sqsTemplate.send(to -> to
                .queue("orders-queue")
                .payload("{\"orderId\": \"12345\"}")
                .header("messageType", "ORDER_CREATED")
                .header("traceId", UUID.randomUUID().toString())
                .delaySeconds(10)  // 延迟10秒
        );
    }

    // ==================== FIFO 队列发送 ====================
    public void sendToFifoQueue() {
        sqsTemplate.send(to -> to
                .queue("orders.fifo")
                .payload("{\"orderId\": \"12345\"}")
                .header("messageType", "ORDER_CREATED")
                // FIFO 必需属性
                .messageGroupId("order-group-1")
                .messageDeduplicationId(UUID.randomUUID().toString())
        );
    }
}
```

### 3.3 可用的系统属性

| 属性名 | 类型 | 描述 |
|--------|------|------|
| `SenderId` | String | 发送消息的AWS账户/用户ID |
| `SentTimestamp` | Long | 消息发送时间戳 (毫秒) |
| `ApproximateReceiveCount` | Integer | 大约接收次数 |
| `ApproximateFirstReceiveTimestamp` | Long | 首次接收时间戳 |
| `MessageDeduplicationId` | String | 去重ID (仅FIFO) |
| `MessageGroupId` | String | 消息组ID (仅FIFO) |
| `SequenceNumber` | String | 序列号 (仅FIFO) |
| `AWSTraceHeader` | String | X-Ray分布式追踪头 |

---

## 四、DLQ (Dead Letter Queue) 详解

### 4.1 DLQ 设计原理

```
┌─────────────────────────────────────────────────────────────────────┐
│                         DLQ 工作流程                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ┌────────────────────────────────────────────────────────────┐   │
│   │                    主队列 (Main Queue)                      │   │
│   │                                                            │   │
│   │   Message ──▶ Consumer ──▶ 处理失败                        │   │
│   │                               │                            │   │
│   │                               ▼                            │   │
│   │                      receiveCount++                        │   │
│   │                               │                            │   │
│   │                               ▼                            │   │
│   │               ┌─────────────────────────────┐              │   │
│   │               │ receiveCount < maxReceiveCount? │           │   │
│   │               └─────────────────────────────┘              │   │
│   │                       │              │                     │   │
│   │                      YES            NO                      │   │
│   │                       │              │                     │   │
│   │                       ▼              ▼                     │   │
│   │               重新变为可见      转移到 DLQ                   │   │
│   │               (等待重试)        (终止重试)                   │   │
│   └────────────────────────────────────────────────────────────┘   │
│                                          │                         │
│                                          ▼                         │
│   ┌────────────────────────────────────────────────────────────┐   │
│   │                    死信队列 (DLQ)                           │   │
│   │                                                            │   │
│   │   用途:                                                    │   │
│   │   1. 保存无法处理的消息                                    │   │
│   │   2. 供开发人员分析和调试                                  │   │
│   │   3. 支持手动或自动重试                                    │   │
│   │   4. 触发告警通知                                          │   │
│   └────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.2 DLQ 配置

#### AWS CLI 配置

```bash
# 1. 创建死信队列
aws sqs create-queue \
    --queue-name my-service-dlq \
    --attributes '{
        "VisibilityTimeout": "30",
        "MessageRetentionPeriod": "1209600"
    }'

# 2. 获取DLQ的ARN
DLQ_ARN=$(aws sqs get-queue-attributes \
    --queue-url https://sqs.region.amazonaws.com/account/my-service-dlq \
    --attribute-names QueueArn \
    --query 'Attributes.QueueArn' \
    --output text)

# 3. 创建主队列并关联DLQ
aws sqs create-queue \
    --queue-name my-service-queue \
    --attributes "{
        \"VisibilityTimeout\": \"60\",
        \"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"$DLQ_ARN\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"
    }"
```

#### Java/Spring 配置

```java
@Configuration
public class DlqConfiguration {

    // ==================== 死信队列定义 ====================
    @Bean
    public Queue deadLetterQueue() {
        return Queue.builder()
                .name("my-service-dlq")
                .messageRetentionPeriodSeconds(1_209_600)  // 14天
                .visibilityTimeoutSeconds(30)
                .build();
    }

    // ==================== 主队列定义 (带DLQ) ====================
    @Bean
    public Queue mainQueue(Queue deadLetterQueue) {
        return Queue.builder()
                .name("my-service-queue")
                .visibilityTimeoutSeconds(60)
                .redrivePolicy(RedrivePolicy.builder()
                        .deadLetterQueue(deadLetterQueue)
                        .maxReceiveCount(3)  // 3次失败后转入DLQ
                        .build())
                .build();
    }

    // ==================== FIFO 队列的 DLQ ====================
    @Bean
    public Queue fifoDeadLetterQueue() {
        return Queue.builder()
                .name("my-service-dlq.fifo")
                .fifoQueue(true)
                .messageRetentionPeriodSeconds(1_209_600)
                .build();
    }

    @Bean
    public Queue fifoMainQueue(Queue fifoDeadLetterQueue) {
        return Queue.builder()
                .name("my-service-queue.fifo")
                .fifoQueue(true)
                .contentBasedDeduplication(true)
                .visibilityTimeoutSeconds(60)
                .redrivePolicy(RedrivePolicy.builder()
                        .deadLetterQueue(fifoDeadLetterQueue)
                        .maxReceiveCount(3)
                        .build())
                .build();
    }
}
```

### 4.3 DLQ 消费者处理

```java
@Component
@Slf4j
public class DlqMessageProcessor {

    private final SqsTemplate sqsTemplate;
    private final ErrorRecordService errorRecordService;
    private final NotificationService notificationService;

    // ==================== DLQ 监听器 ====================
    @SqsListener(value = "my-service-dlq", acknowledgementMode = AcknowledgeMode.MANUAL)
    public void processDeadLetterMessage(
            @Payload String message,
            @Header(value = "ApproximateReceiveCount", type = MessageSystemAttribute.class)
            Integer originalReceiveCount,
            @Header("traceId") String traceId,
            Headers headers,
            Acknowledgement acknowledgement) {

        log.warn("收到死信消息: traceId={}, 原始接收次数={}", traceId, originalReceiveCount);

        try {
            // 1. 记录到数据库供后续分析
            ErrorRecord record = ErrorRecord.builder()
                    .messageBody(message)
                    .traceId(traceId)
                    .headers(extractHeaders(headers))
                    .receiveCount(originalReceiveCount)
                    .createTime(LocalDateTime.now())
                    .status(ErrorStatus.PENDING)
                    .build();
            errorRecordService.save(record);

            // 2. 发送告警通知
            notificationService.sendAlert(
                    "DLQ消息告警",
                    String.format("死信消息需要处理，记录ID: %s", record.getId())
            );

            // 3. 确认消息（从DLQ删除）
            acknowledgement.acknowledge();

        } catch (Exception e) {
            log.error("处理死信消息失败", e);
            // 不确认，消息将保留在DLQ
        }
    }

    // ==================== 手动重试工具方法 ====================
    public void retryMessage(String messageId, String targetQueue) {
        // 从错误记录表获取消息
        ErrorRecord record = errorRecordService.findById(messageId);

        // 重新发送到主队列
        sqsTemplate.send(to -> to
                .queue(targetQueue)
                .payload(record.getMessageBody())
                .headers(record.getHeaders())
        );

        // 更新记录状态
        record.setStatus(ErrorStatus.RETRYING);
        errorRecordService.update(record);
    }

    private Map<String, String> extractHeaders(Headers headers) {
        Map<String, String> result = new HashMap<>();
        headers.forEach((key, value) -> result.put(key, value.toString()));
        return result;
    }
}
```

### 4.4 DLQ 最佳实践

| 实践项 | 建议 | 原因 |
|--------|------|------|
| **maxReceiveCount** | 3-5次 | 平衡重试机会和系统负载 |
| **DLQ 保留期** | 14天（最大值） | 给予足够时间分析和处理 |
| **DLQ 监控** | CloudWatch 告警 | 及时发现处理失败 |
| **消息属性保留** | 保留原始属性 | 便于追踪和问题定位 |
| **定期清理** | 建立清理流程 | 避免DLQ无限增长 |
| **独立IAM权限** | 最小权限原则 | DLQ应有独立安全控制 |

### 4.5 CloudWatch 监控配置

```yaml
# cloudwatch-alarm-config.yaml
Resources:
  DlqMessageCountAlarm:
    Type: AWS::CloudWatch::Alarm
    Properties:
      AlarmName: "DLQ-Message-Count-Alert"
      AlarmDescription: "DLQ中有消息堆积"
      Namespace: "AWS/SQS"
      MetricName: "ApproximateNumberOfMessagesVisible"
      Dimensions:
        - Name: QueueName
          Value: "my-service-dlq"
      Statistic: "Sum"
      Period: 300  # 5分钟
      EvaluationPeriods: 1
      Threshold: 1
      ComparisonOperator: "GreaterThanThreshold"
      TreatMissingData: "notBreaching"
      AlarmActions:
        - !Ref AlertNotificationTopic

  DlqMessageAgeAlarm:
    Type: AWS::CloudWatch::Alarm
    Properties:
      AlarmName: "DLQ-Message-Age-Alert"
      AlarmDescription: "DLQ中有消息超过1小时"
      Namespace: "AWS/SQS"
      MetricName: "ApproximateAgeOfOldestMessage"
      Dimensions:
        - Name: QueueName
          Value: "my-service-dlq"
      Statistic: "Maximum"
      Period: 300
      EvaluationPeriods: 1
      Threshold: 3600  # 1小时
      ComparisonOperator: "GreaterThanThreshold"
```

---

## 五、最佳实践

### 5.1 队列设计原则

```
┌─────────────────────────────────────────────────────────────────┐
│                      队列设计决策树                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│                    是否需要严格顺序？                            │
│                         │                                       │
│              ┌──────────┴──────────┐                            │
│             YES                    NO                           │
│              │                      │                           │
│              ▼                      ▼                           │
│         FIFO 队列            是否需要高吞吐量？                  │
│              │                      │                           │
│     (消息组ID保证顺序)     ┌────────┴────────┐                   │
│                            │                  │                   │
│                          YES                NO                   │
│                            │                  │                   │
│                            ▼                  ▼                   │
│                      标准队列            标准队列                 │
│                    (批量操作)          (简单配置)                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 生产级配置模板

```java
@Configuration
public class ProductionSqsConfig {

    @Bean
    public SqsMessageListenerContainerFactory<Order> orderQueueFactory(
            SqsAsyncClient sqsAsyncClient,
            ObjectMapper objectMapper) {

        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        converter.setStrictContentTypeMatch(false);

        return SqsMessageListenerContainerFactory.<Order>builder()
                .sqsAsyncClient(sqsAsyncClient)
                .messageConverter(converter)
                // 并发配置
                .maxConcurrentMessages(10)
                .maxMessagesPerPoll(10)
                // 超时配置
                .pollTimeout(Duration.ofSeconds(20))        // 长轮询
                .visibilityTimeout(Duration.ofSeconds(60))  // 可见性超时
                // 确认模式
                .acknowledgeMode(AcknowledgeMode.MANUAL)
                // 错误处理
                .errorHandler(new LoggingErrorHandler())
                .autoStartup(true)
                .build();
    }
}
```

### 5.3 异常处理策略

```java
@Component
@Slf4j
public class RobustMessageHandler {

    @SqsListener(value = "orders-queue", acknowledgementMode = AcknowledgeMode.MANUAL)
    public void handleOrder(
            @Payload Order order,
            @Header("traceId") String traceId,
            @Header(value = "ApproximateReceiveCount", type = MessageSystemAttribute.class)
            Integer receiveCount,
            Acknowledgement acknowledgement) {

        MDC.put("traceId", traceId);

        try {
            // 执行业务逻辑
            processOrder(order);
            acknowledgement.acknowledge();

        } catch (ValidationException e) {
            // 数据校验失败 - 永久性错误，不应重试
            log.error("订单数据校验失败: {}", e.getMessage());
            acknowledgement.acknowledge();  // 确认删除，避免进入DLQ
            saveToErrorTable(order, e);

        } catch (ExternalServiceUnavailableException e) {
            // 外部服务不可用 - 暂时性错误，应该重试
            log.warn("外部服务不可用，稍后重试: {}", e.getMessage());

            if (receiveCount >= 3) {
                // 重试次数过多，让消息进入DLQ
                throw e;
            }
            // 不确认，等待重新投递

        } catch (OptimisticLockException e) {
            // 乐观锁冲突 - 稍后重试可能成功
            log.warn("并发冲突，稍后重试: {}", e.getMessage());
            // 不确认

        } catch (Exception e) {
            // 未知异常 - 记录日志，让消息进入DLQ
            log.error("处理订单时发生未知异常", e);
            throw e;

        } finally {
            MDC.clear();
        }
    }
}
```

### 5.4 监控指标清单

| 指标 | 警戒值 | 处理建议 |
|------|--------|----------|
| `ApproximateNumberOfMessagesVisible` | > 1000 | 增加消费者数量 |
| `ApproximateNumberOfMessagesNotVisible` | > 100 | 检查处理时间是否过长 |
| `NumberOfMessagesReceived` | 突然下降 | 检查消费者是否正常 |
| `ApproximateAgeOfOldestMessage` | > 5分钟 | 消息积压，增加处理能力 |
| `DLQ ApproximateNumberOfMessagesVisible` | > 0 | 立即检查处理失败原因 |

### 5.5 完整示例：订单处理服务

```java
@SpringBootApplication
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}

@Service
@Slf4j
public class OrderProcessingService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    @SqsListener(
            value = "orders-queue",
            factory = "orderQueueFactory",
            acknowledgementMode = AcknowledgeMode.MANUAL
    )
    public void processOrderMessage(
            @Payload OrderMessage orderMessage,
            @Header("messageType") String messageType,
            @Header("traceId") String traceId,
            @Header(value = "ApproximateReceiveCount", type = MessageSystemAttribute.class)
            Integer receiveCount,
            Acknowledgement acknowledgement) {

        // 设置追踪上下文
        MDC.put("traceId", traceId);
        MDC.put("orderId", orderMessage.getOrderId());

        log.info("开始处理订单: orderId={}, type={}, attempt={}",
                orderMessage.getOrderId(), messageType, receiveCount);

        try {
            // 1. 幂等性检查
            if (isOrderProcessed(orderMessage.getOrderId())) {
                log.info("订单已处理，跳过: {}", orderMessage.getOrderId());
                acknowledgement.acknowledge();
                return;
            }

            // 2. 业务处理
            switch (messageType) {
                case "ORDER_CREATED":
                    handleOrderCreated(orderMessage);
                    break;
                case "ORDER_CANCELLED":
                    handleOrderCancelled(orderMessage);
                    break;
                default:
                    log.warn("未知消息类型: {}", messageType);
            }

            // 3. 标记处理完成
            markOrderProcessed(orderMessage.getOrderId());

            // 4. 确认消息
            acknowledgement.acknowledge();
            log.info("订单处理完成: {}", orderMessage.getOrderId());

        } catch (InsufficientInventoryException e) {
            // 库存不足 - 等待补货后重试
            log.warn("库存不足，等待重试: {}", e.getMessage());
            // 不确认，等待重新投递

        } catch (PaymentDeclinedException e) {
            // 支付被拒绝 - 永久性错误
            log.error("支付被拒绝: {}", e.getMessage());
            acknowledgement.acknowledge();
            notificationService.notifyPaymentFailed(orderMessage);

        } catch (Exception e) {
            log.error("处理订单时发生异常", e);
            if (receiveCount >= 3) {
                // 重试次数过多，记录错误并确认
                acknowledgement.acknowledge();
                notificationService.alertProcessingFailure(orderMessage, e);
            } else {
                throw e; // 让消息重试或进入DLQ
            }
        } finally {
            MDC.clear();
        }
    }

    private void handleOrderCreated(OrderMessage message) {
        // 检查库存
        inventoryService.checkAndReserve(message.getItems());

        // 处理支付
        paymentService.processPayment(message.getPaymentInfo());

        // 更新订单状态
        orderRepository.updateStatus(message.getOrderId(), OrderStatus.CONFIRMED);

        // 发送通知
        notificationService.notifyOrderConfirmed(message);
    }

    private void handleOrderCancelled(OrderMessage message) {
        // 释放库存
        inventoryService.release(message.getItems());

        // 处理退款
        if (message.isPaid()) {
            paymentService.refund(message.getPaymentInfo());
        }

        // 更新订单状态
        orderRepository.updateStatus(message.getOrderId(), OrderStatus.CANCELLED);
    }
}
```

---

## 六、参考文档

### 6.1 官方文档

| 文档 | 链接 |
|------|------|
| AWS SQS 开发者指南 | https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/welcome.html |
| SQS API 参考 | https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/Welcome.html |
| Spring Cloud AWS 文档 | https://docs.awspring.io/spring-cloud-aws/docs/current/reference/html/ |
| SQS 最佳实践 | https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-best-practices.html |

### 6.2 关键概念文档

| 概念 | 文档链接 |
|------|----------|
| Visibility Timeout | https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html |
| Dead Letter Queues | https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html |
| 长轮询 | https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-short-and-long-polling.html |
| FIFO 队列 | https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues.html |
| 消息属性 | https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-metadata.html |

### 6.3 Spring Cloud AWS 相关

| 主题 | 说明 |
|------|------|
| `@SqsListener` 注解 | 声明式SQS消息监听器配置 |
| `SqsTemplate` | 同步发送消息到SQS的模板类 |
| `Acknowledgement` | 手动消息确认接口 |
| `Headers` | 消息头访问接口 |
| `MessageSystemAttribute` | SQS系统属性类型标记 |

### 6.4 依赖配置

```xml
<!-- Maven -->
<dependency>
    <groupId>io.awspring</groupId>
    <artifactId>spring-cloud-aws-starter-sqs</artifactId>
    <version>3.1.1</version>
</dependency>
```

```gradle
// Gradle
implementation 'io.awspring:spring-cloud-aws-starter-sqs:3.1.1'
```

### 6.5 application.yml 配置示例

```yaml
spring:
  cloud:
    aws:
      region:
        static: ap-northeast-1
      credentials:
        profile: default
      sqs:
        endpoint: https://sqs.ap-northeast-1.amazonaws.com
        listener:
          max-concurrent-messages: 10
          max-messages-per-poll: 10
          poll-timeout: 20s
          visibility-timeout: 60s

logging:
  level:
    io.awspring.cloud.sqs: DEBUG
    software.amazon.awssdk: INFO
```

---

## 附录：常见问题 FAQ

### Q1: 消息重复消费怎么办？

**A**: SQS 标准队列提供"至少一次"投递，设计时需要考虑幂等性：

```java
// 使用消息ID实现幂等性
if (processedMessageIds.contains(messageId)) {
    acknowledgement.acknowledge();
    return; // 跳过重复消息
}
```

### Q2: 如何保证消息顺序？

**A**: 使用 FIFO 队列，通过 `MessageGroupId` 分组：

```java
sqsTemplate.send(to -> to
    .queue("orders.fifo")
    .payload(order)
    .messageGroupId(order.getUserId()) // 同一用户的订单有序
);
```

### Q3: Visibility Timeout 到期但处理未完成？

**A**: Spring Cloud AWS 支持 `Visibility` 回调延长：

```java
// 框架会自动发送 ChangeMessageVisibility 请求
// 或者在配置中设置更长的 visibilityTimeout
```

### Q4: DLQ 消息如何重试？

**A**: 从 DLQ 读取消息后重新发送到主队列：

```java
public void retryFromDlq(String messageBody, Map<String, Object> headers) {
    sqsTemplate.send(to -> to
        .queue("main-queue")
        .payload(messageBody)
        .headers(headers)
    );
}
```

---

*文档版本: 1.0*
*最后更新: 2026-02-24*
