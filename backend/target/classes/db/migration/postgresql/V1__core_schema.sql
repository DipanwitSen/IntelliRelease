-- IntelliRelease v2 :: core schema (PostgreSQL 17)
--
-- The Release Knowledge Repository. Every table carries tenant_id because
-- tenant isolation is architectural, not conventional. Every intelligence
-- payload is JSONB so the provenance-tagged structure survives verbatim.

CREATE TABLE webhook_event (
    event_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(100) NOT NULL DEFAULT 'eli-lilly',
    provider        VARCHAR(50)  NOT NULL,
    delivery_id     VARCHAR(255) NOT NULL,
    event_type      VARCHAR(100),
    raw_payload     JSONB        NOT NULL,
    signature_valid BOOLEAN      NOT NULL,
    processed       BOOLEAN      NOT NULL DEFAULT FALSE,
    received_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (provider, delivery_id)
);

CREATE TABLE pull_request (
    pr_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     VARCHAR(100) NOT NULL DEFAULT 'eli-lilly',
    repo_name     VARCHAR(255) NOT NULL,
    pr_number     INTEGER      NOT NULL,
    merge_sha     VARCHAR(100) NOT NULL,
    title         VARCHAR(500),
    description   TEXT,
    author        VARCHAR(255),
    branch        VARCHAR(255),
    ticket_key    VARCHAR(100),
    merged_at     TIMESTAMP WITH TIME ZONE,
    changed_files JSONB,
    raw_metadata  JSONB,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, repo_name, merge_sha)
);

CREATE TABLE pr_analysis (
    pr_id                       UUID PRIMARY KEY REFERENCES pull_request(pr_id),
    tenant_id                   VARCHAR(100) NOT NULL DEFAULT 'eli-lilly',
    sap_commerce_context        JSONB        NOT NULL,
    impact_analysis             JSONB        NOT NULL,
    regression_recommendation   JSONB,
    risk_score                  INTEGER      NOT NULL,
    risk_level                  VARCHAR(20)  NOT NULL,
    risk_reasons                JSONB        NOT NULL,
    risk_policy_version         VARCHAR(20),
    configuration_drift         JSONB,
    ai_summary                  JSONB,
    ai_fallback_used            BOOLEAN      NOT NULL DEFAULT FALSE,
    deployment_readiness_score  INTEGER,
    deployment_readiness_status VARCHAR(30),
    -- FACT | DERIVED_FACT | RULE_OUTPUT | AI_INFERENCE | UNKNOWN
    -- The record as a whole is a rule-engine output; individual nested
    -- fields carry their own finer-grained provenance class.
    provenance_class            VARCHAR(30)  NOT NULL DEFAULT 'RULE_OUTPUT',
    model_provider              VARCHAR(100),
    model_name                  VARCHAR(100),
    tokens_used                 INTEGER,
    generated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE release (
    release_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            VARCHAR(100) NOT NULL DEFAULT 'eli-lilly',
    repo_name            VARCHAR(255) NOT NULL,
    version              VARCHAR(100) NOT NULL,
    from_ref             VARCHAR(255) NOT NULL,
    to_ref               VARCHAR(255) NOT NULL,
    resolved_pr_count    INTEGER,
    excluded_prs         JSONB,
    aggregate_risk_score INTEGER,
    aggregate_risk_level VARCHAR(20),
    readiness_score      INTEGER,
    readiness_status     VARCHAR(30),
    status               VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, repo_name, version)
);

CREATE TABLE release_pr (
    release_id UUID NOT NULL REFERENCES release(release_id),
    pr_id      UUID NOT NULL REFERENCES pull_request(pr_id),
    PRIMARY KEY (release_id, pr_id)
);

CREATE TABLE release_note (
    note_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    release_id      UUID         NOT NULL REFERENCES release(release_id),
    tenant_id       VARCHAR(100) NOT NULL DEFAULT 'eli-lilly',
    audience        VARCHAR(30)  NOT NULL,
    content         TEXT         NOT NULL,
    approval_status VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    approved_by     VARCHAR(255),
    approved_at     TIMESTAMP WITH TIME ZONE,
    rejected_reason TEXT,
    sent_at         TIMESTAMP WITH TIME ZONE,
    provenance_class VARCHAR(30) NOT NULL DEFAULT 'AI_INFERENCE',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (release_id, audience)
);

CREATE TABLE audit_event (
    audit_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      VARCHAR(100) NOT NULL DEFAULT 'eli-lilly',
    actor          VARCHAR(255) NOT NULL,
    action         VARCHAR(100) NOT NULL,
    entity_type    VARCHAR(100),
    entity_id      VARCHAR(255),
    detail         TEXT,
    correlation_id VARCHAR(100),
    timestamp      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE job (
    job_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    VARCHAR(100) NOT NULL DEFAULT 'eli-lilly',
    kind         VARCHAR(100) NOT NULL,
    payload      JSONB        NOT NULL,
    status       VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    attempts     INTEGER      NOT NULL DEFAULT 0,
    max_attempts INTEGER      NOT NULL DEFAULT 5,
    next_run_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    locked_by    VARCHAR(255),
    locked_at    TIMESTAMP WITH TIME ZONE,
    last_error   TEXT,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_job_claim          ON job (status, next_run_at);
CREATE INDEX idx_pr_merge_sha       ON pull_request (tenant_id, repo_name, merge_sha);
CREATE INDEX idx_pr_repo_number     ON pull_request (tenant_id, repo_name, pr_number);
CREATE INDEX idx_audit_tenant_time  ON audit_event (tenant_id, timestamp);
CREATE INDEX idx_release_tenant     ON release (tenant_id, created_at);
CREATE INDEX idx_webhook_processed  ON webhook_event (processed, received_at);
