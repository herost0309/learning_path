package com.example.awssqs.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 失败消息实体
 * 追踪进入DLQ的消息，用于分析和重试
 */
@Entity
@Table(name = "failed_message", indexes = {
        @Index(name = "idx_message_id", columnList = "messageId"),
        @Index(name = "idx_queue_status", columnList = "queueName,status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 原始消息ID
     */
    @Column(name = "message_id", nullable = false, unique = true)
    private String messageId;

    /**
     * 原队列名称
     */
    @Column(name = "queue_name", nullable = false, length = 200)
    private String queueName;

    /**
     * 消息类型
     */
    @Column(name = "payload_type", nullable = false, length = 100)
    private String payloadType;

    /**
     * 消息内容
     */
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    /**
     * DLQ收据句柄
     */
    @Column(name = "dlq_receipt_handle")
    private String dlqReceiptHandle;

    /**
     * 失败时间
     */
    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    /**
     * 接收次数（从主队列移入DLQ的次数）
     */
    @Column(name = "receive_count", nullable = false)
    private Integer receiveCount;

    /**
     * 失败状态
     * PENDING - 待处理
     * RETRYING - 正在重试
     * MANUAL - 需要人工处理
     * RESOLVED - 已解决
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FailedMessageStatus status;

    /**
     * 错误消息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 失败原因
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 50)
    private FailureReason failureReason;

    /**
     * 重试次数
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 最后重试时间
     */
    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    /**
     * 转人工时间
     */
    @Column(name = "moved_to_manual_at")
    private LocalDateTime movedToManualAt;

    /**
     * 是否已发送到人工队列
     */
    @Column(name = "sent_to_manual")
    @Builder.Default
    private Boolean sentToManual = false;

    /**
     * 租户ID
     */
    @Column(name = "tenant_id")
    private Long tenantId;

    /**
     * 聚合ID
     */
    @Column(name = "aggregate_id")
    private String aggregateId;

    /**
     * 聚合类型
     */
    @Column(name = "aggregate_type", length = 50)
    private String aggregateType;

    /**
     * 人工处理员ID
     */
    @Column(name = "handled_by")
    private String handledBy;

    /**
     * 处理备注
     */
    @Column(name = "handling_notes", columnDefinition = "TEXT")
    private String handlingNotes;

    /**
     * 处理时间
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * 扩展属性（JSON格式）
     */
    @Column(name = "attributes", columnDefinition = "JSON")
    private String attributes;

    public enum FailedMessageStatus {
        PENDING,
        RETRYING,
        MANUAL,
        RESOLVED,
        IGNORED
    }

    public enum FailureReason {
        /**
         * 消息处理超时
         */
        PROCESSING_TIMEOUT,

        /**
         * 消息处理异常
         */
        PROCESSING_EXCEPTION,

        /**
         * 业务逻辑失败
         */
        BUSINESS_LOGIC_FAILURE,

        /**
         * 数据库连接失败
         */
        DATABASE_ERROR,

        /**
         * 依赖服务不可用
         */
        DEPENDENCY_SERVICE_DOWN,

        /**
         * 其他错误
         */
        OTHER
    }
}
