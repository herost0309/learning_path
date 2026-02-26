package com.example.awssqs.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ImMessageController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ImMessageControllerTest {

    @Mock
    private ImMessageProducer messageProducer;

    @Mock
    private ImMessageRepository messageRepository;

    @Mock
    private ImMessageReconciliationService reconciliationService;

    @InjectMocks
    private ImMessageController controller;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testSendMessage_success() {
        // Arrange
        ImMessageController.SendMessageRequest request = new ImMessageController.SendMessageRequest();
        request.setMessageId("msg-123");
        request.setClientMessageId("client-msg-123");
        request.setConversationId("conv-456");
        request.setSenderId("user-001");
        request.setReceiverId("user-002");
        request.setMessageType(ImMessage.MessageType.TEXT);
        request.setContent("Hello World");
        request.setPriority(ImMessage.MessagePriority.NORMAL);

        ImMessageProducer.SendResult sendResult = ImMessageProducer.SendResult.success(
                "msg-123", "sqs-msg-123", "seq-123");

        when(messageProducer.sendImMessage(any(ImMessage.class))).thenReturn(sendResult);

        // Act
        ResponseEntity<ImMessageController.SendMessageResponse> response =
                controller.sendMessage(request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("msg-123", response.getBody().getMessageId());
        assertEquals("sqs-msg-123", response.getBody().getSqsMessageId());
    }

    @Test
    void testSendMessage_duplicate() {
        // Arrange
        ImMessageController.SendMessageRequest request = new ImMessageController.SendMessageRequest();
        request.setMessageId("msg-123");
        request.setConversationId("conv-456");
        request.setSenderId("user-001");
        request.setReceiverId("user-002");
        request.setMessageType(ImMessage.MessageType.TEXT);
        request.setContent("Hello World");

        ImMessageProducer.SendResult sendResult = ImMessageProducer.SendResult.duplicate(
                "msg-123", "Duplicate message");

        when(messageProducer.sendImMessage(any(ImMessage.class))).thenReturn(sendResult);

        // Act
        ResponseEntity<ImMessageController.SendMessageResponse> response =
                controller.sendMessage(request);

        // Assert
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("DUPLICATE", response.getBody().getResultType());
    }

    @Test
    void testSendMessage_failure() {
        // Arrange
        ImMessageController.SendMessageRequest request = new ImMessageController.SendMessageRequest();
        request.setMessageId("msg-123");
        request.setConversationId("conv-456");
        request.setSenderId("user-001");
        request.setReceiverId("user-002");
        request.setMessageType(ImMessage.MessageType.TEXT);
        request.setContent("Hello World");

        ImMessageProducer.SendResult sendResult = ImMessageProducer.SendResult.failure(
                "msg-123", "Send failed");

        when(messageProducer.sendImMessage(any(ImMessage.class))).thenReturn(sendResult);

        // Act
        ResponseEntity<ImMessageController.SendMessageResponse> response =
                controller.sendMessage(request);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Send failed", response.getBody().getErrorMessage());
    }

    @Test
    void testSendMessage_withException() {
        // Arrange
        ImMessageController.SendMessageRequest request = new ImMessageController.SendMessageRequest();
        request.setConversationId("conv-456");
        request.setSenderId("user-001");

        when(messageProducer.sendImMessage(any(ImMessage.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        // Act
        ResponseEntity<ImMessageController.SendMessageResponse> response =
                controller.sendMessage(request);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertNotNull(response.getBody().getErrorMessage());
    }

    @Test
    void testSendBatch() {
        // Arrange
        List<ImMessageController.SendMessageRequest> requests = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ImMessageController.SendMessageRequest request = new ImMessageController.SendMessageRequest();
            request.setMessageId("msg-" + i);
            request.setConversationId("conv-456");
            request.setSenderId("user-001");
            request.setReceiverId("user-002");
            request.setMessageType(ImMessage.MessageType.TEXT);
            request.setContent("Message " + i);
            requests.add(request);
        }

        List<ImMessageProducer.SendResult> results = new ArrayList<>();
        results.add(ImMessageProducer.SendResult.success("msg-0", "sqs-0", "seq-0"));
        results.add(ImMessageProducer.SendResult.success("msg-1", "sqs-1", "seq-1"));
        results.add(ImMessageProducer.SendResult.success("msg-2", "sqs-2", "seq-2"));

        ImMessageProducer.BatchSendResult batchResult = new ImMessageProducer.BatchSendResult(
                results, 3, 0);

        when(messageProducer.sendBatch(any())).thenReturn(batchResult);

        // Act
        ResponseEntity<ImMessageController.BatchSendResponse> response =
                controller.sendBatch(requests);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().getTotalCount());
        assertEquals(3, response.getBody().getSuccessCount());
        assertEquals(0, response.getBody().getFailureCount());
    }

    @Test
    void testSendDeliveredReceipt() {
        // Arrange
        doNothing().when(messageProducer).sendDeliveryReceipt(anyString(), anyString());

        // Act
        ResponseEntity<Void> response = controller.sendDeliveredReceipt("msg-123", "user-002");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(messageProducer).sendDeliveryReceipt("msg-123", "user-002");
    }

    @Test
    void testSendReadReceipt() {
        // Arrange
        doNothing().when(messageProducer).sendReadReceipt(anyString(), anyString());

        // Act
        ResponseEntity<Void> response = controller.sendReadReceipt("msg-123", "user-002");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(messageProducer).sendReadReceipt("msg-123", "user-002");
    }

    @Test
    void testGetMessageStatus_found() {
        // Arrange
        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("msg-123")
                .clientMessageId("client-msg-123")
                .status(ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED)
                .deliveryStatus(ImMessagePersistence.DeliveryStatus.DELIVERED)
                .ackStatus(ImMessagePersistence.AckStatus.DELIVERY_ACK)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .deliveredAt(LocalDateTime.now())
                .build();

        when(messageRepository.findByMessageId("msg-123")).thenReturn(Optional.of(persistence));

        // Act
        ResponseEntity<ImMessageController.MessageStatusResponse> response =
                controller.getMessageStatus("msg-123");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("msg-123", response.getBody().getMessageId());
        assertEquals("ACKNOWLEDGED", response.getBody().getStatus());
        assertEquals("DELIVERED", response.getBody().getDeliveryStatus());
    }

    @Test
    void testGetMessageStatus_notFound() {
        // Arrange
        when(messageRepository.findByMessageId("nonexistent")).thenReturn(Optional.empty());

        // Act
        ResponseEntity<ImMessageController.MessageStatusResponse> response =
                controller.getMessageStatus("nonexistent");

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testGetConversationMessages() {
        // Arrange
        List<ImMessagePersistence> messages = new ArrayList<>();
        messages.add(ImMessagePersistence.builder()
                .messageId("msg-1")
                .conversationId("conv-456")
                .status(ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED)
                .createdAt(LocalDateTime.now())
                .build());
        messages.add(ImMessagePersistence.builder()
                .messageId("msg-2")
                .conversationId("conv-456")
                .status(ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED)
                .createdAt(LocalDateTime.now())
                .build());

        when(messageRepository.findByConversationIdOrderByCreatedAtDesc("conv-456", 50))
                .thenReturn(messages);

        // Act
        ResponseEntity<List<ImMessageController.MessageStatusResponse>> response =
                controller.getConversationMessages("conv-456", 50);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void testRetryMessage() {
        // Arrange
        doNothing().when(messageProducer).retryFailedMessage(anyString());

        // Act
        ResponseEntity<Void> response = controller.retryMessage("msg-123");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(messageProducer).retryFailedMessage("msg-123");
    }

    @Test
    void testTriggerReconciliation() {
        // Arrange
        ImMessageReconciliationService.ReconciliationResult mockResult =
                new ImMessageReconciliationService.ReconciliationResult();
        mockResult.setSuccess(true);
        mockResult.setMessage("Completed");

        when(reconciliationService.manualReconciliation()).thenReturn(mockResult);

        // Act
        ResponseEntity<ImMessageReconciliationService.ReconciliationResult> response =
                controller.triggerReconciliation();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
    }

    @Test
    void testGetReconciliationStats() {
        // Arrange
        ImMessageReconciliationService.ReconciliationStats mockStats =
                ImMessageReconciliationService.ReconciliationStats.builder()
                        .totalMessages(100L)
                        .acknowledgedCount(95L)
                        .failedCount(2L)
                        .lostCount(0L)
                        .dlqCount(3L)
                        .build();

        when(reconciliationService.getReconciliationStats()).thenReturn(mockStats);

        // Act
        ResponseEntity<ImMessageReconciliationService.ReconciliationStats> response =
                controller.getReconciliationStats();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(100L, response.getBody().getTotalMessages());
        assertEquals(95L, response.getBody().getAcknowledgedCount());
    }

    @Test
    void testSendMessageRequest_gettersAndSetters() {
        // Arrange & Act
        ImMessageController.SendMessageRequest request = new ImMessageController.SendMessageRequest();
        request.setMessageId("msg-123");
        request.setClientMessageId("client-msg-123");
        request.setConversationId("conv-456");
        request.setSenderId("user-001");
        request.setReceiverId("user-002");
        request.setMessageType(ImMessage.MessageType.TEXT);
        request.setContent("Hello");
        request.setMetadata("{\"key\":\"value\"}");
        request.setPriority(ImMessage.MessagePriority.HIGH);
        request.setRequireReadReceipt(true);
        request.setTenantId(1L);
        request.setDeviceId("device-001");

        // Assert
        assertEquals("msg-123", request.getMessageId());
        assertEquals("client-msg-123", request.getClientMessageId());
        assertEquals("conv-456", request.getConversationId());
        assertEquals("user-001", request.getSenderId());
        assertEquals("user-002", request.getReceiverId());
        assertEquals(ImMessage.MessageType.TEXT, request.getMessageType());
        assertEquals("Hello", request.getContent());
        assertEquals("{\"key\":\"value\"}", request.getMetadata());
        assertEquals(ImMessage.MessagePriority.HIGH, request.getPriority());
        assertTrue(request.getRequireReadReceipt());
        assertEquals(1L, request.getTenantId());
        assertEquals("device-001", request.getDeviceId());
    }

    @Test
    void testSendMessageResponse_gettersAndSetters() {
        // Arrange & Act
        ImMessageController.SendMessageResponse response = new ImMessageController.SendMessageResponse();
        response.setMessageId("msg-123");
        response.setClientMessageId("client-msg-123");
        response.setSuccess(true);
        response.setSqsMessageId("sqs-msg-123");
        response.setSequenceNumber("seq-123");
        response.setErrorMessage(null);
        response.setResultType("SUCCESS");

        // Assert
        assertEquals("msg-123", response.getMessageId());
        assertEquals("client-msg-123", response.getClientMessageId());
        assertTrue(response.isSuccess());
        assertEquals("sqs-msg-123", response.getSqsMessageId());
        assertEquals("seq-123", response.getSequenceNumber());
        assertNull(response.getErrorMessage());
        assertEquals("SUCCESS", response.getResultType());
    }

    @Test
    void testBatchSendResponse_gettersAndSetters() {
        // Arrange & Act
        ImMessageController.BatchSendResponse response = new ImMessageController.BatchSendResponse();
        response.setTotalCount(10);
        response.setSuccessCount(8);
        response.setFailureCount(2);
        response.setResults(new ArrayList<>());

        // Assert
        assertEquals(10, response.getTotalCount());
        assertEquals(8, response.getSuccessCount());
        assertEquals(2, response.getFailureCount());
        assertNotNull(response.getResults());
    }

    @Test
    void testMessageStatusResponse_gettersAndSetters() {
        // Arrange & Act
        ImMessageController.MessageStatusResponse response = new ImMessageController.MessageStatusResponse();
        response.setMessageId("msg-123");
        response.setClientMessageId("client-msg-123");
        response.setStatus("ACKNOWLEDGED");
        response.setDeliveryStatus("DELIVERED");
        response.setAckStatus("DELIVERY_ACK");
        response.setRetryCount(0);
        response.setErrorMessage(null);
        response.setCreatedAt(LocalDateTime.now());
        response.setDeliveredAt(LocalDateTime.now());
        response.setReadAt(LocalDateTime.now());

        // Assert
        assertEquals("msg-123", response.getMessageId());
        assertEquals("ACKNOWLEDGED", response.getStatus());
        assertEquals("DELIVERED", response.getDeliveryStatus());
    }
}
