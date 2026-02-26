package com.example.awssqs.domain;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 消息处理失败事件
 */
@Data
@NoArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class MessageFailedEvent extends DomainEvent {

    private String messageId;
    private String queueName;
    private String errorMessage;
    private String errorType;

    public MessageFailedEvent(String messageId, String queueName, String errorMessage, String errorType) {
        super();
        this.messageId = messageId;
        this.setAggregateId(messageId);
        this.queueName = queueName;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
    }
}
