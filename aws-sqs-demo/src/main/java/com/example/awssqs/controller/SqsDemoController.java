package com.example.awssqs.controller;

import com.example.awssqs.dlq.DeadLetterQueueService;
import com.example.awssqs.domain.FailedMessage;
import com.example.awssqs.domain.TicketCreatedEvent;
import com.example.awssqs.producer.MessageProducer;
import com.example.awssqs.reconciliation.ReconciliationService;
import com.example.awssqs.reconciliation.ReconciliationService.ReconciliationStats;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQS演示控制器
 * 提供REST API用于测试和监控SQS消息功能
 */
@Slf4j
@RestController
@RequestMapping("/api/sqs")
@RequiredArgsConstructor
public class SqsDemoController {

    private final MessageProducer messageProducer;
    private final DeadLetterQueueService deadLetterQueueService;
    private final ReconciliationService reconciliationService;

    private static final String TICKET_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/123456789012/ticket-events.fifo";
    private static final String EVENT_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/123456789012/domain-events";

    /**
     * 发送工单创建事件到标准队列
     */
    @PostMapping("/send/ticket")
    public ResponseEntity<Map<String, Object>> sendTicketEvent(@RequestBody TicketRequest request) {
        try {
            TicketCreatedEvent event = TicketCreatedEvent.builder()
                    .ticketId(request.getTicketId())
                    .requesterId(request.getRequesterId())
                    .organizationId(request.getOrganizationId())
                    .subject(request.getSubject())
                    .description(request.getDescription())
                    .type(request.getType())
                    .priority(request.getPriority())
                    .via(request.getVia())
                    .customFields(request.getCustomFields())
                    .build();

            String messageId = messageProducer.sendToStandardQueue(
                    TICKET_QUEUE_URL,
                    event,
                    request.getTenantId(),
                    String.valueOf(request.getTicketId()),
                    "Ticket"
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("messageId", messageId);
            response.put("ticketId", request.getTicketId());
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to send ticket event", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 发送事件到FIFO队列
     */
    @PostMapping("/send/fifo")
    public ResponseEntity<Map<String, Object>> sendToFifoQueue(
            @RequestBody Map<String, Object> payload,
            @RequestParam(required = false) String messageGroupId,
            @RequestParam(required = false) String deduplicationId) {

        try {
            String groupId = messageGroupId != null ? messageGroupId : "default-group";
            String dedupId = deduplicationId != null ? deduplicationId : null;

            String messageId = messageProducer.sendToFifoQueue(
                    TICKET_QUEUE_URL,
                    payload,
                    1L,
                    null,
                    null,
                    groupId,
                    dedupId
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("messageId", messageId);
            response.put("groupId", groupId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to send to FIFO queue", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 批量发送消息
     */
    @PostMapping("/send/batch")
    public ResponseEntity<Map<String, Object>> sendBatch(@RequestBody BatchRequest request) {
        try {
            List<Map<String, Object>> payloads = request.getPayloads();

            var result = messageProducer.batchSend(
                    EVENT_QUEUE_URL,
                    payloads,
                    request.getTenantId(),
                    null,
                    null
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("total", payloads.size());
            response.put("successful", result.response().successful().size());
            response.put("failed", result.response().failed().size());
            response.put("allSuccessful", result.isAllSuccessful());

            if (!result.isAllSuccessful()) {
                response.put("failedMessageIds", result.getFailedMessageIds());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to send batch", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 发送带延迟的消息
     */
    @PostMapping("/send/delayed")
    public ResponseEntity<Map<String, Object>> sendDelayed(
            @RequestBody Map<String, Object> payload,
            @RequestParam int delaySeconds) {

        try {
            String messageId = messageProducer.sendWithDelay(
                    EVENT_QUEUE_URL,
                    payload,
                    delaySeconds,
                    null,
                    null,
                    null
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("messageId", messageId);
            response.put("delaySeconds", delaySeconds);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to send delayed message", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 触发手动对账
     */
    @PostMapping("/reconciliation/manual")
    public ResponseEntity<Map<String, Object>> triggerManualReconciliation() {
        try {
            var result = reconciliationService.manualReconciliation();

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Manual reconciliation failed", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 获取对账统计信息
     */
    @GetMapping("/reconciliation/stats")
    public ResponseEntity<ReconciliationStats> getReconciliationStats() {
        ReconciliationStats stats = reconciliationService.getReconciliationStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取DLQ中的消息列表
     */
    @GetMapping("/dlq/messages")
    public ResponseEntity<List<FailedMessage>> getDlqMessages() {
        List<FailedMessage> messages = deadLetterQueueService.getMessagesForManualIntervention();
        return ResponseEntity.ok(messages);
    }

    /**
     * 手动处理DLQ消息
     */
    @PostMapping("/dlq/handle")
    public ResponseEntity<Map<String, Object>> handleDlqMessage(
            @RequestParam String messageId,
            @RequestParam String handledBy,
            @RequestParam String notes,
            @RequestParam boolean retry) {

        try {
            deadLetterQueueService.handleManually(messageId, handledBy, notes, retry);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("messageId", messageId);
            response.put("action", retry ? "retry" : "resolve");
            response.put("handledBy", handledBy);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to handle DLQ message", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 忽略DLQ消息
     */
    @PostMapping("/dlq/ignore")
    public ResponseEntity<Map<String, Object>> ignoreDlqMessage(
            @RequestParam String messageId,
            @RequestParam String handledBy,
            @RequestParam String reason) {

        try {
            deadLetterQueueService.ignoreMessage(messageId, handledBy, reason);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("messageId", messageId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to ignore DLQ message", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "AWS SQS Demo");
        status.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(status);
    }

    /**
     * 获取统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        ReconciliationStats stats = reconciliationService.getReconciliationStats();
        Map<String, Object> response = new HashMap<>();
        response.put("messageStats", stats);

        // 可以添加更多统计信息
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    // 请求DTO类

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketRequest {
        private Long ticketId;
        private Long requesterId;
        private Long organizationId;
        private String subject;
        private String description;
        private String type;
        private String priority;
        private String via;
        private Map<String, Object> customFields;
        private Long tenantId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchRequest {
        private List<Map<String, Object>> payloads;
        private Long tenantId;
    }
}
