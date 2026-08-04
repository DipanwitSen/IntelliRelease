package com.gyansys.intellirelease.repository;

import com.gyansys.intellirelease.model.AuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByTenantIdAndTimestampAfterOrderByTimestampDesc(
            String tenantId, OffsetDateTime since, Pageable pageable);

    List<AuditEvent> findByTenantIdOrderByTimestampDesc(String tenantId, Pageable pageable);

    List<AuditEvent> findByTenantIdAndEntityTypeAndEntityIdOrderByTimestampDesc(
            String tenantId, String entityType, String entityId);
}
