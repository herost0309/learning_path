package com.example.awssqs.consumer;

import com.example.awssqs.domain.DomainEvent;
import com.example.awssqs.domain.TicketCreatedEvent;
import com.example.awssqs.producer.MessagePersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 消息处理器
 * 实现具体的业务逻辑处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageHandler {

    private final MessagePersistenceService persistenceService;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String PROCESSED_MESSAGES_KEY = "sqs:processed:";
    private static final Duration PROCESSED_MESSAGES_TTL = Duration.ofHours(24);

    /**
     * 处理工单创建事件
     */
    public void handleTicketCreated(TicketCreatedEvent event) {
        log.info("Handling TicketCreatedEvent: ticketId={}, subject={}",
                event.getTicketId(), event.getSubject());

        try {
            // 1. 验证数据
            validateTicketEvent(event);

            // 2. 业务处理
            processTicket(event);

            // 3. 发布后置事件（如果有）
            // eventPublisher.publishPostEvent(...);

            log.info("TicketCreatedEvent processed successfully: ticketId={}", event.getTicketId());

        } catch (Exception e) {
            log.error("Failed to process TicketCreatedEvent: ticketId={}, error={}",
                    event.getTicketId(), e.getMessage(), e);
            throw new RuntimeException("Ticket processing failed", e);
        }
    }

    /**
     * 处理消息处理成功事件
     */
    public void handleMessageProcessed(DomainEvent event) {
        log.info("Handling MessageProcessedEvent: eventId={}", event.getEventId());
        // 记录指标、发送通知等
    }

    /**
     * 处理消息处理失败事件
     */
    public void handleMessageFailed(DomainEvent event) {
        log.warn("Handling MessageFailedEvent: eventId={}", event.getEventId());
        // 触发告警、记录错误、通知运维等
    }

    /**
     * 处理通用消息
     */
    public void processGenericMessage(String messageBody) {
        log.debug("Processing generic message");
        // 实现具体的业务逻辑
    }

    /**
     * 检查消息是否已处理（幂等性）
     */
    public boolean isAlreadyProcessed(String messageId) {
        String key = PROCESSED_MESSAGES_KEY + messageId;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 标记消息为已处理（幂等性记录）
     */
    public void markAsProcessed(String messageId) {
        String key = PROCESSED_MESSAGES_KEY + messageId;
        redisTemplate.opsForValue().set(key, "1", PROCESSED_MESSAGES_TTL);
        log.debug("Message marked as processed: messageId={}", messageId);
    }

    /**
     * 补偿操作
     * 当消息处理失败时执行
     */
    public void compensate(String messageId, Exception e) {
        log.warn("Compensating for failed message: messageId={}, error={}", messageId, e.getMessage());
        // 实现补偿逻辑，如回滚事务、清理资源等
    }

    /**
     * 验证工单事件数据
     */
    private void validateTicketEvent(TicketCreatedEvent event) {
        if (event.getTicketId() == null) {
            throw new IllegalArgumentException("Ticket ID is required");
        }
        if (event.getSubject() == null || event.getSubject().isBlank()) {
            throw new IllegalArgumentException("Ticket subject is required");
        }
        if (event.getRequesterId() == null) {
            throw new IllegalArgumentException("Requester ID is required");
        }
    }

    /**
     * 处理工单业务逻辑
     */
    private void processTicket(TicketCreatedEvent event) {
        // 这里实现具体的工单处理逻辑
        // 例如：创建工单记录、发送通知、更新索引等

        log.debug("Processing ticket: id={}, subject={}, type={}, priority={}",
                event.getTicketId(),
                event.getSubject(),
                event.getType(),
                event.getPriority());

        // 模拟处理延迟
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
