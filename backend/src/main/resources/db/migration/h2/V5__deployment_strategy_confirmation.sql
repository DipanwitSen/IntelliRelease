-- IntelliRelease :: Deployment Strategy confirmation (H2, local development only)
-- Mirror of db/migration/postgresql/V5__deployment_strategy_confirmation.sql.
--
-- Records the human decision made on top of the Deployment Strategy Engine's
-- recommendation: which strategy was actually confirmed, by whom, and when.
-- The recommendation itself (deployment_strategy_type, added in V4) is never
-- overwritten by this — the two are kept side by side so an override is
-- visible as a fact, not silently absorbed.

ALTER TABLE pr_analysis ADD COLUMN confirmed_deployment_strategy VARCHAR(20);
ALTER TABLE pr_analysis ADD COLUMN confirmed_by                  VARCHAR(100);
ALTER TABLE pr_analysis ADD COLUMN confirmed_at                  TIMESTAMP WITH TIME ZONE;
