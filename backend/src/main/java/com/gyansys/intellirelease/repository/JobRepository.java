package com.gyansys.intellirelease.repository;

import com.gyansys.intellirelease.model.Job;
import com.gyansys.intellirelease.model.enums.JobStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    /**
     * Candidate jobs whose backoff has elapsed. Selection is deliberately
     * lock-free; the actual claim is the conditional UPDATE below, which is what
     * makes concurrent workers safe.
     */
    @Query("""
            SELECT j FROM Job j
             WHERE j.status = :status
               AND j.nextRunAt <= :now
             ORDER BY j.nextRunAt ASC
            """)
    List<Job> findClaimable(@Param("status") JobStatus status,
                            @Param("now") OffsetDateTime now,
                            Pageable pageable);

    /**
     * Atomic claim. Returns 1 for the worker that won the row and 0 for every
     * worker that lost it, which is the portable equivalent of
     * {@code SELECT ... FOR UPDATE SKIP LOCKED} — H2 has no SKIP LOCKED, and a
     * conditional UPDATE is correct on both engines.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Job j
               SET j.status = com.gyansys.intellirelease.model.enums.JobStatus.RUNNING,
                   j.lockedBy = :worker,
                   j.lockedAt = :now,
                   j.attempts = j.attempts + 1
             WHERE j.jobId = :jobId
               AND j.status = com.gyansys.intellirelease.model.enums.JobStatus.PENDING
            """)
    int claim(@Param("jobId") UUID jobId,
              @Param("worker") String worker,
              @Param("now") OffsetDateTime now);

    List<Job> findByStatusOrderByCreatedAtDesc(JobStatus status, Pageable pageable);
}
