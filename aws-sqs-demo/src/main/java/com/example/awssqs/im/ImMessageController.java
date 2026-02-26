package com.example.awssqs.im;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * IM消息API控制器
 * 提供消息发送、查询、回执等接口
 */
@Slf4j
@RestController
@RequestMapping("/api/im/messages")
@RequiredArgsConstructor
public class ImMessageController {

    private final ImMessageProducer messageProducer;
    private final ImMessageRepository messageRepository;
    private final ImMessageReconciliationService reconciliationService;

    /**
     * 发送IM消息
     */
    @PostMapping("/send")
    public ResponseEntity<SendMessageResponse> sendMessage(@RequestBody SendMessageRequest request) {
        log.info("[IM-API] Received send message request: conversationId={}, senderId={}",
                request.getConversationId(), request.getSenderId());

        try {
            // 构建IM消息
            ImMessage message = ImMessage.builder()
                    .messageId(request.getMessageId() != null ? request.getMessageId() : generateMessageId())
                    .clientMessageId(request.getClientMessageId())
                    .conversationId(request.getConversationId())
                    .senderId(request.getSenderId())
                    .receiverId(request.getReceiverId())
                    .messageType(request.getMessageType() != null ? request.getMessageType() : ImMessage.MessageType.TEXT)
                    .content(request.getContent())
                    .metadata(request.getMetadata())
                    .priority(request.getPriority() != null ? request.getPriority() : ImMessage.MessagePriority.NORMAL)
                    .requireReadReceipt(request.getRequireReadReceipt() != null ? request.getRequireReadReceipt() : false)
                    .timestamp(System.currentTimeMillis())
                    .tenantId(request.getTenantId())
                    .deviceId(request.getDeviceId())
                    .build();

            // 发送消息
            ImMessageProducer.SendResult result = messageProducer.sendImMessage(message);

            SendMessageResponse response = new SendMessageResponse();
            response.setMessageId(message.getMessageId());
            response.setClientMessageId(message.getClientMessageId());
            response.setSuccess(result.isSuccess());
            response.setSqsMessageId(result.getSqsMessageId());
            response.setSequenceNumber(result.getSequenceNumber());
            response.setErrorMessage(result.getErrorMessage());
            response.setResultType(result.getResultType().name());

            if (result.isSuccess()) {
                return ResponseEntity.ok(response);
            } else if (result.getResultType() == ImMessageProducer.SendResult.ResultType.DUPLICATE) {
                return ResponseEntity.status(409).body(response); // Conflict
            } else {
                return ResponseEntity.status(500).body(response);
            }

        } catch (Exception e) {
            log.error("[IM-API] Failed to send message", e);
            SendMessageResponse response = new SendMessageResponse();
            response.setSuccess(false);
            response.setErrorMessage(e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 批量发送消息
     */
    @PostMapping("/batch")
    public ResponseEntity<BatchSendResponse> sendBatch(@RequestBody List<SendMessageRequest> requests) {
        log.info("[IM-API] Received batch send request: count={}", requests.size());

        List<ImMessage> messages = requests.stream()
                .map(this::convertToImMessage)
                .toList();

        ImMessageProducer.BatchSendResult result = messageProducer.sendBatch(messages);

        BatchSendResponse response = new BatchSendResponse();
        response.setTotalCount(requests.size());
        response.setSuccessCount(result.getSuccessCount());
        response.setFailureCount(result.getFailureCount());
        response.setResults(result.getResults());

        return ResponseEntity.ok(response);
    }

    /**
     * 发送送达回执
     */
    @PostMapping("/{messageId}/delivered")
    public ResponseEntity<Void> sendDeliveredReceipt(
            @PathVariable String messageId,
            @RequestParam String receiverId) {

        log.info("[IM-API] Sending delivered receipt: messageId={}, receiverId={}", messageId, receiverId);

        messageProducer.sendDeliveryReceipt(messageId, receiverId);

        return ResponseEntity.ok().build();
    }

    /**
     * 发送已读回执
     */
    @PostMapping("/{messageId}/read")
    public ResponseEntity<Void> sendReadReceipt(
            @PathVariable String messageId,
            @RequestParam String readerId) {

        log.info("[IM-API] Sending read receipt: messageId={}, readerId={}", messageId, readerId);

        messageProducer.sendReadReceipt(messageId, readerId);

        return ResponseEntity.ok().build();
    }

    /**
     * 查询消息状态
     */
    @GetMapping("/{messageId}/status")
    public ResponseEntity<MessageStatusResponse> getMessageStatus(@PathVariable String messageId) {
        return messageRepository.findByMessageId(messageId)
                .map(persistence -> {
                    MessageStatusResponse response = new MessageStatusResponse();
                    response.setMessageId(persistence.getMessageId());
                    response.setClientMessageId(persistence.getClientMessageId());
                    response.setStatus(persistence.getStatus().name());
                    response.setDeliveryStatus(persistence.getDeliveryStatus() != null ?
                            persistence.getDeliveryStatus().name() : null);
                    response.setAckStatus(persistence.getAckStatus() != null ?
                            persistence.getAckStatus().name() : null);
                    response.setRetryCount(persistence.getRetryCount());
                    response.setErrorMessage(persistence.getErrorMessage());
                    response.setCreatedAt(persistence.getCreatedAt());
                    response.setDeliveredAt(persistence.getDeliveredAt());
                    response.setReadAt(persistence.getReadAt());
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取会话消息列表
     */
    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<List<MessageStatusResponse>> getConversationMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "50") int limit) {

        List<ImMessagePersistence> messages = messageRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId, limit);

        List<MessageStatusResponse> response = messages.stream()
                .map(this::convertToStatusResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * 手动重试失败消息
     */
    @PostMapping("/{messageId}/retry")
    public ResponseEntity<Void> retryMessage(@PathVariable String messageId) {
        log.info("[IM-API] Manual retry requested: messageId={}", messageId);

        messageProducer.retryFailedMessage(messageId);

        return ResponseEntity.ok().build();
    }

    /**
     * 手动触发对账
     */
    @PostMapping("/reconciliation")
    public ResponseEntity<ImMessageReconciliationService.ReconciliationResult> triggerReconciliation() {
        log.info("[IM-API] Manual reconciliation triggered");

        ImMessageReconciliationService.ReconciliationResult result =
                reconciliationService.manualReconciliation();

        return ResponseEntity.ok(result);
    }

    /**
     * 获取对账统计
     */
    @GetMapping("/reconciliation/stats")
    public ResponseEntity<ImMessageReconciliationService.ReconciliationStats> getReconciliationStats() {
        return ResponseEntity.ok(reconciliationService.getReconciliationStats());
    }

    // ============== 私有方法 ==============

    private String generateMessageId() {
        return "msg-" + System.currentTimeMillis() + "-" +
               java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private ImMessage convertToImMessage(SendMessageRequest request) {
        return ImMessage.builder()
                .messageId(request.getMessageId() != null ? request.getMessageId() : generateMessageId())
                .clientMessageId(request.getClientMessageId())
                .conversationId(request.getConversationId())
                .senderId(request.getSenderId())
                .receiverId(request.getReceiverId())
                .messageType(request.getMessageType() != null ? request.getMessageType() : ImMessage.MessageType.TEXT)
                .content(request.getContent())
                .metadata(request.getMetadata())
                .priority(request.getPriority() != null ? request.getPriority() : ImMessage.MessagePriority.NORMAL)
                .requireReadReceipt(request.getRequireReadReceipt() != null ? request.getRequireReadReceipt() : false)
                .timestamp(System.currentTimeMillis())
                .tenantId(request.getTenantId())
                .deviceId(request.getDeviceId())
                .build();
    }

    private MessageStatusResponse convertToStatusResponse(ImMessagePersistence persistence) {
        MessageStatusResponse response = new MessageStatusResponse();
        response.setMessageId(persistence.getMessageId());
        response.setClientMessageId(persistence.getClientMessageId());
        response.setStatus(persistence.getStatus().name());
        response.setDeliveryStatus(persistence.getDeliveryStatus() != null ?
                persistence.getDeliveryStatus().name() : null);
        response.setAckStatus(persistence.getAckStatus() != null ?
                persistence.getAckStatus().name() : null);
        response.setRetryCount(persistence.getRetryCount());
        response.setErrorMessage(persistence.getErrorMessage());
        response.setCreatedAt(persistence.getCreatedAt());
        response.setDeliveredAt(persistence.getDeliveredAt());
        response.setReadAt(persistence.getReadAt());
        return response;
    }

    // ============== 请求/响应类 ==============

    @lombok.Data
    public static class SendMessageRequest {
        private String messageId;
        private String clientMessageId;
        private String conversationId;
        private String senderId;
        private String receiverId;
        private ImMessage.MessageType messageType;
        private String content;
        private String metadata;
        private ImMessage.MessagePriority priority;
        private Boolean requireReadReceipt;
        private Long tenantId;
        private String deviceId;
    }

    @lombok.Data
    public static class SendMessageResponse {
        private String messageId;
        private String clientMessageId;
        private boolean success;
        private String sqsMessageId;
        private String sequenceNumber;
        private String errorMessage;
        private String resultType;
    }

    @lombok.Data
    public static class BatchSendResponse {
        private int totalCount;
        private int successCount;
        private int failureCount;
        private List<ImMessageProducer.SendResult> results;
    }

    @lombok.Data
    public static class MessageStatusResponse {
        private String messageId;
        private String clientMessageId;
        private String status;
        private String deliveryStatus;
        private String ackStatus;
        private Integer retryCount;
        private String errorMessage;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime deliveredAt;
        private java.time.LocalDateTime readAt;
    }
}
