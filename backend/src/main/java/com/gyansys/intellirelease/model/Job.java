package com.gyansys.intellirelease.model;

import com.gyansys.intellirelease.model.enums.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A unit of deferred work. Claimed with {@code FOR UPDATE SKIP LOCKED} so
 * multiple workers can drain the queue without contending.
 *
 * <p>Exhausted attempts land in {@link JobStatus#DEAD_LETTER} rather than
 * disappearing — an unanalysed change must be visible, not silently dropped.
 */
@Entity
@Table(name = "job")
@Getter
@Setter
@NoArgsConstructor
public class Job {

    /** Analyse one merged PR end-to-end through the deterministic engines. */
    public static final String KIND_ANALYZE_PR = "ANALYZE_PR";

    /** Ask the AI service to narrate an already-computed analysis. */
    public static final String KIND_AI_EXPLAIN_PR = "AI_EXPLAIN_PR";

    @Id
    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "kind", nullable = false, length = 100)
    private String kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "next_run_at", nullable = false)
    private OffsetDateTime nextRunAt;

    @Column(name = "locked_by", length = 255)
    private String lockedBy;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public static Job create(String tenantId, String kind, String payload) {
        Job job = new Job();
        job.jobId = UUID.randomUUID();
        job.tenantId = tenantId;
        job.kind = kind;
        job.payload = payload;
        job.status = JobStatus.PENDING;
        job.attempts = 0;
        job.maxAttempts = 5;
        job.nextRunAt = OffsetDateTime.now();
        job.createdAt = OffsetDateTime.now();
        return job;
    }

    /** Exponential backoff: 2^attempts seconds, capped at five minutes. */
    public OffsetDateTime backoffFrom(OffsetDateTime now) {
        long seconds = Math.min(300L, (long) Math.pow(2, Math.min(attempts, 8)));
        return now.plusSeconds(seconds);
    }
}
