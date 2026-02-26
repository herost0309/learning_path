package com.example.awssqs.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ImMessageConsumer 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImMessageConsumerTest {

    @Mock
    private ImMessageRepository messageRepository;

    @Mock
    private ImMessageProducer messageProducer;

    @Mock
    private ImMessageDeliveryService deliveryService;

    @Mock
    private Acknowledgement acknowledgement;

    private ObjectMapper objectMapper;

    @InjectMocks
    private ImMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        ReflectionTestUtils.setField(consumer, "objectMapper", objectMapper);
    }

    @Test
    void testConsumeImMessage_success() throws Exception {
        // Arrange
        ImMessage message = createTestMessage();
        String messageBody = objectMapper.writeValueAsString(message);

        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("msg-123")
                .status(ImMessagePersistence.ImMessageStatus.SENT_TO_SQS)
                .build();

        when(messageRepository.findByMessageId("msg-123")).thenReturn(Optional.empty(), Optional.of(persistence));
        when(messageRepository.findByClientMessageId("client-msg-123")).thenReturn(Optional.empty());
        when(messageRepository.save(any(ImMessagePersistence.class))).thenReturn(persistence);

        doNothing().when(deliveryService).deliverToReceiver(any(ImMessage.class));
        doNothing().when(messageProducer).confirmMessageProcessed(anyString());

        // Act
        consumer.consumeImMessage(
                messageBody,
                "msg-123",
                "client-msg-123",
                "conv-456",
                "user-001",
                "user-002",
                "TEXT",
                1,
                System.currentTimeMillis(),
                acknowledgement
        );

        // Assert
        verify(acknowledgement).acknowledge();
        verify(messageProducer).confirmMessageProcessed("msg-123");
        verify(deliveryService).deliverToReceiver(any(ImMessage.class));
    }

    @Test
    void testConsumeImMessage_alreadyProcessed() throws Exception {
        // Arrange
        ImMessage message = createTestMessage();
        String messageBody = objectMapper.writeValueAsString(message);

        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("msg-123")
                .status(ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED)
                .build();

        // Both checks should return the already processed persistence
        when(messageRepository.findByMessageId("msg-123")).thenReturn(Optional.of(persistence));
        when(messageRepository.findByClientMessageId("client-msg-123")).thenReturn(Optional.of(persistence));

        // Act
        consumer.consumeImMessage(
                messageBody,
                "msg-123",
                "client-msg-123",
                "conv-456",
                "user-001",
                "user-002",
                "TEXT",
                1,
                System.currentTimeMillis(),
                acknowledgement
        );

        // Assert
        verify(acknowledgement).acknowledge();
        verify(deliveryService, never()).deliverToReceiver(any(ImMessage.class));
    }

    @Test
    void testConsumeImMessage_expired() throws Exception {
        // Arrange
        ImMessage message = ImMessage.builder()
                .messageId("msg-123")
                .clientMessageId("client-msg-123")
                .conversationId("conv-456")
                .senderId("user-001")
                .receiverId("user-002")
                .messageType(ImMessage.MessageType.TEXT)
                .content("Hello")
                .timestamp(System.currentTimeMillis() - 600000) // 10 minutes ago
                .expireInSeconds(300) // 5 minutes
                .build();

        String messageBody = objectMapper.writeValueAsString(message);

        when(messageRepository.findByMessageId("msg-123")).thenReturn(Optional.empty());
        when(messageRepository.findByClientMessageId("client-msg-123")).thenReturn(Optional.empty());

        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("msg-123")
                .status(ImMessagePersistence.ImMessageStatus.EXPIRED)
                .build();
        when(messageRepository.save(any(ImMessagePersistence.class))).thenReturn(persistence);

        // Act
        consumer.consumeImMessage(
                messageBody,
                "msg-123",
                "client-msg-123",
                "conv-456",
                "user-001",
                "user-002",
                "TEXT",
                1,
                System.currentTimeMillis(),
                acknowledgement
        );

        // Assert
        verify(acknowledgement).acknowledge();
        verify(deliveryService, never()).deliverToReceiver(any(ImMessage.class));
    }

    @Test
    void testConsumeImMessage_processingFailure() throws Exception {
        // Arrange
        ImMessage message = createTestMessage();
        String messageBody = objectMapper.writeValueAsString(message);

        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("msg-123")
                .status(ImMessagePersistence.ImMessageStatus.PROCESSING_FAILED)
                .build();

        when(messageRepository.findByMessageId("msg-123")).thenReturn(Optional.empty(), Optional.of(persistence));
        when(messageRepository.findByClientMessageId("client-msg-123")).thenReturn(Optional.empty());
        when(messageRepository.save(any(ImMessagePersistence.class))).thenReturn(persistence);

        doThrow(new RuntimeException("Delivery failed"))
                .when(deliveryService).deliverToReceiver(any(ImMessage.class));

        // Act
        consumer.consumeImMessage(
                messageBody,
                "msg-123",
                "client-msg-123",
                "conv-456",
                "user-001",
                "user-002",
                "TEXT",
                1,
                System.currentTimeMillis(),
                acknowledgement
        );

        // Assert - should not acknowledge on failure (unless max retries reached)
        verify(acknowledgement, never()).acknowledge();
    }

    @Test
    void testConsumeImMessage_maxRetriesReached() throws Exception {
        // Arrange
        ImMessage message = createTestMessage();
        String messageBody = objectMapper.writeValueAsString(message);

        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("msg-123")
                .status(ImMessagePersistence.ImMessageStatus.PROCESSING_FAILED)
                .build();

        when(messageRepository.findByMessageId("msg-123")).thenReturn(Optional.empty(), Optional.of(persistence));
        when(messageRepository.findByClientMessageId("client-msg-123")).thenReturn(Optional.empty());
        when(messageRepository.save(any(ImMessagePersistence.class))).thenReturn(persistence);

        doThrow(new RuntimeException("Delivery failed"))
                .when(deliveryService).deliverToReceiver(any(ImMessage.class));

        // Act - receiveCount = 5 (max retries)
        consumer.consumeImMessage(
                messageBody,
                "msg-123",
                "client-msg-123",
                "conv-456",
                "user-001",
                "user-002",
                "TEXT",
                5, // max retries reached
                System.currentTimeMillis(),
                acknowledgement
        );

        // Assert - should acknowledge to move to DLQ
        verify(acknowledgement).acknowledge();
    }

    @Test
    void testConsumeImMessage_systemMessage() throws Exception {
        // Arrange
        ImMessage message = ImMessage.builder()
                .messageId("msg-123")
                .conversationId("conv-456")
                .senderId("system")
                .messageType(ImMessage.MessageType.SYSTEM)
                .content("System notification")
                .timestamp(System.currentTimeMillis())
                .build();

        String messageBody = objectMapper.writeValueAsString(message);

        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("msg-123")
                .status(ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED)
                .build();

        when(messageRepository.findByMessageId("msg-123")).thenReturn(Optional.empty(), Optional.of(persistence));
        when(messageRepository.save(any(ImMessagePersistence.class))).thenReturn(persistence);

        doNothing().when(deliveryService).deliverSystemMessage(any(ImMessage.class));
        doNothing().when(messageProducer).confirmMessageProcessed(anyString());

        // Act
        consumer.consumeImMessage(
                messageBody,
                "msg-123",
                null,
                "conv-456",
                "system",
                null,
                "SYSTEM",
                1,
                System.currentTimeMillis(),
                acknowledgement
        );

        // Assert
        verify(deliveryService).deliverSystemMessage(any(ImMessage.class));
        verify(acknowledgement).acknowledge();
    }

    @Test
    void testConsumeImMessage_recallMessage() throws Exception {
        // Arrange
        ImMessage message = ImMessage.builder()
                .messageId("msg-recall-123")
                .conversationId("conv-456")
                .senderId("user-001")
                .messageType(ImMessage.MessageType.RECALL)
                .content("original-msg-123")
                .timestamp(System.currentTimeMillis())
                .build();

        String messageBody = objectMapper.writeValueAsString(message);

        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("msg-recall-123")
                .status(ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED)
                .build();

        when(messageRepository.findByMessageId("msg-recall-123")).thenReturn(Optional.empty(), Optional.of(persistence));
        when(messageRepository.save(any(ImMessagePersistence.class))).thenReturn(persistence);

        doNothing().when(deliveryService).handleRecallMessage(any(ImMessage.class));
        doNothing().when(messageProducer).confirmMessageProcessed(anyString());

        // Act
        consumer.consumeImMessage(
                messageBody,
                "msg-recall-123",
                null,
                "conv-456",
                "user-001",
                null,
                "RECALL",
                1,
                System.currentTimeMillis(),
                acknowledgement
        );

        // Assert
        verify(deliveryService).handleRecallMessage(any(ImMessage.class));
        verify(acknowledgement).acknowledge();
    }

    @Test
    void testConsumeImMessage_typingStatus() throws Exception {
        // Arrange
        ImMessage message = ImMessage.builder()
                .messageId("msg-typing-123")
                .conversationId("conv-456")
                .senderId("user-001")
                .messageType(ImMessage.MessageType.TYPING)
                .timestamp(System.currentTimeMillis())
                .build();

        String messageBody = objectMapper.writeValueAsString(message);

        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("msg-typing-123")
                .status(ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED)
                .build();

        when(messageRepository.findByMessageId("msg-typing-123")).thenReturn(Optional.empty(), Optional.of(persistence));
        when(messageRepository.save(any(ImMessagePersistence.class))).thenReturn(persistence);

        doNothing().when(deliveryService).pushTypingStatus(any(ImMessage.class));
        doNothing().when(messageProducer).confirmMessageProcessed(anyString());

        // Act
        consumer.consumeImMessage(
                messageBody,
                "msg-typing-123",
                null,
                "conv-456",
                "user-001",
                null,
                "TYPING",
                1,
                System.currentTimeMillis(),
                acknowledgement
        );

        // Assert
        verify(deliveryService).pushTypingStatus(any(ImMessage.class));
        verify(acknowledgement).acknowledge();
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
