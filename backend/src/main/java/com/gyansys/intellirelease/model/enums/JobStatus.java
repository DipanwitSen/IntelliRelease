package com.gyansys.intellirelease.model.enums;

/**
 * PostgreSQL-backed job queue states. A database table with SKIP LOCKED is
 * sufficient here — Kafka and Redis solve problems this POC does not have.
 */
public enum JobStatus {

    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,

    /** Exhausted max_attempts. Recoverable via POST /api/v1/jobs/{id}/reprocess. */
    DEAD_LETTER
}
