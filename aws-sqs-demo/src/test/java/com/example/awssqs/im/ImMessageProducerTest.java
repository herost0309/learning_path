package com.example.awssqs.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ImMessageProducer 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImMessageProducerTest {

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private ImMessageRepository messageRepository;

    private ObjectMapper objectMapper;

    @InjectMocks
    private ImMessageProducer messageProducer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(messageProducer, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(messageProducer, "imQueueUrl", "https://sqs.us-east-1.amazonaws.com/123456789/im-messages");
        ReflectionTestUtils.setField(messageProducer, "imFifoQueueUrl", "https://sqs.us-east-1.amazonaws.com/123456789/im-messages.fifo");
        ReflectionTestUtils.setField(messageProducer, "maxRetryCount", 5);
        ReflectionTestUtils.setField(messageProducer, "sendTimeoutSeconds", 30);
    }

    @Test
    void testSendImMessage_success() {
        // Arrange
        ImMessage message = createTestMessage();

        when(messageRepository.findByMessageId(anyString())).thenReturn(Optional.empty());
        when(messageRepository.findByClientMessageId(anyString())).thenReturn(Optional.empty());
        when(messageRepository.findMaxSequenceNumber(anyString())).thenReturn(0L);
        when(messageRepository.save(any(ImMessagePersistence.class))).thenAnswer(invocation -> {
            ImMessagePersistence p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        SendMessageResponse sqsResponse = SendMessageResponse.builder()
                .messageId("sqs-msg-123")
                .sequenceNumber("seq-123")
                .build();
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(sqsResponse));

        // Act
        ImMessageProducer.SendResult result = messageProducer.sendImMessage(message);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("msg-123", result.getMessageId());
        assertEquals("sqs-msg-123", result.getSqsMessageId());
        assertEquals(ImMessageProducer.SendResult.ResultType.SUCCESS, result.getResultType());

        verify(messageRepository, atLeastOnce()).save(any(ImMessagePersistence.class));
        verify(sqsAsyncClient).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testSendImMessage_duplicateMessage_byMessageId() {
        // Arrange
        ImMessage message = ImMessage.builder()
                .messageId("msg-123")
                .clientMessageId(null)
                .conversationId("conv-456")
                .senderId("user-001")
                .timestamp(System.currentTimeMillis())
                .build();

        ImMessagePersistence existingPersistence = ImMessagePersistence.builder()
                .messageId("msg-123")
                .status(ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED)
                .build();

        when(messageRepository.findByMessageId("msg-123")).thenReturn(Optional.of(existingPersistence));

        // Act
        ImMessageProducer.SendResult result = messageProducer.sendImMessage(message);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(ImMessageProducer.SendResult.ResultType.DUPLICATE, result.getResultType());

        verify(sqsAsyncClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testSendImMessage_duplicateMessage_byClientMessageId() {
        // Arrange
        ImMessage message = ImMessage.builder()
                .messageId("msg-new-123")
                .clientMessageId("client-msg-123")
                .conversationId("conv-456")
                .senderId("user-001")
                .timestamp(System.currentTimeMillis())
                .build();

        when(messageRepository.findByMessageId("msg-new-123")).thenReturn(Optional.empty());
        when(messageRepository.existsByClientMessageId("client-msg-123")).thenReturn(true);

        // Act
        ImMessageProducer.SendResult result = messageProducer.sendImMessage(message);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(ImMessageProducer.SendResult.ResultType.DUPLICATE, result.getResultType());

        verify(sqsAsyncClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testSendImMessage_sqsSendFailure() {
        // Arrange
        ImMessage message = createTestMessage();

        when(messageRepository.findByMessageId(anyString())).thenReturn(Optional.empty());
        when(messageRepository.findByClientMessageId(anyString())).thenReturn(Optional.empty());
        when(messageRepository.existsByClientMessageId(anyString())).thenReturn(false);
        when(messageRepository.findMaxSequenceNumber(anyString())).thenReturn(0L);
        when(messageRepository.save(any(ImMessagePersistence.class))).thenAnswer(invocation -> {
            ImMessagePersistence p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("SQS error")));

        // Act
        ImMessageProducer.SendResult result = messageProducer.sendImMessage(message);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(ImMessageProducer.SendResult.ResultType.FAILURE, result.getResultType());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testConfirmMessageProcessed() {
        // Arrange
        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("msg-123")
                .status(ImMessagePersistence.ImMessageStatus.PROCESSED)
                .build();

        when(messageRepository.findByMessageId("msg-123")).thenReturn(Optional.of(persistence));
        when(messageRepository.save(any(ImMessagePersistence.class))).thenReturn(persistence);

        // Act
        messageProducer.confirmMessageProcessed("msg-123");

        // Assert
        verify(messageRepository).findByMessageId("msg-123");
        verify(messageRepository).save(any(ImMessagePersistence.class));
    }

    @Test
    void testConfirmMessageProcessed_notFound() {
        // Arrange
        when(messageRepository.findByMessageId("msg-nonexistent")).thenReturn(Optional.empty());

        // Act - should not throw
        messageProducer.confirmMessageProcessed("msg-nonexistent");

        // Assert
        verify(messageRepository).findByMessageId("msg-nonexistent");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void testSendDeliveryReceipt() {
        // Arrange
        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("msg-123")
                .deliveryStatus(ImMessagePersistence.DeliveryStatus.PENDING)
                .build();

        when(messageRepository.findByMessageId("msg-123")).thenReturn(Optional.of(persistence));
        when(messageRepository.save(any(ImMessagePersistence.class))).thenReturn(persistence);

        // Act
        messageProducer.sendDeliveryReceipt("msg-123", "user-002");

        // Assert
        verify(messageRepository).save(any(ImMessagePersistence.class));
    }

    @Test
    void testSendReadReceipt() {
        // Arrange
        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("msg-123")
                .build();

        when(messageRepository.findByMessageId("msg-123")).thenReturn(Optional.of(persistence));
        when(messageRepository.save(any(ImMessagePersistence.class))).thenReturn(persistence);

        // Act
        messageProducer.sendReadReceipt("msg-123", "user-002");

        // Assert
        verify(messageRepository).save(any(ImMessagePersistence.class));
    }

    @Test
    void testRetryFailedMessage_canRetry() {
        // Arrange
        String payload = "{\"messageId\":\"msg-123\",\"content\":\"Hello\"}";
        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("msg-123")
                .payload(payload)
                .messageType("TEXT")
                .retryCount(2)
                .maxRetryCount(5)
                .manualIntervention(false)
                .build();

        when(messageRepository.findByMessageId("msg-123")).thenReturn(Optional.of(persistence));
        when(messageRepository.findByClientMessageId(any())).thenReturn(Optional.empty());
        when(messageRepository.existsByClientMessageId(any())).thenReturn(false);
        when(messageRepository.findMaxSequenceNumber(any())).thenReturn(0L);
        when(messageRepository.save(any(ImMessagePersistence.class))).thenReturn(persistence);

        SendMessageResponse sqsResponse = SendMessageResponse.builder()
                .messageId("sqs-msg-123")
                .build();
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(sqsResponse));

        // Act
        messageProducer.retryFailedMessage("msg-123");

        // Assert - retry count should be incremented
        verify(messageRepository, atLeast(1)).save(any(ImMessagePersistence.class));
    }

    @Test
    void testRetryFailedMessage_cannotRetry() {
        // Arrange
        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("msg-123")
                .payload("{}")
                .retryCount(5)
                .maxRetryCount(5)
                .manualIntervention(false)
                .build();

        when(messageRepository.findByMessageId("msg-123")).thenReturn(Optional.of(persistence));

        // Act
        messageProducer.retryFailedMessage("msg-123");

        // Assert - SQS should not be called since retry is not allowed
        verify(sqsAsyncClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testRetryFailedMessage_notFound() {
        // Arrange
        when(messageRepository.findByMessageId("msg-nonexistent")).thenReturn(Optional.empty());

        // Act - should not throw
        messageProducer.retryFailedMessage("msg-nonexistent");

        // Assert
        verify(sqsAsyncClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testSendResult_success() {
        ImMessageProducer.SendResult result = ImMessageProducer.SendResult.success(
                "msg-123", "sqs-msg-123", "seq-123");

        assertTrue(result.isSuccess());
        assertEquals("msg-123", result.getMessageId());
        assertEquals("sqs-msg-123", result.getSqsMessageId());
        assertEquals("seq-123", result.getSequenceNumber());
        assertEquals(ImMessageProducer.SendResult.ResultType.SUCCESS, result.getResultType());
    }

    @Test
    void testSendResult_duplicate() {
        ImMessageProducer.SendResult result = ImMessageProducer.SendResult.duplicate(
                "msg-123", "Already exists");

        assertFalse(result.isSuccess());
        assertEquals("msg-123", result.getMessageId());
        assertEquals("Already exists", result.getErrorMessage());
        assertEquals(ImMessageProducer.SendResult.ResultType.DUPLICATE, result.getResultType());
    }

    @Test
    void testSendResult_failure() {
        ImMessageProducer.SendResult result = ImMessageProducer.SendResult.failure(
                "msg-123", "Send failed");

        assertFalse(result.isSuccess());
        assertEquals("msg-123", result.getMessageId());
        assertEquals("Send failed", result.getErrorMessage());
        assertEquals(ImMessageProducer.SendResult.ResultType.FAILURE, result.getResultType());
    }

    @Test
    void testBatchSendResult() {
        List<ImMessageProducer.SendResult> results = new ArrayList<>();
        results.add(ImMessageProducer.SendResult.success("msg-1", "sqs-1", "seq-1"));
        results.add(ImMessageProducer.SendResult.failure("msg-2", "Failed"));

        ImMessageProducer.BatchSendResult batchResult = new ImMessageProducer.BatchSendResult(
                results, 1, 1);

        assertEquals(2, batchResult.getResults().size());
        assertEquals(1, batchResult.getSuccessCount());
        assertEquals(1, batchResult.getFailureCount());
        assertFalse(batchResult.isAllSuccess());
    }

    @Test
    void testBatchSendResult_allSuccess() {
        List<ImMessageProducer.SendResult> results = new ArrayList<>();
        results.add(ImMessageProducer.SendResult.success("msg-1", "sqs-1", "seq-1"));
        results.add(ImMessageProducer.SendResult.success("msg-2", "sqs-2", "seq-2"));

        ImMessageProducer.BatchSendResult batchResult = new ImMessageProducer.BatchSendResult(
                results, 2, 0);

        assertTrue(batchResult.isAllSuccess());
    }

    // Helper methods
    private ImMessage createTestMessage() {
        return ImMessage.builder()
                .messageId("msg-123")
                .clientMessageId("client-msg-123")
                .conversationId("conv-456")
                .senderId("user-001")
                .receiverId("user-002")
                .messageType(ImMessage.MessageType.TEXT)
                .content("Hello World")
                .priority(ImMessage.MessagePriority.NORMAL)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
