package com.example.awssqs.reconciliation;

import com.example.awssqs.domain.MessagePersistence;
import com.example.awssqs.domain.MessagePersistence.MessageStatus;
import com.example.awssqs.producer.MessagePersistenceService;
import com.example.awssqs.producer.MessageProducer;
import com.example.awssqs.repository.MessagePersistenceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 消息对账服务
 * 定期检查消息状态，发现并恢复丢失的消息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final MessagePersistenceRepository messagePersistenceRepository;
    private final MessagePersistenceService persistenceService;
    private final MessageProducer messageProducer;
    private final ObjectMapper objectMapper;

    @Value("${sqs.reconciliation.enabled:true}")
    private boolean reconciliationEnabled;

    @Value("${sqs.reconciliation.timeout-minutes:30}")
    private int timeoutMinutes;

    @Value("${sqs.reconciliation.batch-size:100}")
    private int batchSize;

    @Value("${sqs.reconciliation.max-replay-count:3}")
    private int maxReplayCount;

    // 记录最近重放过的消息，避免重复重放
    private final Map<String, LocalDateTime> recentlyReplayed = new ConcurrentHashMap<>();

    /**
     * 定时对账任务
     * 每10分钟执行一次
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    @Transactional
    public void performReconciliation() {
        if (!reconciliationEnabled) {
            log.debug("Reconciliation is disabled");
            return;
        }

        log.info("Starting message reconciliation");

        try {
            // 1. 检查超时的PENDING消息
            checkPendingMessages();

            // 2. 检查超时的SENT消息（可能发送失败）
            checkSentMessages();

            // 3. 检查超时的RECEIVED消息（可能消费者异常）
            checkReceivedMessages();

            // 4. 检查超时的PROCESSING消息（可能处理卡住）
            checkProcessingMessages();

            // 5. 清理最近重放记录（超过1小时）
            cleanupRecentReplayRecords();

            log.info("Message reconciliation completed");

        } catch (Exception e) {
            log.error("Error during message reconciliation", e);
        }
    }

    /**
     * 检查超时的PENDING消息
     * 消息已持久化但未发送到SQS
     */
    @Transactional
    public void checkPendingMessages() {
        LocalDateTime timeoutTime = LocalDateTime.now().minusMinutes(timeoutMinutes);

        List<MessagePersistence> timeoutMessages = messagePersistenceRepository
                .findTimeoutPendingMessages(MessageStatus.PENDING, timeoutTime)
                .stream()
                .limit(batchSize)
                .toList();

        if (!timeoutMessages.isEmpty()) {
            log.warn("Found {} PENDING messages that timed out", timeoutMessages.size());

            for (MessagePersistence msg : timeoutMessages) {
                if (shouldReplay(msg)) {
                    replayMessage(msg, "Pending timeout - message not sent to SQS");
                } else {
                    persistenceService.markAsLost(msg.getMessageId(),
                            "Pending timeout and max replay count exceeded");
                }
            }
        }
    }

    /**
     * 检查超时的SENT消息
     * 消息已发送到SQS但未被接收
     */
    @Transactional
    public void checkSentMessages() {
        LocalDateTime timeoutTime = LocalDateTime.now().minusMinutes(timeoutMinutes);

        List<MessagePersistence> timeoutMessages = messagePersistenceRepository
                .findTimeoutPendingMessages(MessageStatus.SENT, timeoutTime)
                .stream()
                .limit(batchSize)
                .toList();

        if (!timeoutMessages.isEmpty()) {
            log.warn("Found {} SENT messages that timed out", timeoutMessages.size());

            for (MessagePersistence msg : timeoutMessages) {
                // 检查是否真的在SQS中（可选，需要调用SQS API）
                // 这里假设消息已丢失，需要重放

                if (shouldReplay(msg)) {
                    replayMessage(msg, "Sent timeout - message not received by consumer");
                } else {
                    persistenceService.markAsLost(msg.getMessageId(),
                            "Sent timeout and max replay count exceeded");
                }
            }
        }
    }

    /**
     * 检查超时的RECEIVED消息
     * 消息已被接收但未开始处理
     */
    @Transactional
    public void checkReceivedMessages() {
        LocalDateTime timeoutTime = LocalDateTime.now().minusMinutes(timeoutMinutes);

        List<MessagePersistence> timeoutMessages = messagePersistenceRepository
                .findTimeoutPendingMessages(MessageStatus.RECEIVED, timeoutTime)
                .stream()
                .limit(batchSize)
                .toList();

        if (!timeoutMessages.isEmpty()) {
            log.warn("Found {} RECEIVED messages that timed out", timeoutMessages.size());

            for (MessagePersistence msg : timeoutMessages) {
                if (shouldReplay(msg)) {
                    replayMessage(msg, "Received timeout - consumer crashed before processing");
                } else {
                    persistenceService.markAsLost(msg.getMessageId(),
                            "Received timeout and max replay count exceeded");
                }
            }
        }
    }

    /**
     * 检查超时的PROCESSING消息
     * 消息正在处理但长时间未完成
     */
    @Transactional
    public void checkProcessingMessages() {
        LocalDateTime timeoutTime = LocalDateTime.now().minusMinutes(timeoutMinutes);

        List<MessagePersistence> timeoutMessages = messagePersistenceRepository
                .findTimeoutPendingMessages(MessageStatus.PROCESSING, timeoutTime)
                .stream()
                .limit(batchSize)
                .toList();

        if (!timeoutMessages.isEmpty()) {
            log.warn("Found {} PROCESSING messages that timed out", timeoutMessages.size());

            for (MessagePersistence msg : timeoutMessages) {
                // 处理超时可能需要特殊处理
                if (shouldReplay(msg)) {
                    replayMessage(msg, "Processing timeout - handler took too long or crashed");
                } else {
                    persistenceService.markAsLost(msg.getMessageId(),
                            "Processing timeout and max replay count exceeded");
                }
            }
        }
    }

    /**
     * 检查未确认的消息
     * 消息已发送但未从SQS中删除
     */
    @Transactional
    public void checkUnacknowledgedMessages() {
        LocalDateTime timeoutTime = LocalDateTime.now().minusMinutes(timeoutMinutes);

        List<MessagePersistence> unacknowledged = messagePersistenceRepository
                .findUnacknowledgedMessages(timeoutTime);

        if (!unacknowledged.isEmpty()) {
            log.info("Found {} unacknowledged messages", unacknowledged.size());

            // 对于已处理但未确认的消息，只需确认即可
            for (MessagePersistence msg : unacknowledged) {
                persistenceService.markAsAcknowledged(msg.getMessageId());
                log.debug("Acknowledged unprocessed message: messageId={}", msg.getMessageId());
            }
        }
    }

    /**
     * 重放消息
     */
    @Transactional
    public void replayMessage(MessagePersistence originalMessage, String reason) {
        try {
            log.info("Replaying message: messageId={}, reason={}, retryCount={}",
                    originalMessage.getMessageId(), reason, originalMessage.getRetryCount());

            // 解析原始payload
            Object payload = parsePayload(originalMessage.getPayload(), originalMessage.getPayloadType());

            // 获取队列URL
            String queueUrl = getQueueUrl(originalMessage.getQueueName());

            // 重新发送消息
            String newMessageId = messageProducer.sendToStandardQueue(
                    queueUrl,
                    payload,
                    originalMessage.getTenantId(),
                    originalMessage.getAggregateId(),
                    originalMessage.getAggregateType()
            );

            // 标记原始消息为已重放
            persistenceService.markAsReplayed(originalMessage.getMessageId());

            // 记录到最近重放列表
            recentlyReplayed.put(originalMessage.getMessageId(), LocalDateTime.now());

            // 更新原始消息的元数据，记录新消息ID
            String existingMetadata = originalMessage.getMetadata() != null ?
                    originalMessage.getMetadata() : "{}";

            Map<String, Object> metadataMap = parseMetadata(existingMetadata);
            metadataMap.put("replayedAt", LocalDateTime.now().toString());
            metadataMap.put("replayReason", reason);
            metadataMap.put("newMessageId", newMessageId);

            originalMessage.setMetadata(objectMapper.writeValueAsString(metadataMap));
            messagePersistenceRepository.save(originalMessage);

            log.info("Message replayed successfully: originalId={}, newId={}",
                    originalMessage.getMessageId(), newMessageId);

        } catch (Exception e) {
            log.error("Failed to replay message: messageId={}, error={}",
                    originalMessage.getMessageId(), e.getMessage(), e);
            throw new RuntimeException("Message replay failed", e);
        }
    }

    /**
     * 判断是否应该重放
     */
    private boolean shouldReplay(MessagePersistence message) {
        // 1. 检查重试次数
        if (message.getRetryCount() >= maxReplayCount) {
            log.warn("Message exceeded max replay count: messageId={}, retryCount={}",
                    message.getMessageId(), message.getRetryCount());
            return false;
        }

        // 2. 检查是否最近已经重放过
        LocalDateTime lastReplay = recentlyReplayed.get(message.getMessageId());
        if (lastReplay != null && lastReplay.isAfter(LocalDateTime.now().minusMinutes(30))) {
            log.debug("Message was recently replayed: messageId={}", message.getMessageId());
            return false;
        }

        // 3. 检查消息是否需要人工干预
        if (Boolean.TRUE.equals(message.getManualIntervention())) {
            log.info("Message requires manual intervention: messageId={}", message.getMessageId());
            return false;
        }

        // 4. 检查是否已在DLQ中
        if (Boolean.TRUE.equals(message.getInDlq())) {
            log.info("Message is already in DLQ: messageId={}", message.getMessageId());
            return false;
        }

        return true;
    }

    /**
     * 手动触发对账（用于紧急情况）
     */
    @Transactional
    public ReconciliationResult manualReconciliation() {
        log.info("Manual reconciliation triggered");

        ReconciliationResult result = new ReconciliationResult();

        try {
            List<MessagePersistence> pendingReplays = List.of(); // 实际应该查询

            // 执行对账
            checkPendingMessages();
            checkSentMessages();
            checkReceivedMessages();
            checkProcessingMessages();

            result.setSuccess(true);
            result.setMessage("Manual reconciliation completed");

        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Manual reconciliation failed: " + e.getMessage());
            log.error("Manual reconciliation failed", e);
        }

        return result;
    }

    /**
     * 获取对账统计信息
     */
    public ReconciliationStats getReconciliationStats() {
        long pendingCount = messagePersistenceRepository.countByStatus(MessageStatus.PENDING);
        long sentCount = messagePersistenceRepository.countByStatus(MessageStatus.SENT);
        long receivedCount = messagePersistenceRepository.countByStatus(MessageStatus.RECEIVED);
        long processingCount = messagePersistenceRepository.countByStatus(MessageStatus.PROCESSING);
        long processedCount = messagePersistenceRepository.countByStatus(MessageStatus.PROCESSED);
        long failedCount = messagePersistenceRepository.countByStatus(MessageStatus.FAILED);
        long lostCount = messagePersistenceRepository.countByStatus(MessageStatus.LOST);
        long replayedCount = messagePersistenceRepository.countByStatus(MessageStatus.REPLAYED);

        return ReconciliationStats.builder()
                .pendingCount(pendingCount)
                .sentCount(sentCount)
                .receivedCount(receivedCount)
                .processingCount(processingCount)
                .processedCount(processedCount)
                .failedCount(failedCount)
                .lostCount(lostCount)
                .replayedCount(replayedCount)
                .totalMessages(pendingCount + sentCount + receivedCount + processingCount +
                        processedCount + failedCount + lostCount + replayedCount)
                .build();
    }

    private Object parsePayload(String payload, String payloadType) {
        try {
            return objectMapper.readValue(payload, Object.class);
        } catch (Exception e) {
            return payload;
        }
    }

    private String getQueueUrl(String queueName) {
        return "https://sqs.region.amazonaws.com/account-id/" + queueName;
    }

    private Map<String, Object> parseMetadata(String metadata) {
        try {
            return objectMapper.readValue(metadata, Map.class);
        } catch (Exception e) {
            return new java.util.HashMap<>();
        }
    }

    private void cleanupRecentReplayRecords() {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        recentlyReplayed.entrySet().removeIf(entry ->
                entry.getValue().isBefore(oneHourAgo));
    }

    /**
     * 对账结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReconciliationResult {
        private boolean success;
        private String message;
        private int messagesReplayed;
        private int messagesMarkedAsLost;
    }

    /**
     * 对账统计信息
     */
    @lombok.Data
    @lombok.Builder
    public static class ReconciliationStats {
        private long totalMessages;
        private long pendingCount;
        private long sentCount;
        private long receivedCount;
        private long processingCount;
        private long processedCount;
        private long failedCount;
        private long lostCount;
        private long replayedCount;
    }
}
