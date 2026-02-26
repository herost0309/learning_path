package com.example.awssqs.producer;

import com.example.awssqs.domain.MessagePersistence;
import com.example.awssqs.domain.MessagePersistence.MessageStatus;
import com.example.awssqs.repository.MessagePersistenceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * 消息持久化服务
 * 实现"先持久化，后发送"模式，防止消息丢失
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessagePersistenceService {

    private final MessagePersistenceRepository messagePersistenceRepository;
    private final ObjectMapper objectMapper;

    /**
     * 保存消息到数据库（发送前）
     * 状态初始为PENDING
     */
    @Transactional
    public MessagePersistence persistBeforeSend(String queueName, Object payload,
                                               Long tenantId, String aggregateId, String aggregateType) {
        try {
            String messageId = generateMessageId(queueName);
            String payloadJson = objectMapper.writeValueAsString(payload);
            String payloadType = payload.getClass().getSimpleName();
            Long messageSize = (long) payloadJson.getBytes(StandardCharsets.UTF_8).length;

            MessagePersistence persistence = MessagePersistence.builder()
                    .messageId(messageId)
                    .queueName(queueName)
                    .payloadType(payloadType)
                    .payload(payloadJson)
                    .status(MessageStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .tenantId(tenantId)
                    .aggregateId(aggregateId)
                    .aggregateType(aggregateType)
                    .retryCount(0)
                    .inDlq(false)
                    .dlqReceiveCount(0)
                    .manualIntervention(false)
                    .messageSize(messageSize)
                    .build();

            MessagePersistence saved = messagePersistenceRepository.save(persistence);
            log.info("Message persisted before send: messageId={}, queueName={}, payloadType={}",
                    messageId, queueName, payloadType);

            return saved;
        } catch (Exception e) {
            log.error("Failed to persist message before send", e);
            throw new RuntimeException("Failed to persist message", e);
        }
    }

    /**
     * 更新消息状态为已发送
     */
    @Transactional
    public void markAsSent(String messageId, String receiptHandle) {
        Optional<MessagePersistence> opt = messagePersistenceRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            MessagePersistence persistence = opt.get();
            persistence.setStatus(MessageStatus.SENT);
            persistence.setSentAt(LocalDateTime.now());
            persistence.setReceiptHandle(receiptHandle);
            messagePersistenceRepository.save(persistence);
            log.debug("Message marked as SENT: messageId={}", messageId);
        }
    }

    /**
     * 更新消息状态为已接收（消费者端）
     */
    @Transactional
    public void markAsReceived(String messageId) {
        Optional<MessagePersistence> opt = messagePersistenceRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            MessagePersistence persistence = opt.get();
            persistence.setStatus(MessageStatus.RECEIVED);
            persistence.setReceivedAt(LocalDateTime.now());
            messagePersistenceRepository.save(persistence);
            log.debug("Message marked as RECEIVED: messageId={}", messageId);
        }
    }

    /**
     * 更新消息状态为处理中
     */
    @Transactional
    public void markAsProcessing(String messageId) {
        Optional<MessagePersistence> opt = messagePersistenceRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            MessagePersistence persistence = opt.get();
            persistence.setStatus(MessageStatus.PROCESSING);
            messagePersistenceRepository.save(persistence);
            log.debug("Message marked as PROCESSING: messageId={}", messageId);
        }
    }

    /**
     * 更新消息状态为已处理
     */
    @Transactional
    public void markAsProcessed(String messageId) {
        Optional<MessagePersistence> opt = messagePersistenceRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            MessagePersistence persistence = opt.get();
            persistence.setStatus(MessageStatus.PROCESSED);
            persistence.setProcessedAt(LocalDateTime.now());
            messagePersistenceRepository.save(persistence);
            log.debug("Message marked as PROCESSED: messageId={}", messageId);
        }
    }

    /**
     * 更新消息状态为已确认（SQS删除后）
     */
    @Transactional
    public void markAsAcknowledged(String messageId) {
        Optional<MessagePersistence> opt = messagePersistenceRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            MessagePersistence persistence = opt.get();
            persistence.setStatus(MessageStatus.ACKNOWLEDGED);
            messagePersistenceRepository.save(persistence);
            log.debug("Message marked as ACKNOWLEDGED: messageId={}", messageId);
        }
    }

    /**
     * 更新消息状态为失败
     */
    @Transactional
    public void markAsFailed(String messageId, String errorMessage) {
        Optional<MessagePersistence> opt = messagePersistenceRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            MessagePersistence persistence = opt.get();
            persistence.setStatus(MessageStatus.FAILED);
            persistence.setFailedAt(LocalDateTime.now());
            persistence.setErrorMessage(errorMessage);
            messagePersistenceRepository.save(persistence);
            log.warn("Message marked as FAILED: messageId={}, error={}", messageId, errorMessage);
        }
    }

    /**
     * 标记消息进入DLQ
     */
    @Transactional
    public void markAsInDlq(String messageId, int receiveCount) {
        Optional<MessagePersistence> opt = messagePersistenceRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            MessagePersistence persistence = opt.get();
            persistence.setInDlq(true);
            persistence.setDlqReceiveCount(receiveCount);
            messagePersistenceRepository.save(persistence);
            log.warn("Message marked as in DLQ: messageId={}, receiveCount={}", messageId, receiveCount);
        }
    }

    /**
     * 标记消息需要人工干预
     */
    @Transactional
    public void markAsManualIntervention(String messageId) {
        Optional<MessagePersistence> opt = messagePersistenceRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            MessagePersistence persistence = opt.get();
            persistence.setManualIntervention(true);
            messagePersistenceRepository.save(persistence);
            log.warn("Message marked for manual intervention: messageId={}", messageId);
        }
    }

    /**
     * 标记消息已重放
     */
    @Transactional
    public void markAsReplayed(String messageId) {
        Optional<MessagePersistence> opt = messagePersistenceRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            MessagePersistence persistence = opt.get();
            persistence.setStatus(MessageStatus.REPLAYED);
            persistence.setReplayedAt(LocalDateTime.now());
            persistence.setRetryCount(persistence.getRetryCount() + 1);
            messagePersistenceRepository.save(persistence);
            log.info("Message marked as REPLAYED: messageId={}, retryCount={}",
                    messageId, persistence.getRetryCount());
        }
    }

    /**
     * 标记消息丢失（用于对账发现的消息）
     */
    @Transactional
    public void markAsLost(String messageId, String reason) {
        Optional<MessagePersistence> opt = messagePersistenceRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            MessagePersistence persistence = opt.get();
            persistence.setStatus(MessageStatus.LOST);
            persistence.setErrorMessage("LOST: " + reason);
            messagePersistenceRepository.save(persistence);
            log.error("Message marked as LOST: messageId={}, reason={}", messageId, reason);
        }
    }

    /**
     * 生成消息ID
     * 格式: {queueName}-{timestamp}-{random}
     */
    private String generateMessageId(String queueName) {
        long timestamp = System.currentTimeMillis();
        String random = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%s-%d-%s", queueName, timestamp, random);
    }

    /**
     * 检查消息是否重复
     * 使用幂等键进行去重
     */
    public boolean isDuplicate(String queueName, String deduplicationKey) {
        // 查找最近30分钟内相同deduplicationKey的消息
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
        return messagePersistenceRepository.findByMessageId(deduplicationKey).isPresent();
    }
}
