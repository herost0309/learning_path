package com.example.awssqs.im;

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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IM消息对账服务
 * 定期检查消息状态，发现并恢复丢失的消息
 *
 * 核心机制：
 * 1. 超时检测：检查各阶段超时的消息
 * 2. 自动重试：对可恢复的消息进行重试
 * 3. 丢失标记：对无法恢复的消息标记为丢失
 * 4. 统计监控：提供对账统计信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImMessageReconciliationService {

    private final ImMessageRepository messageRepository;
    private final ImMessageProducer messageProducer;
    private final ObjectMapper objectMapper;

    @Value("${im.reconciliation.enabled:true}")
    private boolean reconciliationEnabled;

    @Value("${im.reconciliation.pending-timeout-minutes:5}")
    private int pendingTimeoutMinutes;

    @Value("${im.reconciliation.sent-timeout-minutes:10}")
    private int sentTimeoutMinutes;

    @Value("${im.reconciliation.processing-timeout-minutes:15}")
    private int processingTimeoutMinutes;

    @Value("${im.reconciliation.batch-size:100}")
    private int batchSize;

    // 对账统计
    private final Map<String, AtomicInteger> reconciliationStats = new ConcurrentHashMap<>();

    /**
     * 定时对账任务
     * 每5分钟执行一次
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 60 * 1000)
    @Transactional
    public void performReconciliation() {
        if (!reconciliationEnabled) {
            log.debug("[IM-RECONCILIATION] Reconciliation is disabled");
            return;
        }

        log.info("[IM-RECONCILIATION] Starting message reconciliation");

        try {
            // 重置统计
            resetStats();

            // 1. 检查待发送超时消息
            checkPendingTimeout();

            // 2. 检查已发送但未确认超时消息
            checkSentTimeout();

            // 3. 检查处理中超时消息
            checkProcessingTimeout();

            // 4. 检查DLQ中的消息
            checkDlqMessages();

            // 5. 输出统计
            logStats();

            log.info("[IM-RECONCILIATION] Reconciliation completed");

        } catch (Exception e) {
            log.error("[IM-RECONCILIATION] Error during reconciliation", e);
        }
    }

    /**
     * 检查待发送超时消息
     * 状态：RECEIVED_FROM_CLIENT, PERSISTED, SENDING_TO_SQS
     */
    @Transactional
    public void checkPendingTimeout() {
        LocalDateTime timeoutTime = LocalDateTime.now().minusMinutes(pendingTimeoutMinutes);

        List<ImMessagePersistence.ImMessageStatus> pendingStatuses = List.of(
                ImMessagePersistence.ImMessageStatus.RECEIVED_FROM_CLIENT,
                ImMessagePersistence.ImMessageStatus.PERSISTED,
                ImMessagePersistence.ImMessageStatus.SENDING_TO_SQS
        );

        List<ImMessagePersistence> timeoutMessages = messageRepository.findRetryableMessages(
                pendingStatuses, timeoutTime);

        if (!timeoutMessages.isEmpty()) {
            log.warn("[IM-RECONCILIATION] Found {} pending timeout messages", timeoutMessages.size());

            for (ImMessagePersistence msg : timeoutMessages) {
                retryOrMarkLost(msg, "Pending timeout");
            }
        }

        incrementStats("pendingTimeout", timeoutMessages.size());
    }

    /**
     * 检查已发送但未确认超时消息
     * 状态：SENT_TO_SQS, RECEIVED_FROM_SQS
     */
    @Transactional
    public void checkSentTimeout() {
        LocalDateTime timeoutTime = LocalDateTime.now().minusMinutes(sentTimeoutMinutes);

        List<ImMessagePersistence.ImMessageStatus> sentStatuses = List.of(
                ImMessagePersistence.ImMessageStatus.SENT_TO_SQS,
                ImMessagePersistence.ImMessageStatus.RECEIVED_FROM_SQS
        );

        List<ImMessagePersistence> timeoutMessages = messageRepository.findRetryableMessages(
                sentStatuses, timeoutTime);

        if (!timeoutMessages.isEmpty()) {
            log.warn("[IM-RECONCILIATION] Found {} sent timeout messages", timeoutMessages.size());

            for (ImMessagePersistence msg : timeoutMessages) {
                retryOrMarkLost(msg, "Sent timeout - not acknowledged");
            }
        }

        incrementStats("sentTimeout", timeoutMessages.size());
    }

    /**
     * 检查处理中超时消息
     * 状态：PROCESSING
     */
    @Transactional
    public void checkProcessingTimeout() {
        LocalDateTime timeoutTime = LocalDateTime.now().minusMinutes(processingTimeoutMinutes);

        List<ImMessagePersistence> timeoutMessages = messageRepository.findRetryableMessages(
                List.of(ImMessagePersistence.ImMessageStatus.PROCESSING), timeoutTime);

        if (!timeoutMessages.isEmpty()) {
            log.warn("[IM-RECONCILIATION] Found {} processing timeout messages", timeoutMessages.size());

            for (ImMessagePersistence msg : timeoutMessages) {
                // 处理超时可能是因为消费者崩溃
                // 需要特别处理，可能需要人工干预
                handleProcessingTimeout(msg);
            }
        }

        incrementStats("processingTimeout", timeoutMessages.size());
    }

    /**
     * 检查DLQ中的消息
     */
    @Transactional
    public void checkDlqMessages() {
        List<ImMessagePersistence> dlqMessages = messageRepository.findMessagesInDlq(
                ImMessagePersistence.ImMessageStatus.IN_DLQ);

        if (!dlqMessages.isEmpty()) {
            log.warn("[IM-RECONCILIATION] Found {} messages in DLQ", dlqMessages.size());

            for (ImMessagePersistence msg : dlqMessages) {
                // 检查是否可以重试
                if (msg.canRetry()) {
                    retryMessageFromDlq(msg);
                } else {
                    // 标记为需要人工干预
                    markForManualIntervention(msg);
                }
            }
        }

        incrementStats("dlqMessages", dlqMessages.size());
    }

    /**
     * 手动触发对账
     */
    @Transactional
    public ReconciliationResult manualReconciliation() {
        log.info("[IM-RECONCILIATION] Manual reconciliation triggered");

        ReconciliationResult result = new ReconciliationResult();

        try {
            // 执行所有检查
            checkPendingTimeout();
            checkSentTimeout();
            checkProcessingTimeout();
            checkDlqMessages();

            result.setSuccess(true);
            result.setMessage("Manual reconciliation completed");
            result.setStats(getCurrentStats());

        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Manual reconciliation failed: " + e.getMessage());
            log.error("[IM-RECONCILIATION] Manual reconciliation failed", e);
        }

        return result;
    }

    /**
     * 获取对账统计
     */
    public ReconciliationStats getReconciliationStats() {
        long receivedCount = messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.RECEIVED_FROM_CLIENT);
        long persistedCount = messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.PERSISTED);
        long sendingCount = messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.SENDING_TO_SQS);
        long sentCount = messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.SENT_TO_SQS);
        long processingCount = messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.PROCESSING);
        long processedCount = messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.PROCESSED);
        long acknowledgedCount = messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED);
        long failedCount = messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.PROCESSING_FAILED);
        long dlqCount = messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.IN_DLQ);
        long lostCount = messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.LOST);

        return ReconciliationStats.builder()
                .receivedCount(receivedCount)
                .persistedCount(persistedCount)
                .sendingCount(sendingCount)
                .sentCount(sentCount)
                .processingCount(processingCount)
                .processedCount(processedCount)
                .acknowledgedCount(acknowledgedCount)
                .failedCount(failedCount)
                .dlqCount(dlqCount)
                .lostCount(lostCount)
                .totalMessages(receivedCount + persistedCount + sendingCount + sentCount +
                        processingCount + processedCount + acknowledgedCount + failedCount + dlqCount + lostCount)
                .build();
    }

    // ============== 私有方法 ==============

    private void retryOrMarkLost(ImMessagePersistence msg, String reason) {
        if (msg.canRetry()) {
            retryMessage(msg);
        } else {
            markAsLost(msg, reason);
        }
    }

    private void retryMessage(ImMessagePersistence msg) {
        try {
            log.info("[IM-RECONCILIATION] Retrying message: messageId={}, retryCount={}",
                    msg.getMessageId(), msg.getRetryCount());

            // 解析原始消息
            ImMessage message = objectMapper.readValue(msg.getPayload(), ImMessage.class);

            // 重新发送
            messageProducer.sendImMessage(message);

            // 更新重试计数
            msg.setRetryCount(msg.getRetryCount() + 1);
            msg.setUpdatedAt(LocalDateTime.now());
            messageRepository.save(msg);

            incrementStats("retried", 1);

        } catch (Exception e) {
            log.error("[IM-RECONCILIATION] Failed to retry message: messageId={}", msg.getMessageId(), e);
            incrementStats("retryFailed", 1);
        }
    }

    private void retryMessageFromDlq(ImMessagePersistence msg) {
        try {
            log.info("[IM-RECONCILIATION] Retrying DLQ message: messageId={}", msg.getMessageId());

            // 解析并重新发送
            ImMessage message = objectMapper.readValue(msg.getPayload(), ImMessage.class);
            messageProducer.sendImMessage(message);

            // 更新状态
            msg.setInDlq(false);
            msg.setStatus(ImMessagePersistence.ImMessageStatus.PERSISTED);
            msg.setRetryCount(msg.getRetryCount() + 1);
            msg.setUpdatedAt(LocalDateTime.now());
            messageRepository.save(msg);

            incrementStats("dlqRetried", 1);

        } catch (Exception e) {
            log.error("[IM-RECONCILIATION] Failed to retry DLQ message: messageId={}", msg.getMessageId(), e);
            markForManualIntervention(msg);
        }
    }

    private void handleProcessingTimeout(ImMessagePersistence msg) {
        // 处理超时比较特殊，可能数据已经部分处理
        // 需要根据业务逻辑判断是否可以重试
        if (msg.canRetry()) {
            log.warn("[IM-RECONCILIATION] Processing timeout, will retry: messageId={}", msg.getMessageId());
            retryMessage(msg);
        } else {
            // 标记为需要人工干预
            markForManualIntervention(msg);
        }
    }

    private void markAsLost(ImMessagePersistence msg, String reason) {
        msg.setStatus(ImMessagePersistence.ImMessageStatus.LOST);
        msg.setErrorMessage("LOST: " + reason);
        msg.setManualIntervention(true);
        msg.setUpdatedAt(LocalDateTime.now());
        messageRepository.save(msg);

        log.error("[IM-RECONCILIATION] Message marked as LOST: messageId={}, reason={}",
                msg.getMessageId(), reason);

        incrementStats("lost", 1);

        // TODO: 发送告警通知
    }

    private void markForManualIntervention(ImMessagePersistence msg) {
        msg.setManualIntervention(true);
        msg.setUpdatedAt(LocalDateTime.now());
        messageRepository.save(msg);

        log.warn("[IM-RECONCILIATION] Message marked for manual intervention: messageId={}",
                msg.getMessageId());

        incrementStats("manualIntervention", 1);

        // TODO: 发送告警通知
    }

    private void resetStats() {
        reconciliationStats.clear();
    }

    private void incrementStats(String key, int count) {
        reconciliationStats.computeIfAbsent(key, k -> new AtomicInteger(0)).addAndGet(count);
    }

    private void logStats() {
        log.info("[IM-RECONCILIATION] Stats: {}", reconciliationStats);
    }

    private Map<String, Integer> getCurrentStats() {
        return Map.of(
                "pendingTimeout", reconciliationStats.getOrDefault("pendingTimeout", new AtomicInteger(0)).get(),
                "sentTimeout", reconciliationStats.getOrDefault("sentTimeout", new AtomicInteger(0)).get(),
                "processingTimeout", reconciliationStats.getOrDefault("processingTimeout", new AtomicInteger(0)).get(),
                "dlqMessages", reconciliationStats.getOrDefault("dlqMessages", new AtomicInteger(0)).get(),
                "retried", reconciliationStats.getOrDefault("retried", new AtomicInteger(0)).get(),
                "lost", reconciliationStats.getOrDefault("lost", new AtomicInteger(0)).get(),
                "manualIntervention", reconciliationStats.getOrDefault("manualIntervention", new AtomicInteger(0)).get()
        );
    }

    // ============== 内部类 ==============

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReconciliationResult {
        private boolean success;
        private String message;
        private Map<String, Integer> stats;
    }

    @lombok.Data
    @lombok.Builder
    public static class ReconciliationStats {
        private long totalMessages;
        private long receivedCount;
        private long persistedCount;
        private long sendingCount;
        private long sentCount;
        private long processingCount;
        private long processedCount;
        private long acknowledgedCount;
        private long failedCount;
        private long dlqCount;
        private long lostCount;

        public double getSuccessRate() {
            if (totalMessages == 0) return 100.0;
            return (acknowledgedCount * 100.0) / totalMessages;
        }

        public double getLossRate() {
            if (totalMessages == 0) return 0.0;
            return (lostCount * 100.0) / totalMessages;
        }
    }
}
