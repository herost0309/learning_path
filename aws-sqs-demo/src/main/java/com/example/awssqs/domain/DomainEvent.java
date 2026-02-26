package com.example.awssqs.domain;

import java.time.Instant;

/**
 * 事件基类
 */
public abstract class DomainEvent {

    private String eventId;
    private String eventType;
    private Instant occurredAt;
    protected String aggregateId;
    private Long tenantId;
    private String correlationId;

    protected DomainEvent() {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.occurredAt = java.time.Instant.now();
        this.eventType = this.getClass().getSimpleName();
    }

    // Getters and Setters
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}
