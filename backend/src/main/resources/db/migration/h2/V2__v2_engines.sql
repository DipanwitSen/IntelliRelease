-- IntelliRelease v2 :: engines added beyond release notes (H2, local dev only)
-- Mirror of db/migration/postgresql/V2__v2_engines.sql.

ALTER TABLE release ADD COLUMN impact_analysis           JSON;
ALTER TABLE release ADD COLUMN regression_recommendation JSON;
ALTER TABLE release ADD COLUMN configuration_drift       JSON;
ALTER TABLE release ADD COLUMN readiness_factors         JSON;
ALTER TABLE release ADD COLUMN attention_flags           JSON;
ALTER TABLE release ADD COLUMN ai_synthesis              JSON;
ALTER TABLE release ADD COLUMN ai_fallback_used          BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE release ADD COLUMN qa_signed_off             BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE release ADD COLUMN regression_executed_count INTEGER;
ALTER TABLE release ADD COLUMN built_at                  TIMESTAMP WITH TIME ZONE;
ALTER TABLE release ADD COLUMN analyzed_at               TIMESTAMP WITH TIME ZONE;

CREATE TABLE cleanup_recommendation (
    recommendation_id          UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    tenant_id                  VARCHAR(100) NOT NULL DEFAULT 'eli-lilly',
    cronjob_name               VARCHAR(255) NOT NULL,
    current_retention_days     INTEGER,
    recommended_retention_days INTEGER,
    table_size_gb              NUMERIC(10,2),
    estimated_reduction_pct    INTEGER,
    confidence                 VARCHAR(20)  NOT NULL,
    healthy                    BOOLEAN      NOT NULL DEFAULT TRUE,
    rationale                  CLOB,
    evidence                   JSON,
    provenance_class           VARCHAR(30)  NOT NULL DEFAULT 'RULE_OUTPUT',
    analyzed_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cleanup_tenant_time ON cleanup_recommendation (tenant_id, analyzed_at);
