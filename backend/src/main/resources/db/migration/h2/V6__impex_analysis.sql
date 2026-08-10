-- IntelliRelease :: ImpEx Analysis Engine (H2, local development only)
-- Mirror of db/migration/postgresql/V6__impex_analysis.sql.
--
-- Stores what a pull request's ImpEx files actually declare: every
-- INSERT_UPDATE/UPDATE/REMOVE/INSERT block, per-item-type operation counts,
-- and the reference links between rows (e.g. a component's contentSlot
-- qualifier) found by the ImpEx Analysis Engine. Null only for analyses
-- recorded before this engine existed; every analysis run afterward stores
-- at least the "untouched" sentinel — see ImpexModel.ImpexAnalysis.

ALTER TABLE pr_analysis ADD COLUMN impex_analysis JSON;
