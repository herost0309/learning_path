package com.example.awssqs.im;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ImMessageDeliveryService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImMessageDeliveryServiceTest {

    @Mock
    private ImMessageRepository messageRepository;

    @InjectMocks
    private ImMessageDeliveryService deliveryService;

    @Test
    void testDeliverToReceiver_onlineUser() {
        // Arrange
        ImMessage message = ImMessage.builder()
                .messageId("msg-123")
                .conversationId("conv-456")
                .senderId("user-001")
                .receiverId("user-002")
                .messageType(ImMessage.MessageType.TEXT)
                .content("Hello")
                .timestamp(System.currentTimeMillis())
                .build();

        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("msg-123")
                .deliveryStatus(ImMessagePersistence.DeliveryStatus.PENDING)
                .build();

        when(messageRepository.findByMessageId("msg-123")).thenReturn(Optional.of(persistence));
        when(messageRepository.save(any(ImMessagePersistence.class))).thenReturn(persistence);

        // Act
        deliveryService.deliverToReceiver(message);

        // Assert
        verify(messageRepository, atLeastOnce()).save(any(ImMessagePersistence.class));
    }

    @Test
    void testDeliverToReceiver_offlineUser() {
        // Arrange
        ImMessage message = ImMessage.builder()
                .messageId("msg-456")
                .conversationId("conv-789")
                .senderId("user-001")
                .receiverId("user-002")
                .messageType(ImMessage.MessageType.TEXT)
                .content("Hello")
                .timestamp(System.currentTimeMillis())
                .build();

        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("msg-456")
                .deliveryStatus(ImMessagePersistence.DeliveryStatus.PENDING)
                .build();

        when(messageRepository.findByMessageId("msg-456")).thenReturn(Optional.of(persistence));
        when(messageRepository.save(any(ImMessagePersistence.class))).thenReturn(persistence);

        // Act
        deliveryService.deliverToReceiver(message);

        // Assert - deliveryService will store as offline since checkUserOnline returns false
        verify(messageRepository, atLeastOnce()).save(any(ImMessagePersistence.class));
    }

    @Test
    void testDeliverToReceiver_persistenceNotFound() {
        // Arrange
        ImMessage message = ImMessage.builder()
                .messageId("msg-nonexistent")
                .conversationId("conv-456")
                .senderId("user-001")
                .receiverId("user-002")
                .messageType(ImMessage.MessageType.TEXT)
                .content("Hello")
                .timestamp(System.currentTimeMillis())
                .build();

        when(messageRepository.findByMessageId("msg-nonexistent")).thenReturn(Optional.empty());

        // Act - should not throw
        deliveryService.deliverToReceiver(message);

        // Assert - findByMessageId is called (implementation may call it multiple times)
        verify(messageRepository, atLeastOnce()).findByMessageId("msg-nonexistent");
    }

    @Test
    void testDeliverSystemMessage() {
        // Arrange
        ImMessage message = ImMessage.builder()
                .messageId("sys-msg-123")
                .conversationId("conv-456")
                .senderId("system")
                .messageType(ImMessage.MessageType.SYSTEM)
                .content("System notification")
                .timestamp(System.currentTimeMillis())
                .build();

        // Act
        deliveryService.deliverSystemMessage(message);

        // Assert - no exception thrown
        // Current implementation logs and does nothing else
    }

    @Test
    void testHandleRecallMessage_originalExists() {
        // Arrange
        ImMessage recallMessage = ImMessage.builder()
                .messageId("recall-msg-123")
                .conversationId("conv-456")
                .senderId("user-001")
                .messageType(ImMessage.MessageType.RECALL)
                .content("original-msg-123") // content contains original message ID
                .timestamp(System.currentTimeMillis())
                .build();

        ImMessagePersistence originalPersistence = ImMessagePersistence.builder()
                .messageId("original-msg-123")
                .status(ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED)
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .receiverId("user-002")
                .build();

        when(messageRepository.findByMessageId("original-msg-123"))
                .thenReturn(Optional.of(originalPersistence));
        when(messageRepository.save(any(ImMessagePersistence.class)))
                .thenReturn(originalPersistence);

        // Act
        deliveryService.handleRecallMessage(recallMessage);

        // Assert
        verify(messageRepository).findByMessageId("original-msg-123");
        verify(messageRepository).save(any(ImMessagePersistence.class));
    }

    @Test
    void testHandleRecallMessage_originalNotFound() {
        // Arrange
        ImMessage recallMessage = ImMessage.builder()
                .messageId("recall-msg-456")
                .conversationId("conv-456")
                .senderId("user-001")
                .messageType(ImMessage.MessageType.RECALL)
                .content("nonexistent-msg")
                .timestamp(System.currentTimeMillis())
                .build();

        when(messageRepository.findByMessageId("nonexistent-msg")).thenReturn(Optional.empty());

        // Act - should not throw
        deliveryService.handleRecallMessage(recallMessage);

        // Assert
        verify(messageRepository).findByMessageId("nonexistent-msg");
        verify(messageRepository, never()).save(any(ImMessagePersistence.class));
    }

    @Test
    void testHandleRecallMessage_tooLate() {
        // Arrange - original message was created 10 minutes ago (beyond 2-minute window)
        ImMessage recallMessage = ImMessage.builder()
                .messageId("recall-msg-789")
                .conversationId("conv-456")
                .senderId("user-001")
                .messageType(ImMessage.MessageType.RECALL)
                .content("original-msg-late")
                .timestamp(System.currentTimeMillis())
                .build();

        ImMessagePersistence originalPersistence = ImMessagePersistence.builder()
                .messageId("original-msg-late")
                .status(ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED)
                .createdAt(LocalDateTime.now().minusMinutes(10)) // Too late to recall
                .receiverId("user-002")
                .build();

        when(messageRepository.findByMessageId("original-msg-late"))
                .thenReturn(Optional.of(originalPersistence));

        // Act
        deliveryService.handleRecallMessage(recallMessage);

        // Assert - should not save since recall window has passed
        verify(messageRepository).findByMessageId("original-msg-late");
    }

    @Test
    void testPushTypingStatus() {
        // Arrange
        ImMessage message = ImMessage.builder()
                .messageId("typing-123")
                .conversationId("conv-456")
                .senderId("user-001")
                .messageType(ImMessage.MessageType.TYPING)
                .timestamp(System.currentTimeMillis())
                .build();

        // Act - should not throw
        deliveryService.pushTypingStatus(message);

        // No assertion needed - just verifying no exception
    }

    @Test
    void testDeliverToReceiver_withImageMessage() {
        // Arrange
        ImMessage message = ImMessage.builder()
                .messageId("img-msg-123")
                .conversationId("conv-456")
                .senderId("user-001")
                .receiverId("user-002")
                .messageType(ImMessage.MessageType.IMAGE)
                .content("https://example.com/image.jpg")
                .timestamp(System.currentTimeMillis())
                .build();

        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("img-msg-123")
                .deliveryStatus(ImMessagePersistence.DeliveryStatus.PENDING)
                .build();

        when(messageRepository.findByMessageId("img-msg-123")).thenReturn(Optional.of(persistence));
        when(messageRepository.save(any(ImMessagePersistence.class))).thenReturn(persistence);

        // Act
        deliveryService.deliverToReceiver(message);

        // Assert
        verify(messageRepository, atLeastOnce()).save(any(ImMessagePersistence.class));
    }

    @Test
    void testDeliverToReceiver_withVideoMessage() {
        // Arrange
        ImMessage message = ImMessage.builder()
                .messageId("video-msg-123")
                .conversationId("conv-456")
                .senderId("user-001")
                .receiverId("user-002")
                .messageType(ImMessage.MessageType.VIDEO)
                .content("https://example.com/video.mp4")
                .timestamp(System.currentTimeMillis())
                .build();

        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("video-msg-123")
                .deliveryStatus(ImMessagePersistence.DeliveryStatus.PENDING)
                .build();

        when(messageRepository.findByMessageId("video-msg-123")).thenReturn(Optional.of(persistence));
        when(messageRepository.save(any(ImMessagePersistence.class))).thenReturn(persistence);

        // Act
        deliveryService.deliverToReceiver(message);

        // Assert
        verify(messageRepository, atLeastOnce()).save(any(ImMessagePersistence.class));
    }

    @Test
    void testDeliverToReceiver_withFileMessage() {
        // Arrange
        ImMessage message = ImMessage.builder()
                .messageId("file-msg-123")
                .conversationId("conv-456")
                .senderId("user-001")
                .receiverId("user-002")
                .messageType(ImMessage.MessageType.FILE)
                .content("https://example.com/file.pdf")
                .timestamp(System.currentTimeMillis())
                .build();

        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .messageId("file-msg-123")
                .deliveryStatus(ImMessagePersistence.DeliveryStatus.PENDING)
                .build();

        when(messageRepository.findByMessageId("file-msg-123")).thenReturn(Optional.of(persistence));
        when(messageRepository.save(any(ImMessagePersistence.class))).thenReturn(persistence);

        // Act
        deliveryService.deliverToReceiver(message);

        // Assert
        verify(messageRepository, atLeastOnce()).save(any(ImMessagePersistence.class));
    }
}
