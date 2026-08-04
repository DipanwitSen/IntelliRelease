package com.gyansys.intellirelease.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable record of a consequential action: who, what, when, on which entity.
 *
 * <p>Append-only by convention — nothing in the codebase updates or deletes a
 * row here. This is the evidence trail a validation or compliance review reads.
 */
@Entity
@Table(name = "audit_event")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @Column(name = "audit_id", nullable = false, updatable = false)
    private UUID auditId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    /** Named human, or a system principal such as {@code system:webhook}. */
    @Column(name = "actor", nullable = false, length = 255)
    private String actor;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id", length = 255)
    private String entityId;

    @Column(name = "detail")
    private String detail;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "timestamp", nullable = false)
    private OffsetDateTime timestamp;

    public static AuditEvent of(String tenantId, String actor, String action,
                                String entityType, String entityId, String detail,
                                String correlationId) {
        AuditEvent event = new AuditEvent();
        event.auditId = UUID.randomUUID();
        event.tenantId = tenantId;
        event.actor = actor;
        event.action = action;
        event.entityType = entityType;
        event.entityId = entityId;
        event.detail = detail;
        event.correlationId = correlationId;
        event.timestamp = OffsetDateTime.now();
        return event;
    }
}
