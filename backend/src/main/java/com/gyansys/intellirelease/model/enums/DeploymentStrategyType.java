package com.gyansys.intellirelease.model.enums;

/**
 * The two SAP Commerce deployment strategies the Deployment Strategy Engine
 * chooses between. Deliberately just two: this platform does not model a
 * blue/green or canary strategy today, and inventing a third value here
 * would be UI the engine can never actually recommend.
 */
public enum DeploymentStrategyType {

    /**
     * Updates one application node at a time while the rest keep serving
     * requests. Valid only when old and new nodes can coexist — no database
     * schema or type system change.
     */
    ROLLING,

    /**
     * Requires a system update / full-environment rollout because the change
     * modifies the SAP Commerce type system, database schema, or something
     * else (a versioned data migration, a topology change) that old and new
     * nodes cannot safely disagree about.
     */
    MIGRATE
}
