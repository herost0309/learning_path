package com.example.awssqs.im;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * IM消息持久化实体
 * 增强的消息状态追踪，支持实时IM系统的防丢机制
 */
@Entity
@Table(name = "im_message_persistence", indexes = {
        @Index(name = "idx_im_message_id", columnList = "messageId", unique = true),
        @Index(name = "idx_im_client_msg_id", columnList = "clientMessageId"),
        @Index(name = "idx_im_conversation", columnList = "conversationId,createdAt"),
        @Index(name = "idx_im_status_created", columnList = "status,createdAt"),
        @Index(name = "idx_im_sender", columnList = "senderId,createdAt"),
        @Index(name = "idx_im_receiver", columnList = "receiverId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImMessagePersistence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 服务端消息ID
     */
    @Column(name = "message_id", nullable = false, unique = true, length = 100)
    private String messageId;

    /**
     * 客户端消息ID（用于幂等性）
     */
    @Column(name = "client_message_id", length = 100)
    private String clientMessageId;

    /**
     * 会话ID
     */
    @Column(name = "conversation_id", nullable = false, length = 100)
    private String conversationId;

    /**
     * 发送者ID
     */
    @Column(name = "sender_id", nullable = false, length = 100)
    private String senderId;

    /**
     * 接收者ID
     */
    @Column(name = "receiver_id", length = 100)
    private String receiverId;

    /**
     * 消息类型
     */
    @Column(name = "message_type", nullable = false, length = 20)
    private String messageType;

    /**
     * 消息内容
     */
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    /**
     * 消息状态（细粒度状态追踪）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ImMessageStatus status;

    /**
     * 发送确认状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ack_status", length = 20)
    private AckStatus ackStatus;

    /**
     * 投递状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", length = 20)
    private DeliveryStatus deliveryStatus;

    /**
     * 消息优先级
     */
    @Column(name = "priority", length = 10)
    private String priority;

    /**
     * 创建时间（客户端发送时间）
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 持久化时间
     */
    @Column(name = "persisted_at")
    private LocalDateTime persistedAt;

    /**
     * 发送到SQS的时间
     */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /**
     * SQS消息ID
     */
    @Column(name = "sqs_message_id")
    private String sqsMessageId;

    /**
     * SQS序列号（FIFO队列）
     */
    @Column(name = "sqs_sequence_number")
    private String sqsSequenceNumber;

    /**
     * 消费者接收时间
     */
    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    /**
     * 处理开始时间
     */
    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    /**
     * 处理完成时间
     */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /**
     * 送达接收者时间
     */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /**
     * 已读时间
     */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /**
     * 重试次数
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 最大重试次数
     */
    @Column(name = "max_retry_count")
    @Builder.Default
    private Integer maxRetryCount = 5;

    /**
     * 错误信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 错误码
     */
    @Column(name = "error_code", length = 50)
    private String errorCode;

    /**
     * 是否在DLQ中
     */
    @Column(name = "in_dlq", nullable = false)
    @Builder.Default
    private Boolean inDlq = false;

    /**
     * DLQ进入时间
     */
    @Column(name = "dlq_at")
    private LocalDateTime dlqAt;

    /**
     * 是否需要人工干预
     */
    @Column(name = "manual_intervention")
    @Builder.Default
    private Boolean manualIntervention = false;

    /**
     * 租户ID
     */
    @Column(name = "tenant_id")
    private Long tenantId;

    /**
     * 设备ID
     */
    @Column(name = "device_id", length = 100)
    private String deviceId;

    /**
     * 消息序号
     */
    @Column(name = "sequence_number")
    private Long sequenceNumber;

    /**
     * 扩展元数据
     */
    @Column(name = "metadata", columnDefinition = "JSON")
    private String metadata;

    /**
     * 最后更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * IM消息状态（细粒度）
     */
    public enum ImMessageStatus {
        // === 发送端状态 ===
        RECEIVED_FROM_CLIENT,    // 从客户端接收
        PERSISTED,               // 已持久化
        SENDING_TO_SQS,          // 正在发送到SQS
        SENT_TO_SQS,             // 已发送到SQS
        SQS_SEND_FAILED,         // SQS发送失败

        // === 消费端状态 ===
        RECEIVED_FROM_SQS,       // 从SQS接收
        PROCESSING,              // 处理中
        PROCESSED,               // 处理完成
        PROCESSING_FAILED,       // 处理失败

        // === 投递状态 ===
        DELIVERING,              // 投递中
        DELIVERED,               // 已送达
        DELIVERY_FAILED,         // 投递失败

        // === 终态 ===
        ACKNOWLEDGED,            // 已确认（完整流程结束）
        EXPIRED,                 // 已过期
        RECALLED,                // 已撤回
        LOST,                    // 丢失（需要人工处理）
        IN_DLQ                   // 在死信队列中
    }

    /**
     * 发送确认状态
     */
    public enum AckStatus {
        PENDING,          // 等待确认
        SENT_ACK,         // 已发送确认给发送方
        DELIVERY_ACK,     // 已发送送达回执
        READ_ACK,         // 已发送已读回执
        FAILED            // 确认失败
    }

    /**
     * 投递状态
     */
    public enum DeliveryStatus {
        PENDING,          // 待投递
        PUSHED,           // 已推送
        DELIVERED,        // 已送达（用户在线）
        STORED_OFFLINE,   // 离线存储
        FAILED            // 投递失败
    }

    /**
     * 检查是否可以重试
     */
    public boolean canRetry() {
        return retryCount < maxRetryCount && !manualIntervention;
    }

    /**
     * 检查是否是终态
     */
    public boolean isTerminalState() {
        return status == ImMessageStatus.ACKNOWLEDGED ||
               status == ImMessageStatus.EXPIRED ||
               status == ImMessageStatus.RECALLED ||
               status == ImMessageStatus.LOST;
    }
}
