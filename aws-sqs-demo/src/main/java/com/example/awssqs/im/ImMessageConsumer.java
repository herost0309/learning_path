package com.example.awssqs.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * IM消息消费者服务
 * 实现可靠消费的核心机制：
 * 1. 手动确认模式
 * 2. 幂等性处理
 * 3. 可见性超时管理
 * 4. 失败处理和重试
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImMessageConsumer {

    private final ImMessageRepository messageRepository;
    private final ImMessageProducer messageProducer;
    private final ImMessageDeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    /**
     * 消费IM消息（标准队列）
     */
    @SqsListener(value = "${aws.sqs.im-queue-url}", factory = "sqsListenerContainerFactory")
    @Transactional
    public void consumeImMessage(String messageBody,
                                  @Header(value = "MessageId", required = false) String messageId,
                                  @Header(value = "ClientMessageId", required = false) String clientMessageId,
                                  @Header(value = "ConversationId", required = false) String conversationId,
                                  @Header(value = "SenderId", required = false) String senderId,
                                  @Header(value = "ReceiverId", required = false) String receiverId,
                                  @Header(value = "MessageType", required = false) String messageType,
                                  @Header(value = "ApproximateReceiveCount", required = false) Integer receiveCount,
                                  @Header(value = "ApproximateFirstReceiveTimestamp", required = false) Long firstReceiveTime,
                                  Acknowledgement acknowledgement) {

        long startTime = System.currentTimeMillis();
        String extractedMessageId = messageId != null ? messageId : extractMessageId(messageBody);

        log.info("[IM-CONSUMER] Received message: messageId={}, conversationId={}, receiveCount={}",
                extractedMessageId, conversationId, receiveCount);

        try {
            // 1. 解析消息
            ImMessage message = objectMapper.readValue(messageBody, ImMessage.class);

            // 2. 幂等性检查
            if (isAlreadyProcessed(message)) {
                log.info("[IM-CONSUMER] Message already processed (idempotent): messageId={}", extractedMessageId);
                // 确认并跳过
                if (acknowledgement != null) {
                    acknowledgement.acknowledge();
                }
                return;
            }

            // 3. 检查消息是否过期
            if (message.isExpired()) {
                log.warn("[IM-CONSUMER] Message expired: messageId={}", extractedMessageId);
                markMessageExpired(extractedMessageId);
                if (acknowledgement != null) {
                    acknowledgement.acknowledge();
                }
                return;
            }

            // 4. 更新状态为从SQS接收
            updateMessageStatus(extractedMessageId, ImMessagePersistence.ImMessageStatus.RECEIVED_FROM_SQS);

            // 5. 更新状态为处理中
            updateMessageStatus(extractedMessageId, ImMessagePersistence.ImMessageStatus.PROCESSING);

            // 6. 处理消息（投递给接收方）
            processImMessage(message);

            // 7. 更新状态为处理完成
            updateMessageStatus(extractedMessageId, ImMessagePersistence.ImMessageStatus.PROCESSED);

            // 8. 确认消息（从SQS删除）
            if (acknowledgement != null) {
                acknowledgement.acknowledge();
            }

            // 9. 通知生产者消息已处理
            messageProducer.confirmMessageProcessed(extractedMessageId);

            // 10. 更新最终状态为已确认
            updateMessageStatus(extractedMessageId, ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED);

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("[IM-CONSUMER] Message processed successfully: messageId={}, time={}ms",
                    extractedMessageId, processingTime);

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("[IM-CONSUMER] Failed to process message: messageId={}, time={}ms, error={}",
                    extractedMessageId, processingTime, e.getMessage(), e);

            // 处理失败
            handleConsumeFailure(extractedMessageId, receiveCount, e);

            // 根据重试次数决定是否确认
            if (receiveCount != null && receiveCount >= 5) {
                // 达到最大重试次数，确认消息（让它进入DLQ）
                if (acknowledgement != null) {
                    acknowledgement.acknowledge();
                }
                markMessageInDlq(extractedMessageId);
            }
            // 否则不确认，让消息重新回到队列
        }
    }

    /**
     * 消费IM消息（FIFO队列 - 保证顺序）
     */
    @SqsListener(value = "${aws.sqs.im-queue-fifo-url:}", factory = "sqsListenerContainerFactory")
    @Transactional
    public void consumeFifoImMessage(String messageBody,
                                      @Header(value = "MessageId", required = false) String messageId,
                                      @Header(value = "MessageGroupId", required = false) String messageGroupId,
                                      @Header(value = "MessageDeduplicationId", required = false) String deduplicationId,
                                      @Header(value = "ApproximateReceiveCount", required = false) Integer receiveCount,
                                      Acknowledgement acknowledgement) {

        log.info("[IM-CONSUMER-FIFO] Received FIFO message: messageId={}, groupId={}, dedupId={}",
                messageId, messageGroupId, deduplicationId);

        // FIFO队列的处理逻辑类似，但需要更严格地保证顺序
        consumeImMessage(messageBody, messageId, deduplicationId, null, null, null,
                null, receiveCount, null, acknowledgement);
    }

    /**
     * 处理IM消息的核心逻辑
     */
    private void processImMessage(ImMessage message) {
        switch (message.getMessageType()) {
            case TEXT:
            case IMAGE:
            case VIDEO:
            case AUDIO:
            case FILE:
                // 投递普通消息给接收方
                deliveryService.deliverToReceiver(message);
                break;

            case SYSTEM:
                // 系统消息处理
                deliveryService.deliverSystemMessage(message);
                break;

            case RECALL:
                // 撤回消息处理
                deliveryService.handleRecallMessage(message);
                break;

            case TYPING:
                // 输入状态（实时推送，不持久化）
                deliveryService.pushTypingStatus(message);
                break;

            default:
                log.warn("[IM-CONSUMER] Unknown message type: {}", message.getMessageType());
        }
    }

    /**
     * 幂等性检查
     */
    private boolean isAlreadyProcessed(ImMessage message) {
        // 检查消息是否已经被处理过
        Optional<ImMessagePersistence> opt;
        if (message.getClientMessageId() != null) {
            opt = messageRepository.findByClientMessageId(message.getClientMessageId());
        } else {
            opt = messageRepository.findByMessageId(message.getMessageId());
        }

        if (opt.isEmpty()) {
            return false;
        }

        ImMessagePersistence persistence = opt.get();

        // 如果消息已经处于终态，则认为已处理
        return persistence.isTerminalState() ||
               persistence.getStatus() == ImMessagePersistence.ImMessageStatus.PROCESSED ||
               persistence.getStatus() == ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED;
    }

    /**
     * 更新消息状态
     */
    private void updateMessageStatus(String messageId, ImMessagePersistence.ImMessageStatus newStatus) {
        Optional<ImMessagePersistence> opt = messageRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            ImMessagePersistence persistence = opt.get();
            persistence.setStatus(newStatus);
            persistence.setUpdatedAt(LocalDateTime.now());

            // 设置特定时间戳
            switch (newStatus) {
                case RECEIVED_FROM_SQS:
                    persistence.setReceivedAt(LocalDateTime.now());
                    break;
                case PROCESSING:
                    persistence.setProcessingStartedAt(LocalDateTime.now());
                    break;
                case PROCESSED:
                    persistence.setProcessedAt(LocalDateTime.now());
                    break;
                case ACKNOWLEDGED:
                    persistence.setDeliveredAt(LocalDateTime.now());
                    break;
                default:
                    break;
            }

            messageRepository.save(persistence);
        }
    }

    /**
     * 处理消费失败
     */
    private void handleConsumeFailure(String messageId, Integer receiveCount, Exception e) {
        Optional<ImMessagePersistence> opt = messageRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            ImMessagePersistence persistence = opt.get();
            persistence.setStatus(ImMessagePersistence.ImMessageStatus.PROCESSING_FAILED);
            persistence.setErrorMessage(e.getMessage());
            persistence.setErrorCode(e.getClass().getSimpleName());
            persistence.setUpdatedAt(LocalDateTime.now());

            if (receiveCount != null) {
                persistence.setRetryCount(receiveCount);
            }

            messageRepository.save(persistence);
        }
    }

    /**
     * 标记消息已过期
     */
    private void markMessageExpired(String messageId) {
        Optional<ImMessagePersistence> opt = messageRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            ImMessagePersistence persistence = opt.get();
            persistence.setStatus(ImMessagePersistence.ImMessageStatus.EXPIRED);
            persistence.setUpdatedAt(LocalDateTime.now());
            messageRepository.save(persistence);
        }
    }

    /**
     * 标记消息进入DLQ
     */
    private void markMessageInDlq(String messageId) {
        Optional<ImMessagePersistence> opt = messageRepository.findByMessageId(messageId);
        if (opt.isPresent()) {
            ImMessagePersistence persistence = opt.get();
            persistence.setStatus(ImMessagePersistence.ImMessageStatus.IN_DLQ);
            persistence.setInDlq(true);
            persistence.setDlqAt(LocalDateTime.now());
            persistence.setManualIntervention(true);
            persistence.setUpdatedAt(LocalDateTime.now());
            messageRepository.save(persistence);

            log.warn("[IM-CONSUMER] Message moved to DLQ: messageId={}", messageId);
            // TODO: 发送告警通知
        }
    }

    /**
     * 从消息体提取消息ID
     */
    private String extractMessageId(String messageBody) {
        try {
            ImMessage msg = objectMapper.readValue(messageBody, ImMessage.class);
            return msg.getMessageId();
        } catch (Exception e) {
            return "unknown-" + System.currentTimeMillis();
        }
    }
}
