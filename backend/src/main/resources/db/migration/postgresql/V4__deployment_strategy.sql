-- IntelliRelease :: Deployment Strategy Advisor
-- Structurally identical to db/migration/h2/V4__deployment_strategy.sql.
--
-- Deterministic ROLLING/MIGRATE recommendation from the Deployment Strategy
-- Engine, computed right after the SAP Commerce Context Engine for every
-- pull request, and aggregated onto the release once its contents are built.

ALTER TABLE pr_analysis ADD COLUMN deployment_strategy      JSONB;
ALTER TABLE pr_analysis ADD COLUMN deployment_strategy_type VARCHAR(20);

ALTER TABLE release ADD COLUMN deployment_strategy      JSONB;
ALTER TABLE release ADD COLUMN deployment_strategy_type VARCHAR(20);
