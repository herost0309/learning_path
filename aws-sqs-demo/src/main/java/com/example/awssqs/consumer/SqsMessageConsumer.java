package com.example.awssqs.consumer;

import com.example.awssqs.domain.DomainEvent;
import com.example.awssqs.domain.MessagePersistence.MessageStatus;
import com.example.awssqs.domain.TicketCreatedEvent;
import com.example.awssqs.producer.MessagePersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * SQS消息消费者
 * 实现手动确认模式，确保消息可靠处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqsMessageConsumer {

    private final MessagePersistenceService persistenceService;
    private final ObjectMapper objectMapper;
    private final MessageHandler messageHandler;

    /**
     * 监听工单队列
     * 手动确认模式: 需要显式调用acknowledgement.acknowledge()
     */
    @SqsListener(value = "${sqs.queue.ticket}", factory = "sqsListenerContainerFactory")
    public void handleTicketEvent(String messageBody,
                                 @Header(value = "InternalMessageId", required = false) String internalMessageId,
                                 @Header(value = "ApproximateReceiveCount", required = false) Integer receiveCount,
                                 Acknowledgement acknowledgement) {

        long startTime = Instant.now().toEpochMilli();
        String messageId = internalMessageId != null ? internalMessageId : extractMessageId(messageBody);

        log.info("Received ticket event: messageId={}, receiveCount={}", messageId, receiveCount);

        try {
            // 1. 更新状态为已接收
            persistenceService.markAsReceived(messageId);

            // 2. 更新状态为处理中
            persistenceService.markAsProcessing(messageId);

            // 3. 解析并处理消息
            TicketCreatedEvent event = objectMapper.readValue(messageBody, TicketCreatedEvent.class);
            messageHandler.handleTicketCreated(event);

            // 4. 处理成功，更新状态为已处理
            persistenceService.markAsProcessed(messageId);

            // 5. 手动确认删除消息
            if (acknowledgement != null) {
                acknowledgement.acknowledge();
            }

            // 6. 更新状态为已确认
            persistenceService.markAsAcknowledged(messageId);

            long processingTime = Instant.now().toEpochMilli() - startTime;
            log.info("Ticket event processed successfully: messageId={}, processingTime={}ms",
                    messageId, processingTime);

        } catch (Exception e) {
            long processingTime = Instant.now().toEpochMilli() - startTime;
            log.error("Failed to process ticket event: messageId={}, error={}, processingTime={}ms",
                    messageId, e.getMessage(), processingTime, e);

            // 标记消息处理失败
            persistenceService.markAsFailed(messageId, e.getMessage());

            // 不确认消息，让它返回队列以便重试
            // 或者根据重试次数决定是否移到DLQ
            if (receiveCount != null && receiveCount >= 3) {
                log.warn("Message exceeded max retry count, will go to DLQ: messageId={}, receiveCount={}",
                        messageId, receiveCount);
            }

            throw new RuntimeException("Message processing failed", e);
        }
    }

    /**
     * 监听事件队列（使用消息头中的消息类型动态处理）
     */
    @SqsListener(value = "${sqs.queue.events}", factory = "sqsListenerContainerFactory")
    public void handleDomainEvent(String messageBody,
                                   @Header(value = "MessageType") String messageType,
                                   @Header(value = "InternalMessageId") String internalMessageId,
                                   @Header(value = "ApproximateReceiveCount") Integer receiveCount,
                                   Acknowledgement acknowledgement) {

        log.info("Received domain event: messageType={}, messageId={}", messageType, internalMessageId);

        try {
            persistenceService.markAsReceived(internalMessageId);
            persistenceService.markAsProcessing(internalMessageId);

            // 根据消息类型路由到不同的处理器
            switch (messageType) {
                case "TicketCreatedEvent":
                    TicketCreatedEvent ticketEvent = objectMapper.readValue(messageBody, TicketCreatedEvent.class);
                    messageHandler.handleTicketCreated(ticketEvent);
                    break;

                case "MessageProcessedEvent":
                    DomainEvent processedEvent = objectMapper.readValue(messageBody, DomainEvent.class);
                    messageHandler.handleMessageProcessed(processedEvent);
                    break;

                case "MessageFailedEvent":
                    DomainEvent failedEvent = objectMapper.readValue(messageBody, DomainEvent.class);
                    messageHandler.handleMessageFailed(failedEvent);
                    break;

                default:
                    log.warn("Unknown message type: {}", messageType);
                    // 仍然确认消息，避免堆积
                    if (acknowledgement != null) {
                        acknowledgement.acknowledge();
                    }
                    persistenceService.markAsAcknowledged(internalMessageId);
                    return;
            }

            persistenceService.markAsProcessed(internalMessageId);

            if (acknowledgement != null) {
                acknowledgement.acknowledge();
            }

            persistenceService.markAsAcknowledged(internalMessageId);

            log.info("Domain event processed: messageType={}", messageType);

        } catch (Exception e) {
            log.error("Failed to process domain event: messageType={}, error={}",
                    messageType, e.getMessage(), e);

            persistenceService.markAsFailed(internalMessageId, e.getMessage());

            if (receiveCount != null && receiveCount >= 3) {
                log.warn("Event exceeded max retry count: messageType={}", messageType);
            }

            throw new RuntimeException("Event processing failed", e);
        }
    }

    /**
     * 监听DLQ（死信队列）
     * 失败消息进入DLQ后的处理
     */
    @SqsListener(value = "${sqs.dlq.name}", factory = "sqsListenerContainerFactory")
    public void handleDeadLetterMessage(String messageBody,
                                         @Header(value = "InternalMessageId") String internalMessageId,
                                         @Header(value = "OriginalQueue") String originalQueue,
                                         Acknowledgement acknowledgement) {

        log.warn("Received DLQ message: messageId={}, originalQueue={}", internalMessageId, originalQueue);

        try {
            // 标记消息进入DLQ
            persistenceService.markAsInDlq(internalMessageId, 1);

            // 确认删除DLQ中的消息（已记录到数据库）
            if (acknowledgement != null) {
                acknowledgement.acknowledge();
            }

            log.info("DLQ message recorded: messageId={}", internalMessageId);

        } catch (Exception e) {
            log.error("Failed to process DLQ message: messageId={}, error={}",
                    internalMessageId, e.getMessage(), e);
        }
    }

    /**
     * 带可见性超时扩展的消费者
     * 适用于长时间运行的任务
     */
    @SqsListener(value = "${sqs.queue.long-task}", factory = "sqsListenerContainerFactory")
    public void handleLongTask(String messageBody,
                              @Header(value = "InternalMessageId") String internalMessageId,
                              Acknowledgement acknowledgement) {

        log.info("Received long task: messageId={}", internalMessageId);

        try {
            persistenceService.markAsReceived(internalMessageId);
            persistenceService.markAsProcessing(internalMessageId);

            // 执行长时间任务
            longTaskProcessor(messageBody, internalMessageId);

            persistenceService.markAsProcessed(internalMessageId);

            if (acknowledgement != null) {
                acknowledgement.acknowledge();
            }

            persistenceService.markAsAcknowledged(internalMessageId);

            log.info("Long task completed: messageId={}", internalMessageId);

        } catch (Exception e) {
            log.error("Long task failed: messageId={}, error={}", internalMessageId, e.getMessage(), e);
            persistenceService.markAsFailed(internalMessageId, e.getMessage());
            throw new RuntimeException("Long task failed", e);
        }
    }

    /**
     * 消息批处理消费者
     * 一次性处理多条消息
     */
    @SqsListener(value = "${sqs.queue.batch}", factory = "sqsListenerContainerFactory",
               maxMessagesPerPoll = "10")
    public void handleBatchMessages(java.util.List<String> messages,
                                     @Header(value = "InternalMessageId", required = false) String internalMessageId,
                                     Acknowledgement acknowledgement) {

        log.info("Received batch messages: count={}", messages.size());

        try {
            for (String message : messages) {
                String msgId = extractMessageId(message);
                persistenceService.markAsReceived(msgId);
                persistenceService.markAsProcessing(msgId);

                // 处理单条消息
                messageHandler.processGenericMessage(message);

                persistenceService.markAsProcessed(msgId);
            }

            // 批量确认
            if (acknowledgement != null) {
                acknowledgement.acknowledge();
            }

            log.info("Batch messages processed: count={}", messages.size());

        } catch (Exception e) {
            log.error("Batch processing failed: error={}", e.getMessage(), e);
            throw new RuntimeException("Batch processing failed", e);
        }
    }

    /**
     * 可靠性消费者模式
     * 实现幂等性和补偿机制
     */
    @SqsListener(value = "${sqs.queue.reliable}", factory = "sqsListenerContainerFactory")
    public void handleReliably(String messageBody,
                               @Header(value = "InternalMessageId") String internalMessageId,
                               @Header(value = "ApproximateReceiveCount") Integer receiveCount,
                               Acknowledgement acknowledgement) {

        log.debug("Processing message reliably: messageId={}", internalMessageId);

        try {
            // 1. 幂等性检查
            if (messageHandler.isAlreadyProcessed(internalMessageId)) {
                log.info("Message already processed (idempotent), acknowledging: messageId={}", internalMessageId);
                if (acknowledgement != null) {
                    acknowledgement.acknowledge();
                }
                persistenceService.markAsAcknowledged(internalMessageId);
                return;
            }

            // 2. 标记开始处理
            persistenceService.markAsReceived(internalMessageId);
            persistenceService.markAsProcessing(internalMessageId);

            // 3. 处理消息
            messageHandler.processGenericMessage(messageBody);

            // 4. 记录处理结果（用于幂等性）
            messageHandler.markAsProcessed(internalMessageId);

            // 5. 更新状态
            persistenceService.markAsProcessed(internalMessageId);

            // 6. 确认删除
            if (acknowledgement != null) {
                acknowledgement.acknowledge();
            }

            persistenceService.markAsAcknowledged(internalMessageId);

        } catch (Exception e) {
            log.error("Reliable processing failed: messageId={}, receiveCount={}, error={}",
                    internalMessageId, receiveCount, e.getMessage(), e);

            // 补偿操作：回滚或记录状态
            messageHandler.compensate(internalMessageId, e);

            persistenceService.markAsFailed(internalMessageId, e.getMessage());

            // 根据重试次数决定是否确认
            if (receiveCount != null && receiveCount >= 5) {
                log.warn("Max retries reached, acknowledging to prevent infinite loop: messageId={}", internalMessageId);
                if (acknowledgement != null) {
                    acknowledgement.acknowledge();
                }
            }

            throw new RuntimeException("Processing failed", e);
        }
    }

    private void longTaskProcessor(String messageBody, String messageId) throws InterruptedException {
        // 模拟长时间任务
        Thread.sleep(5000);
        log.debug("Long task processing completed for: messageId={}", messageId);
    }

    private String extractMessageId(String messageBody) {
        // 尝试从JSON中提取messageId
        try {
            Map<?, ?> map = objectMapper.readValue(messageBody, Map.class);
            Object eventId = map.get("eventId");
            return eventId != null ? eventId.toString() : "unknown";
        } catch (Exception e) {
            return "unknown-" + System.currentTimeMillis();
        }
    }
}
