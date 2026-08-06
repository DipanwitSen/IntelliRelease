-- IntelliRelease v2 :: deployment confirmation (H2, local development only)
-- Mirror of db/migration/postgresql/V3__deployment_tracking.sql.
--
-- Deployment is a fact distinct from release notes being sent: RELEASED
-- (see ReleaseStatus) means communications went out, not that the code is
-- live. This is the human-asserted confirmation that it actually is.

ALTER TABLE release ADD COLUMN deployed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE release ADD COLUMN deployed_by VARCHAR(255);
