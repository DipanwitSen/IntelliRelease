-- IntelliRelease v2 :: engines added beyond release notes (PostgreSQL 17)
-- Impact · Regression · Drift · Cleanup · Readiness at RELEASE level,
-- plus the scheduled Cleanup Intelligence output which is not per-PR.

ALTER TABLE release ADD COLUMN impact_analysis           JSONB;
ALTER TABLE release ADD COLUMN regression_recommendation JSONB;
ALTER TABLE release ADD COLUMN configuration_drift       JSONB;
ALTER TABLE release ADD COLUMN readiness_factors         JSONB;
ALTER TABLE release ADD COLUMN attention_flags           JSONB;
ALTER TABLE release ADD COLUMN ai_synthesis              JSONB;
ALTER TABLE release ADD COLUMN ai_fallback_used          BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE release ADD COLUMN qa_signed_off             BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE release ADD COLUMN regression_executed_count INTEGER;
ALTER TABLE release ADD COLUMN built_at                  TIMESTAMP WITH TIME ZONE;
ALTER TABLE release ADD COLUMN analyzed_at               TIMESTAMP WITH TIME ZONE;

-- Cleanup Intelligence runs on a SCHEDULE, not per PR. Its output feeds
-- Deployment Readiness on the next release cycle.
CREATE TABLE cleanup_recommendation (
    recommendation_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(100) NOT NULL DEFAULT 'eli-lilly',
    cronjob_name        VARCHAR(255) NOT NULL,
    current_retention_days INTEGER,
    recommended_retention_days INTEGER,
    table_size_gb       NUMERIC(10,2),
    estimated_reduction_pct INTEGER,
    confidence          VARCHAR(20)  NOT NULL,
    healthy             BOOLEAN      NOT NULL DEFAULT TRUE,
    rationale           TEXT,
    evidence            JSONB,
    provenance_class    VARCHAR(30)  NOT NULL DEFAULT 'RULE_OUTPUT',
    analyzed_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cleanup_tenant_time ON cleanup_recommendation (tenant_id, analyzed_at);
