package com.gyansys.intellirelease.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The raw, unmodified inbound event exactly as the provider delivered it.
 *
 * <p>Persist-then-acknowledge: the row is written before any analysis runs, so
 * a crash mid-pipeline never loses a production-bound change. {@code deliveryId}
 * is unique per provider, which is what makes redelivery idempotent.
 *
 * <p>Provenance: FACT. This is directly observed input.
 */
@Entity
@Table(name = "webhook_event")
@Getter
@Setter
@NoArgsConstructor
public class WebhookEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    /** GitHub's X-GitHub-Delivery header. The idempotency key. */
    @Column(name = "delivery_id", nullable = false, length = 255)
    private String deliveryId;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false)
    private String rawPayload;

    /**
     * Result of HMAC-SHA256 verification against the configured webhook secret.
     * False events are still stored — they are evidence — but the UI labels them
     * unverified and the pipeline can be configured to refuse them.
     */
    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Column(name = "processed", nullable = false)
    private boolean processed;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    public static WebhookEvent create(String tenantId, String provider, String deliveryId,
                                      String eventType, String rawPayload, boolean signatureValid) {
        WebhookEvent event = new WebhookEvent();
        event.eventId = UUID.randomUUID();
        event.tenantId = tenantId;
        event.provider = provider;
        event.deliveryId = deliveryId;
        event.eventType = eventType;
        event.rawPayload = rawPayload;
        event.signatureValid = signatureValid;
        event.processed = false;
        event.receivedAt = OffsetDateTime.now();
        return event;
    }
}
