package com.gyansys.intellirelease.infra;

import com.gyansys.intellirelease.model.AuditEvent;
import com.gyansys.intellirelease.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit trail. Every consequential action goes through here.
 *
 * <p>{@link Propagation#REQUIRES_NEW}: the audit record commits on its own
 * transaction, so an action that later rolls back still leaves evidence that it
 * was attempted. An audit trail that disappears alongside the failure it was
 * recording is not an audit trail.
 */
@Component
public class AuditWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditWriter.class);

    public static final String SYSTEM_WEBHOOK = "system:webhook";
    public static final String SYSTEM_WORKER = "system:worker";
    public static final String SYSTEM_SCHEDULER = "system:scheduler";

    private final AuditEventRepository repository;
    private final TenantContext tenantContext;

    public AuditWriter(AuditEventRepository repository, TenantContext tenantContext) {
        this.repository = repository;
        this.tenantContext = tenantContext;
    }

    /** Records an action performed by the authenticated caller. */
    public void record(String action, String entityType, String entityId, String detail) {
        write(currentActor(), action, entityType, entityId, detail);
    }

    /** Records an action performed by a named system principal. */
    public void recordAs(String actor, String action, String entityType, String entityId, String detail) {
        write(actor, action, entityType, entityId, detail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void write(String actor, String action, String entityType, String entityId, String detail) {
        try {
            AuditEvent event = AuditEvent.of(
                    tenantContext.currentTenantId(),
                    actor,
                    action,
                    entityType,
                    entityId,
                    detail,
                    MDC.get("correlationId")
            );
            repository.save(event);
        } catch (RuntimeException exception) {
            // Never let an audit failure break the action being audited — but
            // never let it pass unnoticed either.
            log.error("Failed to write audit event action={} entityType={} entityId={}",
                    action, entityType, entityId, exception);
        }
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        return authentication.getName();
    }
}
