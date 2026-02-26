package com.example.awssqs.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 消息持久化实体
 * 发送到SQS前先保存到数据库，防止消息丢失
 */
@Entity
@Table(name = "message_persistence", indexes = {
        @Index(name = "idx_status_created_at", columnList = "status,created_at"),
        @Index(name = "idx_message_id_queue", columnList = "messageId,queueName")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagePersistence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true)
    private String messageId;

    @Column(name = "queue_name", nullable = false, length = 200)
    private String queueName;

    /**
     * 消息类型（类名）
     */
    @Column(name = "payload_type", nullable = false, length = 100)
    private String payloadType;

    /**
     * 消息内容（JSON字符串）
     */
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    /**
     * SQS收据句柄（用于删除消息）
     */
    @Column(name = "receipt_handle")
    private String receiptHandle;

    /**
     * 消息状态
     * PENDING - 待发送
     * SENT - 已发送到SQS
     * RECEIVED - 消费者已接收
     * PROCESSING - 消费者处理中
     * PROCESSED - 已处理完成
     * FAILED - 发送或处理失败
     * REPLAYED - 已重放
     * LOST - 确认丢失
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MessageStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "replayed_at")
    private LocalDateTime replayedAt;

    /**
     * 重试次数
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 错误信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 租户ID
     */
    @Column(name = "tenant_id")
    private Long tenantId;

    /**
     * 业务聚合ID（如ticketId）
     */
    @Column(name = "aggregate_id")
    private String aggregateId;

    /**
     * 业务类型
     */
    @Column(name = "aggregate_type", length = 50)
    private String aggregateType;

    /**
     * 是否被发送到DLQ
     */
    @Column(name = "in_dlq", nullable = false)
    @Builder.Default
    private Boolean inDlq = false;

    /**
     * DLQ接收次数
     */
    @Column(name = "dlq_receive_count")
    @Builder.Default
    private Integer dlqReceiveCount = 0;

    /**
     * 是否手动处理
     */
    @Column(name = "manual_intervention")
    @Builder.Default
    private Boolean manualIntervention = false;

    /**
     * 额外元数据
     */
    @Column(name = "metadata", columnDefinition = "JSON")
    private String metadata;

    /**
     * 消息大小（字节）
     */
    @Column(name = "message_size")
    private Long messageSize;

    public enum MessageStatus {
        /**
         * 待发送 - 初始状态
         */
        PENDING,

        /**
         * 已发送 - 成功发送到SQS
         */
        SENT,

        /**
         * 已接收 - 消费者已从SQS接收
         */
        RECEIVED,

        /**
         * 处理中 - 消费者正在处理
         */
        PROCESSING,

        /**
         * 已处理 - 消费者处理完成
         */
        PROCESSED,

        /**
         * 失败 - 发送或处理失败
         */
        FAILED,

        /**
         * 已重放 - 定时任务重放
         */
        REPLAYED,

        /**
         * 丢失 - 确认丢失（超时且未在DLQ）
         */
        LOST,

        /**
         * 已确认 - 已被SQS删除
         */
        ACKNOWLEDGED
    }
}
