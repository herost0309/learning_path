package com.example.awssqs.domain;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工单创建事件
 */
@Data
@NoArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class TicketCreatedEvent extends DomainEvent {

    private Long ticketId;
    private Long requesterId;
    private Long organizationId;
    private String subject;
    private String description;
    private String type;
    private String priority;
    private String via;
    private Map<String, Object> customFields;

    public TicketCreatedEvent(Long ticketId, Long requesterId, Long organizationId,
                             String subject, String description, String type,
                             String priority, String via, Map<String, Object> customFields) {
        super();
        this.ticketId = ticketId;
        this.setAggregateId(String.valueOf(ticketId));
        this.requesterId = requesterId;
        this.organizationId = organizationId;
        this.subject = subject;
        this.description = description;
        this.type = type;
        this.priority = priority;
        this.via = via;
        this.customFields = customFields;
    }
}
