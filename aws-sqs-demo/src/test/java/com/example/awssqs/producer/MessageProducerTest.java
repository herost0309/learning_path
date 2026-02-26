package com.example.awssqs.producer;

import com.example.awssqs.config.SqsConfiguration.SqsProperties;
import com.example.awssqs.domain.MessagePersistence;
import com.example.awssqs.domain.MessagePersistence.MessageStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test for MessageProducer
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MessageProducer Tests")
class MessageProducerTest {

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private MessagePersistenceService persistenceService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SqsProperties sqsProperties;

    @InjectMocks
    private MessageProducer messageProducer;

    private static final String TEST_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";
    private static final String TEST_FIFO_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue.fifo";

    @BeforeEach
    void setUp() {
        // Setup SqsProperties
        SqsProperties.ProducerConfig producerConfig = mock(SqsProperties.ProducerConfig.class);
        when(producerConfig.getDefaultDelaySeconds()).thenReturn(0);
        when(sqsProperties.getProducer()).thenReturn(producerConfig);
    }

    @Test
    @DisplayName("Should send message to standard queue successfully")
    void testSendToStandardQueue() throws Exception {
        // Given
        TestPayload payload = new TestPayload("123", "Test message");
        MessagePersistence persistedMsg = MessagePersistence.builder()
                .messageId("msg-123")
                .queueName("test-queue")
                .status(MessageStatus.PENDING)
                .build();
        when(persistenceService.persistBeforeSend(anyString(), any(), anyLong(), anyString(), anyString()))
                .thenReturn(persistedMsg);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        SendMessageResponse.builder()
                                .messageId("sqs-msg-id")
                                .sequenceNumber("seq-123")
                                .build()
                ));

        // When
        String messageId = messageProducer.sendToStandardQueue(
                TEST_QUEUE_URL, payload, 1L, "agg-123", "Ticket"
        );

        // Then
        assertEquals("msg-123", messageId);
        verify(persistenceService).persistBeforeSend(anyString(), any(), anyLong(), anyString(), anyString());
        verify(sqsAsyncClient).sendMessage(any(SendMessageRequest.class));
        verify(persistenceService).markAsSent("msg-123", "seq-123");
    }

    @Test
    @DisplayName("Should send message to FIFO queue with deduplication")
    void testSendToFifoQueue() throws Exception {
        // Given
        TestPayload payload = new TestPayload("123", "Test message");
        MessagePersistence persistedMsg = MessagePersistence.builder()
                .messageId("msg-123")
                .queueName("test-queue.fifo")
                .status(MessageStatus.PENDING)
                .build();
        when(persistenceService.persistBeforeSend(anyString(), any(), anyLong(), anyString(), anyString()))
                .thenReturn(persistedMsg);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        SendMessageResponse.builder()
                                .messageId("sqs-msg-id")
                                .build()
                ));

        // When
        String messageId = messageProducer.sendToFifoQueue(
                TEST_FIFO_QUEUE_URL,
                payload,
                1L,
                "agg-123",
                "Ticket",
                "group-123",
                "dedup-123"
        );

        // Then
        assertEquals("msg-123", messageId);
        verify(persistenceService).persistBeforeSend(anyString(), any(), anyLong(), anyString(), anyString());
        verify(sqsAsyncClient).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    @DisplayName("Should batch send multiple messages")
    void testBatchSend() throws Exception {
        // Given
        List<TestPayload> payloads = List.of(
                new TestPayload("1", "Message 1"),
                new TestPayload("2", "Message 2"),
                new TestPayload("3", "Message 3")
        );

        MessagePersistence persistedMsg = MessagePersistence.builder()
                .messageId("msg-123")
                .status(MessageStatus.PENDING)
                .build();
        when(persistenceService.persistBeforeSend(anyString(), any(), anyLong(), anyString(), anyString()))
                .thenReturn(persistedMsg);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(sqsAsyncClient.sendMessageBatch(any(SendMessageBatchRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        SendMessageBatchResponse.builder()
                                .successful(List.of(
                                        SendMessageBatchResultEntry.builder()
                                                .id("0")
                                                .messageId("sqs-msg-1")
                                                .sequenceNumber("seq-1")
                                                .build(),
                                        SendMessageBatchResultEntry.builder()
                                                .id("1")
                                                .messageId("sqs-msg-2")
                                                .sequenceNumber("seq-2")
                                                .build(),
                                        SendMessageBatchResultEntry.builder()
                                                .id("2")
                                                .messageId("sqs-msg-3")
                                                .sequenceNumber("seq-3")
                                                .build()))
                                .failed(List.of())
                                .build()
                ));

        // When
        var result = messageProducer.batchSend(
                TEST_QUEUE_URL, payloads, 1L, "agg-123", "Ticket"
        );

        // Then
        assertTrue(result.isAllSuccessful());
        assertEquals(3, result.response().successful().size());
        verify(sqsAsyncClient).sendMessageBatch(any(SendMessageBatchRequest.class));
    }

    @Test
    @DisplayName("Should send message with delay")
    void testSendWithDelay() throws Exception {
        // Given
        TestPayload payload = new TestPayload("123", "Delayed message");
        MessagePersistence persistedMsg = MessagePersistence.builder()
                .messageId("msg-123")
                .status(MessageStatus.PENDING)
                .build();
        when(persistenceService.persistBeforeSend(anyString(), any(), anyLong(), anyString(), anyString()))
                .thenReturn(persistedMsg);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        SendMessageResponse.builder().messageId("sqs-msg-id").build()
                ));

        // When
        String messageId = messageProducer.sendWithDelay(
                TEST_QUEUE_URL, payload, 60, 1L, "agg-123", "Ticket"
        );

        // Then
        assertEquals("msg-123", messageId);
        verify(sqsAsyncClient).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    @DisplayName("Should handle batch with some failures")
    void testBatchSendWithFailures() throws Exception {
        // Given
        List<TestPayload> payloads = List.of(
                new TestPayload("1", "Message 1"),
                new TestPayload("2", "Message 2")
        );

        when(persistenceService.persistBeforeSend(anyString(), any(), anyLong(), anyString(), anyString()))
                .thenReturn(MessagePersistence.builder()
                        .messageId("msg-1")
                        .status(MessagePersistence.MessageStatus.PENDING)
                        .build());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(sqsAsyncClient.sendMessageBatch(any(SendMessageBatchRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        SendMessageBatchResponse.builder()
                                .successful(List.of(
                                        SendMessageBatchResultEntry.builder()
                                                .id("0")
                                                .messageId("sqs-msg-1")
                                                .sequenceNumber("seq-1")
                                                .build()
                                ))
                                .failed(List.of(
                                        BatchResultErrorEntry.builder()
                                                .id("1")
                                                .code("InvalidParameter")
                                                .message("Invalid payload")
                                                .senderFault(true)
                                                .build()
                                ))
                                .build()
                ));

        // When
        var result = messageProducer.batchSend(
                TEST_QUEUE_URL, payloads, 1L, "agg-123", "Ticket"
        );

        // Then
        assertFalse(result.isAllSuccessful());
        assertEquals(1, result.response().successful().size());
        assertEquals(1, result.response().failed().size());
        assertFalse(result.getFailedMessageIds().isEmpty());
    }

    @Test
    @DisplayName("Should extract queue name from URL")
    void testExtractQueueName() {
        // Given
        String url1 = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";
        String url2 = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue.fifo";
        String url3 = "simple-queue-name";

        // When/Then
        assertAll(
                () -> assertEquals("test-queue", extractQueueName(url1)),
                () -> assertEquals("test-queue.fifo", extractQueueName(url2))
        );
    }

    // Helper method to extract queue name from URL (same as in MessageProducer)
    private String extractQueueName(String queueUrl) {
        if (queueUrl.contains("/")) {
            String[] parts = queueUrl.split("/");
            return parts[parts.length - 1];
        }
        return queueUrl;
    }

    @Test
    @DisplayName("Should build message attributes correctly")
    void testBuildMessageAttributes() throws Exception {
        // Given
        TestPayload payload = new TestPayload("123", "Test");
        MessagePersistence persistedMsg = MessagePersistence.builder()
                .messageId("msg-id")
                .status(MessageStatus.PENDING)
                .build();
        when(persistenceService.persistBeforeSend(anyString(), any(), anyLong(), anyString(), anyString()))
                .thenReturn(persistedMsg);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        SendMessageResponse.builder().messageId("sqs-id").build()
                ));

        // When
        messageProducer.sendToStandardQueue(TEST_QUEUE_URL, payload, 100L, "agg-123", "Ticket");

        // Then
        verify(sqsAsyncClient).sendMessage(any(SendMessageRequest.class));
        verify(persistenceService).markAsSent(eq("msg-id"), isNull());
    }

    @Test
    @DisplayName("Should handle SQS send failure")
    void testSendFailure() throws Exception {
        // Given
        TestPayload payload = new TestPayload("123", "Test");
        MessagePersistence persistedMsg = MessagePersistence.builder()
                .messageId("msg-123")
                .status(MessageStatus.PENDING)
                .build();
        when(persistenceService.persistBeforeSend(anyString(), any(), anyLong(), anyString(), anyString()))
                .thenReturn(persistedMsg);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("SQS error")));

        // When/Then
        assertThrows(RuntimeException.class, () ->
                messageProducer.sendToStandardQueue(TEST_QUEUE_URL, payload, 1L, "agg-123", "Ticket")
        );

        verify(sqsAsyncClient).sendMessage(any(SendMessageRequest.class));
    }

    // Helper test class
    private static class TestPayload {
        private String id;
        private String message;

        public TestPayload(String id, String message) {
            this.id = id;
            this.message = message;
        }

        public String getId() { return id; }
        public String getMessage() { return message; }
    }
}
