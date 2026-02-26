package com.example.awssqs.dlq;

import com.example.awssqs.domain.FailedMessage;
import com.example.awssqs.domain.FailedMessage.FailedMessageStatus;
import com.example.awssqs.domain.FailedMessage.FailureReason;
import com.example.awssqs.producer.MessagePersistenceService;
import com.example.awssqs.producer.MessageProducer;
import com.example.awssqs.repository.FailedMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 死信队列(DLQ)服务
 * 负责DLQ消息的记录、监控、重试和人工处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterQueueService {

    private final FailedMessageRepository failedMessageRepository;
    private final MessageProducer messageProducer;
    private final MessagePersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRY_COUNT = 5;
    private static final int RETRY_DELAY_MINUTES = 5;

    /**
     * 记录进入DLQ的消息
     */
    @Transactional
    public void recordDeadLetterMessage(String messageId, String originalQueue,
                                       String payload, String errorMessage) {
        try {
            String payloadType = "Unknown";
            try {
                // 尝试解析payload获取类型
                if (payload.startsWith("{")) {
                    var node = objectMapper.readTree(payload);
                    if (node.has("ticketId")) {
                        payloadType = "TicketCreatedEvent";
                    } else if (node.has("eventType")) {
                        payloadType = node.get("eventType").asText();
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse payload type", e);
            }

            FailedMessage failedMessage = FailedMessage.builder()
                    .messageId(messageId)
                    .queueName(originalQueue)
                    .payloadType(payloadType)
                    .payload(payload)
                    .failedAt(LocalDateTime.now())
                    .receiveCount(1)
                    .status(FailedMessageStatus.PENDING)
                    .errorMessage(errorMessage)
                    .failureReason(FailureReason.PROCESSING_EXCEPTION)
                    .retryCount(0)
                    .build();

            failedMessageRepository.save(failedMessage);

            log.warn("DLQ message recorded: messageId={}, queue={}, error={}",
                    messageId, originalQueue, errorMessage);

        } catch (Exception e) {
            log.error("Failed to record DLQ message: messageId={}", messageId, e);
        }
    }

    /**
     * 自动重试待处理的失败消息
     * 每5分钟执行一次
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @Transactional
    public void retryPendingMessages() {
        log.info("Starting DLQ retry process");

        try {
            // 查找可以重试的消息
            List<FailedMessage> messages = failedMessageRepository.findPendingMessagesForRetry(MAX_RETRY_COUNT);

            log.info("Found {} messages pending retry", messages.size());

            for (FailedMessage msg : messages) {
                retryMessage(msg);
            }

        } catch (Exception e) {
            log.error("Error during DLQ retry process", e);
        }
    }

    /**
     * 重试单条消息
     */
    @Transactional
    public void retryMessage(FailedMessage failedMessage) {
        try {
            log.info("Retrying message: messageId={}, retryCount={}",
                    failedMessage.getMessageId(), failedMessage.getRetryCount());

            // 更新状态为正在重试
            failedMessage.setStatus(FailedMessageStatus.RETRYING);
            failedMessage.setRetryCount(failedMessage.getRetryCount() + 1);
            failedMessage.setLastRetryAt(LocalDateTime.now());
            failedMessageRepository.save(failedMessage);

            // 解析payload并重新发送
            Object payload = parsePayload(failedMessage.getPayload(), failedMessage.getPayloadType());

            // 获取原始队列URL
            String queueUrl = getQueueUrl(failedMessage.getQueueName());

            // 重新发送消息
            String newMessageId = messageProducer.sendToStandardQueue(
                    queueUrl,
                    payload,
                    failedMessage.getTenantId(),
                    failedMessage.getAggregateId(),
                    failedMessage.getAggregateType()
            );

            // 标记重试成功
            failedMessage.setStatus(FailedMessageStatus.RESOLVED);
            failedMessage.setResolvedAt(LocalDateTime.now());
            failedMessage.setHandlingNotes("Auto-retried successfully. New messageId: " + newMessageId);
            failedMessageRepository.save(failedMessage);

            log.info("Message retry successful: messageId={}, newMessageId={}",
                    failedMessage.getMessageId(), newMessageId);

        } catch (Exception e) {
            log.error("Failed to retry message: messageId={}, error={}",
                    failedMessage.getMessageId(), e.getMessage(), e);

            // 重试失败，检查是否达到最大重试次数
            if (failedMessage.getRetryCount() >= MAX_RETRY_COUNT) {
                failedMessage.setStatus(FailedMessageStatus.MANUAL);
                failedMessage.setMovedToManualAt(LocalDateTime.now());
                failedMessage.setHandlingNotes("Max retry count reached, requiring manual intervention");
                log.warn("Message moved to manual intervention: messageId={}", failedMessage.getMessageId());
            } else {
                failedMessage.setStatus(FailedMessageStatus.PENDING);
            }

            failedMessageRepository.save(failedMessage);
        }
    }

    /**
     * 检查超时的PENDING消息
     * 每小时执行一次
     */
    @Scheduled(fixedDelay = 60 * 60 * 1000)
    @Transactional
    public void checkTimeoutMessages() {
        log.info("Checking for timeout DLQ messages");

        LocalDateTime timeoutTime = LocalDateTime.now().minusHours(1);
        List<FailedMessage> timeoutMessages = failedMessageRepository.findTimeoutPendingMessages(timeoutTime);

        log.info("Found {} timeout messages in DLQ", timeoutMessages.size());

        for (FailedMessage msg : timeoutMessages) {
            log.warn("Timeout DLQ message: messageId={}, failedAt={}",
                    msg.getMessageId(), msg.getFailedAt());

            // 标记为需要人工处理
            msg.setStatus(FailedMessageStatus.MANUAL);
            msg.setMovedToManualAt(LocalDateTime.now());
            msg.setHandlingNotes("Timeout after being in PENDING status for too long");
            failedMessageRepository.save(msg);

            // 发送通知（邮件/钉钉/Slack等）
            sendManualInterventionAlert(msg);
        }
    }

    /**
     * 手动处理消息
     */
    @Transactional
    public void handleManually(String messageId, String handledBy, String notes, boolean retry) {
        FailedMessage msg = failedMessageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        msg.setHandledBy(handledBy);
        msg.setHandlingNotes(notes);

        if (retry) {
            // 重新发送
            retryMessage(msg);
        } else {
            // 标记为已解决（不重试）
            msg.setStatus(FailedMessageStatus.RESOLVED);
            msg.setResolvedAt(LocalDateTime.now());
            failedMessageRepository.save(msg);
        }

        log.info("Message handled manually: messageId={}, handledBy={}, retry={}",
                messageId, handledBy, retry);
    }

    /**
     * 忽略消息（不再处理）
     */
    @Transactional
    public void ignoreMessage(String messageId, String handledBy, String reason) {
        FailedMessage msg = failedMessageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        msg.setStatus(FailedMessageStatus.IGNORED);
        msg.setHandledBy(handledBy);
        msg.setHandlingNotes(reason);
        failedMessageRepository.save(msg);

        log.info("Message ignored: messageId={}, reason={}", messageId, reason);
    }

    /**
     * 获取需要人工处理的消息列表
     */
    public List<FailedMessage> getMessagesForManualIntervention() {
        return failedMessageRepository.findByStatusOrderByFailedAtDesc(FailedMessageStatus.MANUAL);
    }

    /**
     * 发送人工干预告警
     */
    private void sendManualInterventionAlert(FailedMessage message) {
        // TODO: 实现告警发送逻辑
        // 可以发送邮件、钉钉、Slack、企业微信等
        log.warn("Manual intervention required for DLQ message: messageId={}, queue={}, error={}",
                message.getMessageId(), message.getQueueName(), message.getErrorMessage());
    }

    /**
     * 解析payload
     */
    private Object parsePayload(String payload, String payloadType) {
        try {
            return objectMapper.readValue(payload, Object.class);
        } catch (Exception e) {
            // 返回原始字符串
            return payload;
        }
    }

    /**
     * 获取队列URL（简化版）
     */
    private String getQueueUrl(String queueName) {
        // 实际实现应该从配置或通过AWS SDK获取
        return "https://sqs.region.amazonaws.com/account-id/" + queueName;
    }
}
