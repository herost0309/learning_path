package com.example.awssqs.domain;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 消息处理成功事件
 */
@Data
@NoArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class MessageProcessedEvent extends DomainEvent {

    private String messageId;
    private String queueName;
    private String payloadType;
    private Long processingTimeMs;

    public MessageProcessedEvent(String messageId, String queueName, String payloadType, Long processingTimeMs) {
        super();
        this.messageId = messageId;
        this.setAggregateId(messageId);
        this.queueName = queueName;
        this.payloadType = payloadType;
        this.processingTimeMs = processingTimeMs;
    }
}
