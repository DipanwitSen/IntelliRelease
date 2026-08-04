package com.gyansys.intellirelease.infra;

import com.gyansys.intellirelease.config.IntelliReleaseProperties;
import org.springframework.stereotype.Component;

/**
 * Resolves the tenant for the current request.
 *
 * <p>Single-tenant in the POC, but every table carries {@code tenant_id} and
 * every query goes through here — so onboarding customer #2 is configuration
 * plus PostgreSQL row-level security, not a schema migration and an audit of
 * every query in the codebase.
 *
 * <p>Tenant isolation is architectural, not conventional. This class is where
 * that intent is enforced in code rather than asserted in a document.
 */
@Component
public class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private final String defaultTenantId;

    public TenantContext(IntelliReleaseProperties properties) {
        this.defaultTenantId = properties.tenant().defaultId();
    }

    /** The active tenant, falling back to the configured default. */
    public String currentTenantId() {
        String current = CURRENT.get();
        return current == null ? defaultTenantId : current;
    }

    public String defaultTenantId() {
        return defaultTenantId;
    }

    public void set(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            CURRENT.set(tenantId);
        }
    }

    /** Always call this at the end of a request; a leaked thread-local is a data leak. */
    public void clear() {
        CURRENT.remove();
    }
}
