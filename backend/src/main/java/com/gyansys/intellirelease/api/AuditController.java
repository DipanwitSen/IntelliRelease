package com.gyansys.intellirelease.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.infra.TenantContext;
import com.gyansys.intellirelease.model.AuditEvent;
import com.gyansys.intellirelease.repository.AuditEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Read access to the append-only audit trail every consequential action
 * writes through {@code AuditWriter}. Every row here was written once, by the
 * action it records — this endpoint never derives or infers a row.
 */
@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit", description = "The append-only evidence trail")
public class AuditController {

    private final AuditEventRepository auditEventRepository;
    private final TenantContext tenantContext;

    public AuditController(AuditEventRepository auditEventRepository, TenantContext tenantContext) {
        this.auditEventRepository = auditEventRepository;
        this.tenantContext = tenantContext;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Entry(
            UUID id, OffsetDateTime occurredAt, String actor, String action, String entityType,
            String entityId, String correlationId, String outcome, String detail, String ipAddress
    ) {
    }

    @GetMapping
    @Operation(summary = "Audit trail entries, most recent first")
    public PageResponse<Entry> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) String actor,
                                    @RequestParam(required = false) String action,
                                    @RequestParam(required = false) String entityType,
                                    @RequestParam(required = false) String outcome,
                                    @RequestParam(required = false) OffsetDateTime since) {
        String tenantId = tenantContext.currentTenantId();
        List<AuditEvent> all = auditEventRepository.findByTenantId(tenantId,
                Sort.by(Sort.Direction.DESC, "timestamp"));

        List<Entry> filtered = all.stream()
                .filter(event -> actor == null || actor.isBlank() || actor.equalsIgnoreCase(event.getActor()))
                .filter(event -> action == null || action.isBlank() || action.equalsIgnoreCase(event.getAction()))
                .filter(event -> entityType == null || entityType.isBlank()
                        || entityType.equalsIgnoreCase(event.getEntityType()))
                // Every audit write records a completed action — see AuditWriter, which
                // logs and swallows its own failures rather than persisting them.
                .filter(event -> outcome == null || outcome.isBlank() || "SUCCESS".equalsIgnoreCase(outcome))
                .filter(event -> since == null || (event.getTimestamp() != null && event.getTimestamp().isAfter(since)))
                .filter(event -> q == null || q.isBlank() || matches(event, q))
                .map(this::toEntry)
                .toList();

        int safeSize = size <= 0 ? 50 : Math.min(size, 200);
        int fromIndex = Math.min(Math.max(page, 0) * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());

        return PageResponse.of(filtered.subList(fromIndex, toIndex), filtered.size(), page, safeSize);
    }

    private boolean matches(AuditEvent event, String q) {
        String needle = q.toLowerCase();
        return contains(event.getAction(), needle) || contains(event.getDetail(), needle)
                || contains(event.getEntityType(), needle) || contains(event.getEntityId(), needle)
                || contains(event.getActor(), needle);
    }

    private boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle);
    }

    private Entry toEntry(AuditEvent event) {
        return new Entry(event.getAuditId(), event.getTimestamp(), event.getActor(), event.getAction(),
                event.getEntityType(), event.getEntityId(), event.getCorrelationId(), "SUCCESS",
                event.getDetail(), null);
    }
}
