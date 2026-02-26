package com.example.awssqs.im;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ImMessagePersistence 单元测试
 */
class ImMessagePersistenceTest {

    @Test
    void testBuilder() {
        LocalDateTime now = LocalDateTime.now();

        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .id(1L)
                .messageId("msg-123")
                .clientMessageId("client-msg-123")
                .conversationId("conv-456")
                .senderId("user-001")
                .receiverId("user-002")
                .messageType("TEXT")
                .payload("{\"content\":\"Hello\"}")
                .status(ImMessagePersistence.ImMessageStatus.PERSISTED)
                .ackStatus(ImMessagePersistence.AckStatus.PENDING)
                .deliveryStatus(ImMessagePersistence.DeliveryStatus.PENDING)
                .priority("NORMAL")
                .createdAt(now)
                .persistedAt(now)
                .retryCount(0)
                .maxRetryCount(5)
                .inDlq(false)
                .manualIntervention(false)
                .tenantId(1L)
                .deviceId("device-001")
                .sequenceNumber(1L)
                .build();

        assertEquals(1L, persistence.getId());
        assertEquals("msg-123", persistence.getMessageId());
        assertEquals("client-msg-123", persistence.getClientMessageId());
        assertEquals("conv-456", persistence.getConversationId());
        assertEquals("user-001", persistence.getSenderId());
        assertEquals("user-002", persistence.getReceiverId());
        assertEquals("TEXT", persistence.getMessageType());
        assertEquals("{\"content\":\"Hello\"}", persistence.getPayload());
        assertEquals(ImMessagePersistence.ImMessageStatus.PERSISTED, persistence.getStatus());
        assertEquals(ImMessagePersistence.AckStatus.PENDING, persistence.getAckStatus());
        assertEquals(ImMessagePersistence.DeliveryStatus.PENDING, persistence.getDeliveryStatus());
        assertEquals("NORMAL", persistence.getPriority());
        assertEquals(now, persistence.getCreatedAt());
        assertEquals(0, persistence.getRetryCount());
        assertEquals(5, persistence.getMaxRetryCount());
        assertFalse(persistence.getInDlq());
        assertFalse(persistence.getManualIntervention());
    }

    @Test
    void testCanRetry_withinLimit() {
        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .retryCount(3)
                .maxRetryCount(5)
                .manualIntervention(false)
                .build();

        assertTrue(persistence.canRetry());
    }

    @Test
    void testCanRetry_exceededLimit() {
        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .retryCount(5)
                .maxRetryCount(5)
                .manualIntervention(false)
                .build();

        assertFalse(persistence.canRetry());
    }

    @Test
    void testCanRetry_manualIntervention() {
        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .retryCount(1)
                .maxRetryCount(5)
                .manualIntervention(true)
                .build();

        assertFalse(persistence.canRetry());
    }

    @Test
    void testIsTerminalState_acknowledged() {
        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .status(ImMessagePersistence.ImMessageStatus.ACKNOWLEDGED)
                .build();

        assertTrue(persistence.isTerminalState());
    }

    @Test
    void testIsTerminalState_expired() {
        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .status(ImMessagePersistence.ImMessageStatus.EXPIRED)
                .build();

        assertTrue(persistence.isTerminalState());
    }

    @Test
    void testIsTerminalState_recalled() {
        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .status(ImMessagePersistence.ImMessageStatus.RECALLED)
                .build();

        assertTrue(persistence.isTerminalState());
    }

    @Test
    void testIsTerminalState_lost() {
        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .status(ImMessagePersistence.ImMessageStatus.LOST)
                .build();

        assertTrue(persistence.isTerminalState());
    }

    @Test
    void testIsTerminalState_notTerminal() {
        ImMessagePersistence persistence = ImMessagePersistence.builder()
                .status(ImMessagePersistence.ImMessageStatus.PROCESSING)
                .build();

        assertFalse(persistence.isTerminalState());
    }

    @Test
    void testImMessageStatus_values() {
        ImMessagePersistence.ImMessageStatus[] statuses = ImMessagePersistence.ImMessageStatus.values();
        assertTrue(statuses.length > 0);

        // Test key statuses
        assertNotNull(ImMessagePersistence.ImMessageStatus.valueOf("RECEIVED_FROM_CLIENT"));
        assertNotNull(ImMessagePersistence.ImMessageStatus.valueOf("PERSISTED"));
        assertNotNull(ImMessagePersistence.ImMessageStatus.valueOf("SENT_TO_SQS"));
        assertNotNull(ImMessagePersistence.ImMessageStatus.valueOf("PROCESSING"));
        assertNotNull(ImMessagePersistence.ImMessageStatus.valueOf("PROCESSED"));
        assertNotNull(ImMessagePersistence.ImMessageStatus.valueOf("ACKNOWLEDGED"));
        assertNotNull(ImMessagePersistence.ImMessageStatus.valueOf("PROCESSING_FAILED"));
        assertNotNull(ImMessagePersistence.ImMessageStatus.valueOf("IN_DLQ"));
        assertNotNull(ImMessagePersistence.ImMessageStatus.valueOf("LOST"));
    }

    @Test
    void testAckStatus_values() {
        assertNotNull(ImMessagePersistence.AckStatus.valueOf("PENDING"));
        assertNotNull(ImMessagePersistence.AckStatus.valueOf("SENT_ACK"));
        assertNotNull(ImMessagePersistence.AckStatus.valueOf("DELIVERY_ACK"));
        assertNotNull(ImMessagePersistence.AckStatus.valueOf("READ_ACK"));
        assertNotNull(ImMessagePersistence.AckStatus.valueOf("FAILED"));
    }

    @Test
    void testDeliveryStatus_values() {
        assertNotNull(ImMessagePersistence.DeliveryStatus.valueOf("PENDING"));
        assertNotNull(ImMessagePersistence.DeliveryStatus.valueOf("PUSHED"));
        assertNotNull(ImMessagePersistence.DeliveryStatus.valueOf("DELIVERED"));
        assertNotNull(ImMessagePersistence.DeliveryStatus.valueOf("STORED_OFFLINE"));
        assertNotNull(ImMessagePersistence.DeliveryStatus.valueOf("FAILED"));
    }

    @Test
    void testNoArgsConstructor() {
        ImMessagePersistence persistence = new ImMessagePersistence();
        assertNotNull(persistence);
    }

    @Test
    void testSettersAndGetters() {
        ImMessagePersistence persistence = new ImMessagePersistence();

        persistence.setMessageId("msg-123");
        persistence.setStatus(ImMessagePersistence.ImMessageStatus.PROCESSING);
        persistence.setRetryCount(2);
        persistence.setErrorMessage("Test error");

        assertEquals("msg-123", persistence.getMessageId());
        assertEquals(ImMessagePersistence.ImMessageStatus.PROCESSING, persistence.getStatus());
        assertEquals(2, persistence.getRetryCount());
        assertEquals("Test error", persistence.getErrorMessage());
    }
}
