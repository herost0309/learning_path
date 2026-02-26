package com.example.awssqs.im;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ImMessage 单元测试
 */
class ImMessageTest {

    @Test
    void testBuilder() {
        ImMessage message = ImMessage.builder()
                .messageId("msg-123")
                .clientMessageId("client-msg-123")
                .conversationId("conv-456")
                .senderId("user-001")
                .receiverId("user-002")
                .messageType(ImMessage.MessageType.TEXT)
                .content("Hello World")
                .priority(ImMessage.MessagePriority.NORMAL)
                .requireReadReceipt(true)
                .timestamp(System.currentTimeMillis())
                .tenantId(1L)
                .deviceId("device-001")
                .sequenceNumber(1L)
                .build();

        assertEquals("msg-123", message.getMessageId());
        assertEquals("client-msg-123", message.getClientMessageId());
        assertEquals("conv-456", message.getConversationId());
        assertEquals("user-001", message.getSenderId());
        assertEquals("user-002", message.getReceiverId());
        assertEquals(ImMessage.MessageType.TEXT, message.getMessageType());
        assertEquals("Hello World", message.getContent());
        assertEquals(ImMessage.MessagePriority.NORMAL, message.getPriority());
        assertTrue(message.getRequireReadReceipt());
        assertNotNull(message.getTimestamp());
        assertEquals(1L, message.getTenantId());
        assertEquals("device-001", message.getDeviceId());
        assertEquals(1L, message.getSequenceNumber());
    }

    @Test
    void testGetDeduplicationKey_withClientMessageId() {
        ImMessage message = ImMessage.builder()
                .messageId("msg-123")
                .clientMessageId("client-msg-456")
                .build();

        assertEquals("client-msg-456", message.getDeduplicationKey());
    }

    @Test
    void testGetDeduplicationKey_withoutClientMessageId() {
        ImMessage message = ImMessage.builder()
                .messageId("msg-123")
                .build();

        assertEquals("msg-123", message.getDeduplicationKey());
    }

    @Test
    void testGetMessageGroupId() {
        ImMessage message = ImMessage.builder()
                .conversationId("conv-456")
                .build();

        assertEquals("conv-conv-456", message.getMessageGroupId());
    }

    @Test
    void testIsExpired_notExpired() {
        ImMessage message = ImMessage.builder()
                .timestamp(Instant.now().toEpochMilli())
                .expireInSeconds(300) // 5 minutes
                .build();

        assertFalse(message.isExpired());
    }

    @Test
    void testIsExpired_expired() {
        ImMessage message = ImMessage.builder()
                .timestamp(Instant.now().minusSeconds(600).toEpochMilli()) // 10 minutes ago
                .expireInSeconds(300) // 5 minutes
                .build();

        assertTrue(message.isExpired());
    }

    @Test
    void testIsExpired_noExpiration() {
        ImMessage message = ImMessage.builder()
                .timestamp(Instant.now().minusSeconds(60000).toEpochMilli())
                .expireInSeconds(0) // Never expire
                .build();

        assertFalse(message.isExpired());
    }

    @Test
    void testIsExpired_nullExpiration() {
        ImMessage message = ImMessage.builder()
                .timestamp(Instant.now().toEpochMilli())
                .expireInSeconds(null)
                .build();

        assertFalse(message.isExpired());
    }

    @Test
    void testMessageTypes() {
        assertEquals(10, ImMessage.MessageType.values().length);
        assertNotNull(ImMessage.MessageType.valueOf("TEXT"));
        assertNotNull(ImMessage.MessageType.valueOf("IMAGE"));
        assertNotNull(ImMessage.MessageType.valueOf("VIDEO"));
        assertNotNull(ImMessage.MessageType.valueOf("AUDIO"));
        assertNotNull(ImMessage.MessageType.valueOf("FILE"));
        assertNotNull(ImMessage.MessageType.valueOf("SYSTEM"));
        assertNotNull(ImMessage.MessageType.valueOf("TYPING"));
        assertNotNull(ImMessage.MessageType.valueOf("RECALL"));
        assertNotNull(ImMessage.MessageType.valueOf("REPLY"));
        assertNotNull(ImMessage.MessageType.valueOf("FORWARD"));
    }

    @Test
    void testMessagePriorities() {
        assertEquals(3, ImMessage.MessagePriority.values().length);
        assertNotNull(ImMessage.MessagePriority.valueOf("HIGH"));
        assertNotNull(ImMessage.MessagePriority.valueOf("NORMAL"));
        assertNotNull(ImMessage.MessagePriority.valueOf("LOW"));
    }

    @Test
    void testNoArgsConstructor() {
        ImMessage message = new ImMessage();
        assertNotNull(message);
    }

    @Test
    void testAllArgsConstructor() {
        ImMessage message = new ImMessage(
                "msg-123", "client-msg-123", "conv-456",
                "user-001", "user-002", ImMessage.MessageType.TEXT,
                "Hello", "metadata", ImMessage.MessagePriority.NORMAL,
                true, System.currentTimeMillis(), 0, 1L, "device-001", 1L
        );

        assertEquals("msg-123", message.getMessageId());
        assertEquals("Hello", message.getContent());
    }
}
