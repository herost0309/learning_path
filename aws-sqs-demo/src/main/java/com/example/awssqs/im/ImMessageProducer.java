package com.example.awssqs.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * IM消息生产者服务
 * 实现防丢消息的核心机制：
 * 1. 先持久化后发送
 * 2. 发送确认机制
 * 3. 失败重试
 * 4. 本地缓存回滚
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImMessageProducer {

    private final SqsAsyncClient sqsAsyncClient;
    private final ImMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.im-queue-url}")
    private String imQueueUrl;

    @Value("${aws.sqs.im-queue-fifo-url:}")
    private String imFifoQueueUrl;

    @Value("${im.message.max-retry:5}")
    private int maxRetryCount;

    @Value("${im.message.timeout-seconds:30}")
    private int sendTimeoutSeconds;

    // 本地消息缓存（用于发送失败时的回滚和重试）
    private final Map<String, ImMessage> localCache = new ConcurrentHashMap<>();

    // 待确认消息队列
    private final Map<String, PendingMessage> pendingConfirmations = new ConcurrentHashMap<>();

    /**
     * 发送IM消息（核心方法）
     * 流程：接收 -> 持久化 -> 发送SQS -> 确认 -> 返回
     */
    @Transactional
    public SendResult sendImMessage(ImMessage message) {
        String messageId = message.getMessageId();
        long startTime = System.currentTimeMillis();

        try {
            log.info("[IM-PRODUCER] Starting to send message: messageId={}, conversationId={}, senderId={}",
                    messageId, message.getConversationId(), message.getSenderId());

            // 1. 幂等性检查
            if (isDuplicateMessage(message)) {
                log.warn("[IM-PRODUCER] Duplicate message detected: messageId={}, clientMessageId={}",
                        messageId, message.getClientMessageId());
                return SendResult.duplicate(messageId, "Message already processed");
            }

            // 2. 保存到本地缓存（用于失败恢复）
            localCache.put(messageId, message);

            // 3. 持久化到数据库（状态: RECEIVED_FROM_CLIENT）
            ImMessagePersistence persistence = persistMessage(message);

            // 4. 更新状态为 SENDING_TO_SQS
            updateStatus(persistence, ImMessagePersistence.ImMessageStatus.SENDING_TO_SQS);

            // 5. 发送到SQS（根据优先级选择队列）
            SendMessageResponse response = sendToSqs(message, persistence);

            // 6. 更新状态为 SENT_TO_SQS
            persistence.setSqsMessageId(response.messageId());
            persistence.setSqsSequenceNumber(response.sequenceNumber());
            updateStatus(persistence, ImMessagePersistence.ImMessageStatus.SENT_TO_SQS);

            // 7. 添加到待确认队列
            addToPendingConfirmations(messageId, persistence, response.messageId());

            // 8. 清理本地缓存
            localCache.remove(messageId);

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("[IM-PRODUCER] Message sent successfully: messageId={}, sqsMessageId={}, time={}ms",
                    messageId, response.messageId(), processingTime);

            return SendResult.success(messageId, response.messageId(), response.sequenceNumber());

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("[IM-PRODUCER] Failed to send message: messageId={}, time={}ms, error={}",
                    messageId, processingTime, e.getMessage(), e);

            // 处理发送失败
            handleSendFailure(messageId, e);

            return SendResult.failure(messageId, e.getMessage());
        }
    }

    /**
     * 异步发送IM消息
     */
    public CompletableFuture<SendResult> sendImMessageAsync(ImMessage message) {
        return CompletableFuture.supplyAsync(() -> sendImMessage(message));
    }

    /**
     * 批量发送消息
     */
    @Transactional
    public BatchSendResult sendBatch(List<ImMessage> messages) {
        log.info("[IM-PRODUCER] Starting batch send: count={}", messages.size());

        List<SendResult> results = new ArrayList<>();
        List<SendMessageBatchRequestEntry> entries = new ArrayList<>();
        Map<String, ImMessagePersistence> persistenceMap = new HashMap<>();

        try {
            // 1. 批量持久化
            for (int i = 0; i < messages.size(); i++) {
                ImMessage msg = messages.get(i);

                // 幂等性检查
                if (isDuplicateMessage(msg)) {
                    results.add(SendResult.duplicate(msg.getMessageId(), "Duplicate"));
                    continue;
                }

                // 持久化
                ImMessagePersistence persistence = persistMessage(msg);
                persistenceMap.put(String.valueOf(i), persistence);

                // 构建批量请求条目
                String body = objectMapper.writeValueAsString(msg);
                SendMessageBatchRequestEntry entry = SendMessageBatchRequestEntry.builder()
                        .id(String.valueOf(i))
                        .messageBody(body)
                        .messageAttributes(buildMessageAttributes(msg))
                        .messageGroupId(msg.getMessageGroupId())
                        .messageDeduplicationId(msg.getDeduplicationKey())
                        .build();

                entries.add(entry);
            }

            // 2. 批量发送到SQS
            SendMessageBatchRequest batchRequest = SendMessageBatchRequest.builder()
                    .queueUrl(selectQueueUrl(messages.get(0)))
                    .entries(entries)
                    .build();

            SendMessageBatchResponse response = sqsAsyncClient.sendMessageBatch(batchRequest)
                    .get(sendTimeoutSeconds, TimeUnit.SECONDS);

            // 3. 处理结果
            for (SendMessageBatchResultEntry success : response.successful()) {
                String id = success.id();
                ImMessagePersistence p = persistenceMap.get(id);
                if (p != null) {
                    p.setSqsMessageId(success.messageId());
                    p.setSqsSequenceNumber(success.sequenceNumber());
                    updateStatus(p, ImMessagePersistence.ImMessageStatus.SENT_TO_SQS);
                    results.add(SendResult.success(p.getMessageId(), success.messageId(), success.sequenceNumber()));
                }
            }

            // 4. 处理失败
            for (BatchResultErrorEntry error : response.failed()) {
                String id = error.id();
                ImMessagePersistence p = persistenceMap.get(id);
                if (p != null) {
                    updateStatus(p, ImMessagePersistence.ImMessageStatus.SQS_SEND_FAILED);
                    handleSendFailure(p.getMessageId(), new RuntimeException(error.message()));
                    results.add(SendResult.failure(p.getMessageId(), error.message()));
                }
            }

            log.info("[IM-PRODUCER] Batch send completed: total={}, success={}, failed={}",
                    messages.size(), response.successful().size(), response.failed().size());

            return new BatchSendResult(results, response.successful().size(), response.failed().size());

        } catch (Exception e) {
            log.error("[IM-PRODUCER] Batch send failed", e);
            return new BatchSendResult(results, 0, messages.size());
        }
    }

    /**
     * 确认消息已被消费者处理（由消费者调用）
     */
    @Transactional
    public void confirmMessageProcessed(String messageId) {
        Optional<ImMessagePersistence> opt = messageRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            ImMessagePersistence persistence = opt.get();
            updateStatus(persistence, ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED);
            pendingConfirmations.remove(messageId);
            log.info("[IM-PRODUCER] Message confirmed as processed: messageId={}", messageId);
        }
    }

    /**
     * 发送送达回执给发送方
     */
    @Transactional
    public void sendDeliveryReceipt(String messageId, String receiverId) {
        Optional<ImMessagePersistence> opt = messageRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            ImMessagePersistence persistence = opt.get();
            persistence.setDeliveryStatus(ImMessagePersistence.DeliveryStatus.DELIVERED);
            persistence.setDeliveredAt(LocalDateTime.now());
            persistence.setAckStatus(ImMessagePersistence.AckStatus.DELIVERY_ACK);
            messageRepository.save(persistence);

            log.info("[IM-PRODUCER] Delivery receipt sent: messageId={}, receiverId={}", messageId, receiverId);
            // TODO: 通过WebSocket推送送达回执给发送方
        }
    }

    /**
     * 发送已读回执
     */
    @Transactional
    public void sendReadReceipt(String messageId, String readerId) {
        Optional<ImMessagePersistence> opt = messageRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            ImMessagePersistence persistence = opt.get();
            persistence.setReadAt(LocalDateTime.now());
            persistence.setAckStatus(ImMessagePersistence.AckStatus.READ_ACK);
            messageRepository.save(persistence);

            log.info("[IM-PRODUCER] Read receipt sent: messageId={}, readerId={}", messageId, readerId);
            // TODO: 通过WebSocket推送已读回执给发送方
        }
    }

    /**
     * 重试失败的消息
     */
    @Transactional
    public void retryFailedMessage(String messageId) {
        Optional<ImMessagePersistence> opt = messageRepository.findByMessageId(messageId);
        if (opt.isEmpty()) {
            log.warn("[IM-PRODUCER] Message not found for retry: messageId={}", messageId);
            return;
        }

        ImMessagePersistence persistence = opt.get();

        if (!persistence.canRetry()) {
            log.warn("[IM-PRODUCER] Message cannot be retried: messageId={}, retryCount={}, maxRetry={}",
                    messageId, persistence.getRetryCount(), persistence.getMaxRetryCount());
            return;
        }

        try {
            // 解析原始消息
            ImMessage message = objectMapper.readValue(persistence.getPayload(), ImMessage.class);

            // 重新发送
            sendImMessage(message);

            // 更新重试计数
            persistence.setRetryCount(persistence.getRetryCount() + 1);
            messageRepository.save(persistence);

            log.info("[IM-PRODUCER] Message retry successful: messageId={}, retryCount={}",
                    messageId, persistence.getRetryCount());

        } catch (Exception e) {
            log.error("[IM-PRODUCER] Message retry failed: messageId={}", messageId, e);
            persistence.setRetryCount(persistence.getRetryCount() + 1);
            persistence.setErrorMessage(e.getMessage());
            messageRepository.save(persistence);
        }
    }

    // ============== 私有方法 ==============

    private boolean isDuplicateMessage(ImMessage message) {
        // 检查客户端消息ID
        if (message.getClientMessageId() != null) {
            return messageRepository.existsByClientMessageId(message.getClientMessageId());
        }
        // 检查服务端消息ID
        return messageRepository.findByMessageId(message.getMessageId()).isPresent();
    }

    @Transactional
    protected ImMessagePersistence persistMessage(ImMessage message) {
        // 获取或生成序号
        Long sequenceNumber = messageRepository.findMaxSequenceNumber(message.getConversationId());
        if (sequenceNumber == null) {
            sequenceNumber = 0L;
        }
        sequenceNumber++;

        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId(message.getMessageId())
                .clientMessageId(message.getClientMessageId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .receiverId(message.getReceiverId())
                .messageType(message.getMessageType().name())
                .payload(toJson(message))
                .status(ImMessagePersistence.ImMessageStatus.RECEIVED_FROM_CLIENT)
                .ackStatus(ImMessagePersistence.AckStatus.PENDING)
                .deliveryStatus(ImMessagePersistence.DeliveryStatus.PENDING)
                .priority(message.getPriority().name())
                .createdAt(LocalDateTime.now())
                .persistedAt(LocalDateTime.now())
                .retryCount(0)
                .maxRetryCount(maxRetryCount)
                .inDlq(false)
                .manualIntervention(false)
                .tenantId(message.getTenantId())
                .deviceId(message.getDeviceId())
                .sequenceNumber(sequenceNumber)
                .build();

        ImMessagePersistence saved = messageRepository.save(persistence);

        // 更新状态为 PERSISTED
        saved.setStatus(ImMessagePersistence.ImMessageStatus.PERSISTED);
        saved.setPersistedAt(LocalDateTime.now());
        return messageRepository.save(saved);
    }

    private SendMessageResponse sendToSqs(ImMessage message, ImMessagePersistence persistence) throws Exception {
        String queueUrl = selectQueueUrl(message);
        String body = objectMapper.writeValueAsString(message);

        SendMessageRequest.Builder requestBuilder = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .messageAttributes(buildMessageAttributes(message));

        // FIFO队列需要额外的参数
        if (isFifoQueue(queueUrl)) {
            requestBuilder
                    .messageGroupId(message.getMessageGroupId())
                    .messageDeduplicationId(message.getDeduplicationKey());
        }

        // 设置延迟（高优先级消息不延迟）
        if (message.getPriority() != ImMessage.MessagePriority.HIGH) {
            requestBuilder.delaySeconds(0);
        }

        SendMessageResponse response = sqsAsyncClient.sendMessage(requestBuilder.build())
                .get(sendTimeoutSeconds, TimeUnit.SECONDS);

        return response;
    }

    private String selectQueueUrl(ImMessage message) {
        // 高优先级和需要顺序保证的消息使用FIFO队列
        if (message.getPriority() == ImMessage.MessagePriority.HIGH && imFifoQueueUrl != null && !imFifoQueueUrl.isEmpty()) {
            return imFifoQueueUrl;
        }
        return imQueueUrl;
    }

    private boolean isFifoQueue(String queueUrl) {
        return queueUrl != null && queueUrl.endsWith(".fifo");
    }

    private Map<String, MessageAttributeValue> buildMessageAttributes(ImMessage message) {
        Map<String, MessageAttributeValue> attributes = new HashMap<>();

        attributes.put("MessageId", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(message.getMessageId())
                .build());

        attributes.put("ClientMessageId", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(message.getClientMessageId() != null ? message.getClientMessageId() : "")
                .build());

        attributes.put("ConversationId", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(message.getConversationId())
                .build());

        attributes.put("SenderId", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(message.getSenderId())
                .build());

        attributes.put("ReceiverId", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(message.getReceiverId() != null ? message.getReceiverId() : "")
                .build());

        attributes.put("MessageType", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(message.getMessageType().name())
                .build());

        attributes.put("Priority", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(message.getPriority().name())
                .build());

        if (message.getTenantId() != null) {
            attributes.put("TenantId", MessageAttributeValue.builder()
                    .dataType("Number")
                    .stringValue(String.valueOf(message.getTenantId()))
                    .build());
        }

        return attributes;
    }

    private void updateStatus(ImMessagePersistence persistence, ImMessagePersistence.ImMessageStatus newStatus) {
        persistence.setStatus(newStatus);
        persistence.setUpdatedAt(LocalDateTime.now());
        messageRepository.save(persistence);
    }

    private void handleSendFailure(String messageId, Exception e) {
        Optional<ImMessagePersistence> opt = messageRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            ImMessagePersistence persistence = opt.get();
            persistence.setStatus(ImMessagePersistence.ImMessageStatus.SQS_SEND_FAILED);
            persistence.setErrorMessage(e.getMessage());
            persistence.setUpdatedAt(LocalDateTime.now());
            messageRepository.save(persistence);
        }

        // 清理本地缓存
        localCache.remove(messageId);
    }

    private void addToPendingConfirmations(String messageId, ImMessagePersistence persistence, String sqsMessageId) {
        PendingMessage pending = new PendingMessage(
                messageId,
                sqsMessageId,
                persistence.getConversationId(),
                System.currentTimeMillis()
        );
        pendingConfirmations.put(messageId, pending);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    // ============== 内部类 ==============

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SendResult {
        private String messageId;
        private String sqsMessageId;
        private String sequenceNumber;
        private boolean success;
        private String errorMessage;
        private ResultType resultType;

        public enum ResultType {
            SUCCESS, DUPLICATE, FAILURE
        }

        public static SendResult success(String messageId, String sqsMessageId, String sequenceNumber) {
            return new SendResult(messageId, sqsMessageId, sequenceNumber, true, null, ResultType.SUCCESS);
        }

        public static SendResult duplicate(String messageId, String errorMessage) {
            return new SendResult(messageId, null, null, false, errorMessage, ResultType.DUPLICATE);
        }

        public static SendResult failure(String messageId, String errorMessage) {
            return new SendResult(messageId, null, null, false, errorMessage, ResultType.FAILURE);
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class BatchSendResult {
        private List<SendResult> results;
        private int successCount;
        private int failureCount;

        public boolean isAllSuccess() {
            return failureCount == 0;
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class PendingMessage {
        private String messageId;
        private String sqsMessageId;
        private String conversationId;
        private long createdAt;
    }
}
