package com.example.awssqs.producer;

import com.example.awssqs.domain.MessagePersistence;
import com.example.awssqs.domain.MessagePersistence.MessageStatus;
import com.example.awssqs.repository.MessagePersistenceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test for MessagePersistenceService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MessagePersistenceService Tests")
class MessagePersistenceServiceTest {

    @Mock
    private MessagePersistenceRepository messagePersistenceRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MessagePersistenceService messagePersistenceService;

    private MessagePersistence testMessage;

    @BeforeEach
    void setUp() {
        testMessage = MessagePersistence.builder()
                .messageId("test-msg-id")
                .queueName("test-queue")
                .payloadType("TestPayload")
                .payload("{}")
                .status(MessageStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .retryCount(0)
                .build();
    }

    @Test
    @DisplayName("Should persist message with PENDING status")
    void testPersistBeforeSend() throws Exception {
        // Given
        Object payload = new Object() {
            public final String id = "123";
        };
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":\"123\"}");
        when(messagePersistenceRepository.save(any(MessagePersistence.class)))
                .thenReturn(testMessage);

        // When
        MessagePersistence result = messagePersistenceService.persistBeforeSend(
                "test-queue", payload, 1L, "agg-123", "Ticket"
        );

        // Then
        assertNotNull(result);
        assertEquals(MessageStatus.PENDING, result.getStatus());
        verify(messagePersistenceRepository).save(any(MessagePersistence.class));
    }

    @Test
    @DisplayName("Should mark message as SENT")
    void testMarkAsSent() {
        // Given
        when(messagePersistenceRepository.findByMessageId("test-msg-id"))
                .thenReturn(Optional.of(testMessage));
        when(messagePersistenceRepository.save(any(MessagePersistence.class)))
                .thenReturn(testMessage);

        // When
        messagePersistenceService.markAsSent("test-msg-id", "receipt-123");

        // Then
        verify(messagePersistenceRepository).findByMessageId("test-msg-id");
        verify(messagePersistenceRepository).save(argThat(msg ->
                msg.getStatus() == MessageStatus.SENT &&
                        "receipt-123".equals(msg.getReceiptHandle())
        ));
    }

    @Test
    @DisplayName("Should mark message as RECEIVED")
    void testMarkAsReceived() {
        // Given
        when(messagePersistenceRepository.findByMessageId("test-msg-id"))
                .thenReturn(Optional.of(testMessage));
        when(messagePersistenceRepository.save(any(MessagePersistence.class)))
                .thenReturn(testMessage);

        // When
        messagePersistenceService.markAsReceived("test-msg-id");

        // Then
        verify(messagePersistenceRepository).save(argThat(msg ->
                msg.getStatus() == MessageStatus.RECEIVED
        ));
    }

    @Test
    @DisplayName("Should mark message as PROCESSING")
    void testMarkAsProcessing() {
        // Given
        when(messagePersistenceRepository.findByMessageId("test-msg-id"))
                .thenReturn(Optional.of(testMessage));
        when(messagePersistenceRepository.save(any(MessagePersistence.class)))
                .thenReturn(testMessage);

        // When
        messagePersistenceService.markAsProcessing("test-msg-id");

        // Then
        verify(messagePersistenceRepository).save(argThat(msg ->
                msg.getStatus() == MessageStatus.PROCESSING
        ));
    }

    @Test
    @DisplayName("Should mark message as PROCESSED")
    void testMarkAsProcessed() {
        // Given
        when(messagePersistenceRepository.findByMessageId("test-msg-id"))
                .thenReturn(Optional.of(testMessage));
        when(messagePersistenceRepository.save(any(MessagePersistence.class)))
                .thenReturn(testMessage);

        // When
        messagePersistenceService.markAsProcessed("test-msg-id");

        // Then
        verify(messagePersistenceRepository).save(argThat(msg ->
                msg.getStatus() == MessageStatus.PROCESSED
        ));
    }

    @Test
    @DisplayName("Should mark message as ACKNOWLEDGED")
    void testMarkAsAcknowledged() {
        // Given
        when(messagePersistenceRepository.findByMessageId("test-msg-id"))
                .thenReturn(Optional.of(testMessage));
        when(messagePersistenceRepository.save(any(MessagePersistence.class)))
                .thenReturn(testMessage);

        // When
        messagePersistenceService.markAsAcknowledged("test-msg-id");

        // Then
        verify(messagePersistenceRepository).save(argThat(msg ->
                msg.getStatus() == MessageStatus.ACKNOWLEDGED
        ));
    }

    @Test
    @DisplayName("Should mark message as FAILED with error message")
    void testMarkAsFailed() {
        // Given
        String errorMessage = "Processing error occurred";
        when(messagePersistenceRepository.findByMessageId("test-msg-id"))
                .thenReturn(Optional.of(testMessage));
        when(messagePersistenceRepository.save(any(MessagePersistence.class)))
                .thenReturn(testMessage);

        // When
        messagePersistenceService.markAsFailed("test-msg-id", errorMessage);

        // Then
        verify(messagePersistenceRepository).save(argThat(msg ->
                msg.getStatus() == MessageStatus.FAILED &&
                        errorMessage.equals(msg.getErrorMessage())
        ));
    }

    @Test
    @DisplayName("Should mark message as in DLQ")
    void testMarkAsInDlq() {
        // Given
        when(messagePersistenceRepository.findByMessageId("test-msg-id"))
                .thenReturn(Optional.of(testMessage));
        when(messagePersistenceRepository.save(any(MessagePersistence.class)))
                .thenReturn(testMessage);

        // When
        messagePersistenceService.markAsInDlq("test-msg-id", 3);

        // Then
        verify(messagePersistenceRepository).save(argThat(msg ->
                msg.getInDlq() &&
                        msg.getDlqReceiveCount() == 3
        ));
    }

    @Test
    @DisplayName("Should mark message for manual intervention")
    void testMarkAsManualIntervention() {
        // Given
        when(messagePersistenceRepository.findByMessageId("test-msg-id"))
                .thenReturn(Optional.of(testMessage));
        when(messagePersistenceRepository.save(any(MessagePersistence.class)))
                .thenReturn(testMessage);

        // When
        messagePersistenceService.markAsManualIntervention("test-msg-id");

        // Then
        verify(messagePersistenceRepository).save(argThat(msg ->
                msg.getManualIntervention()
        ));
    }

    @Test
    @DisplayName("Should mark message as REPLAYED and increment retry count")
    void testMarkAsReplayed() {
        // Given
        testMessage.setRetryCount(2);
        when(messagePersistenceRepository.findByMessageId("test-msg-id"))
                .thenReturn(Optional.of(testMessage));
        when(messagePersistenceRepository.save(any(MessagePersistence.class)))
                .thenReturn(testMessage);

        // When
        messagePersistenceService.markAsReplayed("test-msg-id");

        // Then
        verify(messagePersistenceRepository).save(argThat(msg ->
                msg.getStatus() == MessageStatus.REPLAYED &&
                        msg.getRetryCount() == 3 &&
                        msg.getReplayedAt() != null
        ));
    }

    @Test
    @DisplayName("Should mark message as LOST")
    void testMarkAsLost() {
        // Given
        String reason = "Timeout after 30 minutes";
        when(messagePersistenceRepository.findByMessageId("test-msg-id"))
                .thenReturn(Optional.of(testMessage));
        when(messagePersistenceRepository.save(any(MessagePersistence.class)))
                .thenReturn(testMessage);

        // When
        messagePersistenceService.markAsLost("test-msg-id", reason);

        // Then
        verify(messagePersistenceRepository).save(argThat(msg ->
                msg.getStatus() == MessageStatus.LOST &&
                        msg.getErrorMessage().startsWith("LOST:")
        ));
    }

    @Test
    @DisplayName("Should return false for non-duplicate message")
    void testIsDuplicateReturnsFalse() {
        // Given
        when(messagePersistenceRepository.findByMessageId("dedup-key"))
                .thenReturn(Optional.empty());

        // When
        boolean result = messagePersistenceService.isDuplicate("test-queue", "dedup-key");

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Should return true for duplicate message")
    void testIsDuplicateReturnsTrue() {
        // Given
        when(messagePersistenceRepository.findByMessageId("dedup-key"))
                .thenReturn(Optional.of(testMessage));

        // When
        boolean result = messagePersistenceService.isDuplicate("test-queue", "dedup-key");

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Should generate valid message ID")
    void testGenerateMessageId() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(messagePersistenceRepository.save(any(MessagePersistence.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        String messageId = messagePersistenceService.persistBeforeSend(
                "test-queue", new Object(), null, null, null
        ).getMessageId();

        // Then
        assertTrue(messageId.startsWith("test-queue-"));
    }

    @Test
    @DisplayName("Should handle missing message when marking as sent")
    void testMarkAsSentWithMissingMessage() {
        // Given
        when(messagePersistenceRepository.findByMessageId("nonexistent-id"))
                .thenReturn(Optional.empty());

        // When/Then - should not throw exception
        assertDoesNotThrow(() -> {
            messagePersistenceService.markAsSent("nonexistent-id", "receipt-123");
        });

        verify(messagePersistenceRepository, never()).save(any());
    }
}
