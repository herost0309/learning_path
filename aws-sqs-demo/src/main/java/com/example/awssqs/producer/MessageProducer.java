package com.example.awssqs.producer;

import com.example.awssqs.config.SqsConfiguration.SqsProperties;
import com.example.awssqs.domain.MessagePersistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SQS消息生产者
 * 实现消息发送、消息去重、批量发送等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageProducer {

    private final SqsAsyncClient sqsAsyncClient;
    private final MessagePersistenceService persistenceService;
    private final ObjectMapper objectMapper;
    private final SqsProperties sqsProperties;

    /**
     * 发送消息到标准队列
     * 流程: 1. 持久化消息 -> 2. 发送到SQS -> 3. 更新状态
     */
    public <T> String sendToStandardQueue(String queueUrl, T payload,
                                          Long tenantId, String aggregateId, String aggregateType) {
        try {
            // 1. 先持久化消息
            MessagePersistence persistence = persistenceService.persistBeforeSend(
                    extractQueueName(queueUrl), payload, tenantId, aggregateId, aggregateType);

            String messageId = persistence.getMessageId();

            // 2. 发送到SQS
            String body = objectMapper.writeValueAsString(payload);

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .messageAttributes(buildMessageAttributes(payload, messageId, tenantId))
                    .messageSystemAttributes(buildSystemAttributes())
                    .messageDeduplicationId(null) // 标准队列不支持
                    .messageGroupId(null) // 标准队列不支持
                    .delaySeconds(sqsProperties.getProducer().getDefaultDelaySeconds())
                    .build();

            SendMessageResponse response = sqsAsyncClient.sendMessage(request).join();
            String sqsMessageId = response.messageId();

            // 3. 更新消息状态为已发送
            persistenceService.markAsSent(messageId, response.sequenceNumber());

            log.info("Message sent to standard queue: messageId={}, sqsMessageId={}, queue={}",
                    messageId, sqsMessageId, queueUrl);

            return messageId;

        } catch (Exception e) {
            log.error("Failed to send message to standard queue", e);
            throw new RuntimeException("Failed to send message", e);
        }
    }

    /**
     * 发送消息到FIFO队列
     * FIFO队列支持严格的消息顺序和消息去重
     */
    public <T> String sendToFifoQueue(String queueUrl, T payload,
                                      Long tenantId, String aggregateId, String aggregateType,
                                      String messageGroupId, String deduplicationId) {
        try {
            // 1. 先持久化消息
            MessagePersistence persistence = persistenceService.persistBeforeSend(
                    extractQueueName(queueUrl), payload, tenantId, aggregateId, aggregateType);

            String messageId = persistence.getMessageId();

            // 2. 发送到FIFO队列
            String body = objectMapper.writeValueAsString(payload);

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .messageAttributes(buildMessageAttributes(payload, messageId, tenantId))
                    .messageSystemAttributes(buildSystemAttributes())
                    .messageDeduplicationId(deduplicationId != null ? deduplicationId : messageId)
                    .messageGroupId(messageGroupId)
                    .delaySeconds(sqsProperties.getProducer().getDefaultDelaySeconds())
                    .build();

            SendMessageResponse response = sqsAsyncClient.sendMessage(request).join();
            String sqsMessageId = response.messageId();

            // 3. 更新消息状态为已发送
            persistenceService.markAsSent(messageId, response.sequenceNumber());

            log.info("Message sent to FIFO queue: messageId={}, sqsMessageId={}, groupId={}, queue={}",
                    messageId, sqsMessageId, messageGroupId, queueUrl);

            return messageId;

        } catch (Exception e) {
            log.error("Failed to send message to FIFO queue", e);
            throw new RuntimeException("Failed to send message", e);
        }
    }

    /**
     * 批量发送消息（最多10条）
     */
    public <T> BatchSendMessageResult batchSend(String queueUrl, java.util.List<T> payloads,
                                                 Long tenantId, String aggregateId, String aggregateType) {
        try {
            // 批量持久化
            java.util.List<MessagePersistence> persistences = payloads.stream()
                    .map(payload -> persistenceService.persistBeforeSend(
                            extractQueueName(queueUrl), payload, tenantId, aggregateId, aggregateType))
                    .toList();

            // 批量发送到SQS
            java.util.List<SendMessageBatchRequestEntry> entries = new java.util.ArrayList<>();
            for (int i = 0; i < persistences.size(); i++) {
                MessagePersistence p = persistences.get(i);
                String body = objectMapper.writeValueAsString(payloads.get(i));

                entries.add(SendMessageBatchRequestEntry.builder()
                        .id(String.valueOf(i))
                        .messageBody(body)
                        .messageAttributes(buildMessageAttributes(payloads.get(i), p.getMessageId(), tenantId))
                        .messageSystemAttributes(buildSystemAttributes())
                        .build());
            }

            SendMessageBatchRequest batchRequest = SendMessageBatchRequest.builder()
                    .queueUrl(queueUrl)
                    .entries(entries)
                    .build();

            SendMessageBatchResponse response = sqsAsyncClient.sendMessageBatch(batchRequest).join();

            // 更新成功发送的消息状态
            for (SendMessageBatchResultEntry entry : response.successful()) {
                MessagePersistence p = persistences.get(Integer.parseInt(entry.id()));
                persistenceService.markAsSent(p.getMessageId(), entry.sequenceNumber());
            }

            log.info("Batch sent: successful={}, failed={}",
                    response.successful().size(), response.failed().size());

            return new BatchSendMessageResult(response, persistences);

        } catch (Exception e) {
            log.error("Failed to batch send messages", e);
            throw new RuntimeException("Failed to batch send messages", e);
        }
    }

    /**
     * 发送带延迟的消息
     */
    public <T> String sendWithDelay(String queueUrl, T payload, int delaySeconds,
                                     Long tenantId, String aggregateId, String aggregateType) {
        try {
            MessagePersistence persistence = persistenceService.persistBeforeSend(
                    extractQueueName(queueUrl), payload, tenantId, aggregateId, aggregateType);

            String body = objectMapper.writeValueAsString(payload);

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .messageAttributes(buildMessageAttributes(payload, persistence.getMessageId(), tenantId))
                    .delaySeconds(delaySeconds)
                    .build();

            sqsAsyncClient.sendMessage(request).join();

            persistenceService.markAsSent(persistence.getMessageId(), null);

            log.info("Message sent with delay: messageId={}, delaySeconds={}",
                    persistence.getMessageId(), delaySeconds);

            return persistence.getMessageId();

        } catch (Exception e) {
            log.error("Failed to send message with delay", e);
            throw new RuntimeException("Failed to send message", e);
        }
    }

    /**
     * 构建消息属性
     */
    private <T> Map<String, MessageAttributeValue> buildMessageAttributes(T payload, String messageId, Long tenantId) {
        Map<String, MessageAttributeValue> attributes = new HashMap<>();

        // 消息类型
        attributes.put("MessageType", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(payload.getClass().getSimpleName())
                .build());

        // 内部消息ID（用于追踪）
        attributes.put("InternalMessageId", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(messageId)
                .build());

        // 租户ID（如果有）
        if (tenantId != null) {
            attributes.put("TenantId", MessageAttributeValue.builder()
                    .dataType("Number")
                    .stringValue(String.valueOf(tenantId))
                    .build());
        }

        // 请求ID（用于追踪）
        attributes.put("RequestId", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(UUID.randomUUID().toString())
                .build());

        // 消息优先级
        attributes.put("Priority", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue("NORMAL")
                .build());

        return attributes;
    }

    /**
     * 构建系统属性
     */
    private Map<MessageSystemAttributeNameForSends, MessageSystemAttributeValue> buildSystemAttributes() {
        Map<MessageSystemAttributeNameForSends, MessageSystemAttributeValue> attributes = new HashMap<>();

        // 可以添加追踪ID等系统级属性
        // attributes.put(MessageSystemAttributeNameForSends.fromValue("AWSTraceHeader"), ...)

        return attributes;
    }

    /**
     * 从队列URL提取队列名称
     */
    private String extractQueueName(String queueUrl) {
        int lastSlash = queueUrl.lastIndexOf('/');
        return lastSlash >= 0 ? queueUrl.substring(lastSlash + 1) : queueUrl;
    }

    /**
     * 异步发送消息（适用于高吞吐场景）
     */
    public CompletableFuture<String> sendAsync(String queueUrl, Object payload,
                                               Long tenantId, String aggregateId, String aggregateType) {
        return CompletableFuture.supplyAsync(() ->
                sendToStandardQueue(queueUrl, payload, tenantId, aggregateId, aggregateType));
    }

    /**
     * 批量发送结果封装
     */
    public record BatchSendMessageResult(
            SendMessageBatchResponse response,
            java.util.List<MessagePersistence> persistences
    ) {
        public boolean isAllSuccessful() {
            return response.failed().isEmpty();
        }

        public java.util.List<String> getFailedMessageIds() {
            return response.failed().stream()
                    .map(entry -> persistences.get(Integer.parseInt(entry.id())).getMessageId())
                    .toList();
        }
    }
}
