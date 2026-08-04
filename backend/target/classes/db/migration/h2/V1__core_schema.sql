-- IntelliRelease v2 :: core schema (H2, local development only)
--
-- Structurally identical to db/migration/postgresql/V1__core_schema.sql.
-- Differences are engine syntax only:
--   gen_random_uuid() -> RANDOM_UUID()
--   JSONB             -> JSON
--   TEXT              -> CLOB
-- Keep the two files in lockstep. PostgreSQL is the persistent target.

CREATE TABLE webhook_event (
    event_id        UUID PRIMARY KEY DEFAULT RANDOM_UUID(),
    tenant_id       VARCHAR(100) NOT NULL DEFAULT 'eli-lilly',
    provider        VARCHAR(50)  NOT NULL,
    delivery_id     VARCHAR(255) NOT NULL,
    event_type      VARCHAR(100),
    raw_payload     JSON         NOT NULL,
    signature_valid BOOLEAN      NOT NULL,
    processed       BOOLEAN      NOT NULL DEFAULT FALSE,
    received_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider, delivery_id)
);

CREATE TABLE pull_request (
    pr_id         UUID PRIMARY KEY DEFAULT RANDOM_UUID(),
    tenant_id     VARCHAR(100) NOT NULL DEFAULT 'eli-lilly',
    repo_name     VARCHAR(255) NOT NULL,
    pr_number     INTEGER      NOT NULL,
    merge_sha     VARCHAR(100) NOT NULL,
    title         VARCHAR(500),
    description   CLOB,
    author        VARCHAR(255),
    branch        VARCHAR(255),
    ticket_key    VARCHAR(100),
    merged_at     TIMESTAMP WITH TIME ZONE,
    changed_files JSON,
    raw_metadata  JSON,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, repo_name, merge_sha)
);

CREATE TABLE pr_analysis (
    pr_id                       UUID PRIMARY KEY REFERENCES pull_request(pr_id),
    tenant_id                   VARCHAR(100) NOT NULL DEFAULT 'eli-lilly',
    sap_commerce_context        JSON         NOT NULL,
    impact_analysis             JSON         NOT NULL,
    regression_recommendation   JSON,
    risk_score                  INTEGER      NOT NULL,
    risk_level                  VARCHAR(20)  NOT NULL,
    risk_reasons                JSON         NOT NULL,
    risk_policy_version         VARCHAR(20),
    configuration_drift         JSON,
    ai_summary                  JSON,
    ai_fallback_used            BOOLEAN      NOT NULL DEFAULT FALSE,
    deployment_readiness_score  INTEGER,
    deployment_readiness_status VARCHAR(30),
    provenance_class            VARCHAR(30)  NOT NULL DEFAULT 'RULE_OUTPUT',
    model_provider              VARCHAR(100),
    model_name                  VARCHAR(100),
    tokens_used                 INTEGER,
    generated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE release (
    release_id           UUID PRIMARY KEY DEFAULT RANDOM_UUID(),
    tenant_id            VARCHAR(100) NOT NULL DEFAULT 'eli-lilly',
    repo_name            VARCHAR(255) NOT NULL,
    version              VARCHAR(100) NOT NULL,
    from_ref             VARCHAR(255) NOT NULL,
    to_ref               VARCHAR(255) NOT NULL,
    resolved_pr_count    INTEGER,
    excluded_prs         JSON,
    aggregate_risk_score INTEGER,
    aggregate_risk_level VARCHAR(20),
    readiness_score      INTEGER,
    readiness_status     VARCHAR(30),
    status               VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, repo_name, version)
);

CREATE TABLE release_pr (
    release_id UUID NOT NULL REFERENCES release(release_id),
    pr_id      UUID NOT NULL REFERENCES pull_request(pr_id),
    PRIMARY KEY (release_id, pr_id)
);

CREATE TABLE release_note (
    note_id          UUID PRIMARY KEY DEFAULT RANDOM_UUID(),
    release_id       UUID         NOT NULL REFERENCES release(release_id),
    tenant_id        VARCHAR(100) NOT NULL DEFAULT 'eli-lilly',
    audience         VARCHAR(30)  NOT NULL,
    content          CLOB         NOT NULL,
    approval_status  VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    approved_by      VARCHAR(255),
    approved_at      TIMESTAMP WITH TIME ZONE,
    rejected_reason  CLOB,
    sent_at          TIMESTAMP WITH TIME ZONE,
    provenance_class VARCHAR(30)  NOT NULL DEFAULT 'AI_INFERENCE',
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (release_id, audience)
);

CREATE TABLE audit_event (
    audit_id       UUID PRIMARY KEY DEFAULT RANDOM_UUID(),
    tenant_id      VARCHAR(100) NOT NULL DEFAULT 'eli-lilly',
    actor          VARCHAR(255) NOT NULL,
    action         VARCHAR(100) NOT NULL,
    entity_type    VARCHAR(100),
    entity_id      VARCHAR(255),
    detail         CLOB,
    correlation_id VARCHAR(100),
    timestamp      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE job (
    job_id       UUID PRIMARY KEY DEFAULT RANDOM_UUID(),
    tenant_id    VARCHAR(100) NOT NULL DEFAULT 'eli-lilly',
    kind         VARCHAR(100) NOT NULL,
    payload      JSON         NOT NULL,
    status       VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    attempts     INTEGER      NOT NULL DEFAULT 0,
    max_attempts INTEGER      NOT NULL DEFAULT 5,
    next_run_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_by    VARCHAR(255),
    locked_at    TIMESTAMP WITH TIME ZONE,
    last_error   CLOB,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_job_claim          ON job (status, next_run_at);
CREATE INDEX idx_pr_merge_sha       ON pull_request (tenant_id, repo_name, merge_sha);
CREATE INDEX idx_pr_repo_number     ON pull_request (tenant_id, repo_name, pr_number);
CREATE INDEX idx_audit_tenant_time  ON audit_event (tenant_id, timestamp);
CREATE INDEX idx_release_tenant     ON release (tenant_id, created_at);
CREATE INDEX idx_webhook_processed  ON webhook_event (processed, received_at);
