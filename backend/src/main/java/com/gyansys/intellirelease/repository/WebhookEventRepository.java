package com.gyansys.intellirelease.repository;

import com.gyansys.intellirelease.model.WebhookEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    /**
     * Idempotency check. GitHub retries deliveries; the same delivery ID must
     * never produce a second analysis.
     */
    Optional<WebhookEvent> findByProviderAndDeliveryId(String provider, String deliveryId);

    boolean existsByProviderAndDeliveryId(String provider, String deliveryId);

    /** Raw event log — duplicates deliberately visible for the demo. */
    List<WebhookEvent> findByTenantIdOrderByReceivedAtDesc(String tenantId, Pageable pageable);
}
