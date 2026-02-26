package com.example.awssqs.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ImMessageReconciliationService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ImMessageReconciliationServiceTest {

    @Mock
    private ImMessageRepository messageRepository;

    @Mock
    private ImMessageProducer messageProducer;

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    private ObjectMapper objectMapper;

    @InjectMocks
    private ImMessageReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(reconciliationService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(reconciliationService, "reconciliationEnabled", true);
        ReflectionTestUtils.setField(reconciliationService, "pendingTimeoutMinutes", 5);
        ReflectionTestUtils.setField(reconciliationService, "sentTimeoutMinutes", 10);
        ReflectionTestUtils.setField(reconciliationService, "processingTimeoutMinutes", 15);
        ReflectionTestUtils.setField(reconciliationService, "batchSize", 100);
    }

    @Test
    void testPerformReconciliation_enabled() {
        // Arrange
        when(messageRepository.findRetryableMessages(any(), any())).thenReturn(Collections.emptyList());
        when(messageRepository.findMessagesInDlq(any())).thenReturn(Collections.emptyList());

        // Act
        reconciliationService.performReconciliation();

        // Assert
        verify(messageRepository, atLeastOnce()).findRetryableMessages(any(), any());
    }

    @Test
    void testPerformReconciliation_disabled() {
        // Arrange
        ReflectionTestUtils.setField(reconciliationService, "reconciliationEnabled", false);

        // Act
        reconciliationService.performReconciliation();

        // Assert
        verify(messageRepository, never()).findRetryableMessages(any(), any());
    }

    @Test
    void testCheckPendingTimeout_withTimeoutMessages() {
        // Arrange
        List<ImMessagePersistence> timeoutMessages = new ArrayList<>();
        timeoutMessages.add(ImMessagePersistence.builder()
                .messageId("msg-1")
                .status(ImMessagePersistence.ImMessageStatus.PERSISTED)
                .payload("{\"messageId\":\"msg-1\"}")
                .retryCount(0)
                .maxRetryCount(5)
                .manualIntervention(false)
                .inDlq(false)
                .build());

        when(messageRepository.findRetryableMessages(any(), any())).thenReturn(timeoutMessages);
        when(messageRepository.findMessagesInDlq(any())).thenReturn(Collections.emptyList());
        when(messageRepository.save(any(ImMessagePersistence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        reconciliationService.performReconciliation();

        // Assert
        verify(messageRepository, atLeastOnce()).findRetryableMessages(any(), any());
    }

    @Test
    void testCheckDlqMessages_withMessages() {
        // Arrange
        List<ImMessagePersistence> dlqMessages = new ArrayList<>();
        dlqMessages.add(ImMessagePersistence.builder()
                .messageId("msg-dlq-1")
                .status(ImMessagePersistence.ImMessageStatus.IN_DLQ)
                .inDlq(true)
                .retryCount(2)
                .maxRetryCount(5)
                .manualIntervention(false)
                .payload("{\"messageId\":\"msg-dlq-1\"}")
                .build());

        when(messageRepository.findRetryableMessages(any(), any())).thenReturn(Collections.emptyList());
        when(messageRepository.findMessagesInDlq(ImMessagePersistence.ImMessageStatus.IN_DLQ)).thenReturn(dlqMessages);
        when(messageRepository.save(any(ImMessagePersistence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        reconciliationService.performReconciliation();

        // Assert
        verify(messageRepository).findMessagesInDlq(ImMessagePersistence.ImMessageStatus.IN_DLQ);
    }

    @Test
    void testManualReconciliation_success() {
        // Arrange
        when(messageRepository.findRetryableMessages(any(), any())).thenReturn(Collections.emptyList());
        when(messageRepository.findMessagesInDlq(any())).thenReturn(Collections.emptyList());

        // Act
        ImMessageReconciliationService.ReconciliationResult result = reconciliationService.manualReconciliation();

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("Manual reconciliation completed", result.getMessage());
    }

    @Test
    void testGetReconciliationStats() {
        // Arrange
        when(messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.RECEIVED_FROM_CLIENT)).thenReturn(10L);
        when(messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.PERSISTED)).thenReturn(5L);
        when(messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.SENDING_TO_SQS)).thenReturn(2L);
        when(messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.SENT_TO_SQS)).thenReturn(20L);
        when(messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.PROCESSING)).thenReturn(3L);
        when(messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.PROCESSED)).thenReturn(15L);
        when(messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED)).thenReturn(50L);
        when(messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.PROCESSING_FAILED)).thenReturn(2L);
        when(messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.IN_DLQ)).thenReturn(1L);
        when(messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.LOST)).thenReturn(0L);

        // Act
        ImMessageReconciliationService.ReconciliationStats stats = reconciliationService.getReconciliationStats();

        // Assert
        assertEquals(108L, stats.getTotalMessages());
        assertEquals(50L, stats.getAcknowledgedCount());
        assertEquals(1L, stats.getDlqCount());
        assertEquals(0L, stats.getLostCount());
    }

    @Test
    void testGetReconciliationStats_successRate() {
        // Arrange
        when(messageRepository.countByStatus(any())).thenReturn(0L);
        when(messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED)).thenReturn(90L);
        when(messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.PROCESSED)).thenReturn(5L);
        when(messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.SENT_TO_SQS)).thenReturn(5L);

        // Act
        ImMessageReconciliationService.ReconciliationStats stats = reconciliationService.getReconciliationStats();

        // Assert
        assertTrue(stats.getSuccessRate() > 0);
    }

    @Test
    void testGetReconciliationStats_emptyRepository() {
        // Arrange
        when(messageRepository.countByStatus(any())).thenReturn(0L);

        // Act
        ImMessageReconciliationService.ReconciliationStats stats = reconciliationService.getReconciliationStats();

        // Assert
        assertEquals(0L, stats.getTotalMessages());
        assertEquals(100.0, stats.getSuccessRate()); // 0 total messages = 100% success rate
        assertEquals(0.0, stats.getLossRate());
    }

    @Test
    void testCheckProcessingTimeout_withStuckMessages() {
        // Arrange
        List<ImMessagePersistence> stuckMessages = new ArrayList<>();
        stuckMessages.add(ImMessagePersistence.builder()
                .messageId("msg-stuck-1")
                .status(ImMessagePersistence.ImMessageStatus.PROCESSING)
                .payload("{\"messageId\":\"msg-stuck-1\"}")
                .retryCount(1)
                .maxRetryCount(5)
                .manualIntervention(false)
                .inDlq(false)
                .build());

        when(messageRepository.findRetryableMessages(any(), any())).thenReturn(stuckMessages);
        when(messageRepository.findMessagesInDlq(any())).thenReturn(Collections.emptyList());
        when(messageRepository.save(any(ImMessagePersistence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        reconciliationService.performReconciliation();

        // Assert - should have attempted to process stuck messages
        verify(messageRepository, atLeastOnce()).findRetryableMessages(any(), any());
    }

    @Test
    void testCheckSentTimeout_withUnconfirmedMessages() {
        // Arrange
        List<ImMessagePersistence> unconfirmedMessages = new ArrayList<>();
        unconfirmedMessages.add(ImMessagePersistence.builder()
                .messageId("msg-unconfirmed-1")
                .status(ImMessagePersistence.ImMessageStatus.SENT_TO_SQS)
                .payload("{\"messageId\":\"msg-unconfirmed-1\"}")
                .retryCount(0)
                .maxRetryCount(5)
                .manualIntervention(false)
                .inDlq(false)
                .build());

        when(messageRepository.findRetryableMessages(any(), any())).thenReturn(unconfirmedMessages);
        when(messageRepository.findMessagesInDlq(any())).thenReturn(Collections.emptyList());
        when(messageRepository.save(any(ImMessagePersistence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        reconciliationService.performReconciliation();

        // Assert
        verify(messageRepository, atLeastOnce()).findRetryableMessages(any(), any());
    }

    @Test
    void testReconciliationStats_lossRate() {
        // Arrange
        when(messageRepository.countByStatus(any())).thenReturn(0L);
        when(messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.LOST)).thenReturn(5L);
        when(messageRepository.countByStatus(ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED)).thenReturn(95L);

        // Act
        ImMessageReconciliationService.ReconciliationStats stats = reconciliationService.getReconciliationStats();

        // Assert
        assertEquals(100L, stats.getTotalMessages());
        assertEquals(5.0, stats.getLossRate());
    }

    @Test
    void testPerformReconciliation_withException() {
        // Arrange
        when(messageRepository.findRetryableMessages(any(), any()))
                .thenThrow(new RuntimeException("Database error"));

        // Act - should not throw
        reconciliationService.performReconciliation();

        // Assert - exception is caught and logged
        verify(messageRepository).findRetryableMessages(any(), any());
    }

    @Test
    void testManualReconciliation_withException() {
        // Arrange
        when(messageRepository.findRetryableMessages(any(), any()))
                .thenThrow(new RuntimeException("Database error"));

        // Act
        ImMessageReconciliationService.ReconciliationResult result = reconciliationService.manualReconciliation();

        // Assert
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("failed"));
    }

    @Test
    void testReconciliationResult_builder() {
        // Act
        java.util.Map<String, Integer> stats = new java.util.HashMap<>();
        stats.put("retried", 5);
        stats.put("lost", 1);

        ImMessageReconciliationService.ReconciliationResult result = ImMessageReconciliationService.ReconciliationResult.builder()
                .success(true)
                .message("Test")
                .stats(stats)
                .build();

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("Test", result.getMessage());
        assertNotNull(result.getStats());
        assertEquals(5, result.getStats().get("retried"));
        assertEquals(1, result.getStats().get("lost"));
    }
}
