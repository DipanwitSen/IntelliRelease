package com.gyansys.intellirelease.infra;

import com.gyansys.intellirelease.config.IntelliReleaseProperties;
import com.gyansys.intellirelease.model.Job;
import com.gyansys.intellirelease.model.enums.JobStatus;
import com.gyansys.intellirelease.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A PostgreSQL-backed work queue.
 *
 * <p>A table plus a conditional UPDATE is enough here. Kafka would add an
 * operational dependency to solve a throughput problem this workload does not
 * have, and Redis would add a second source of truth for state the database
 * already holds transactionally.
 *
 * <p>Failure semantics: retry with exponential backoff, then DEAD_LETTER —
 * visible and replayable through {@code POST /api/v1/jobs/{id}/reprocess}.
 * A change that failed to analyse must never simply vanish.
 */
@Component
public class JobQueue {

    private static final Logger log = LoggerFactory.getLogger(JobQueue.class);

    private final JobRepository jobRepository;
    private final JsonMapper jsonMapper;
    private final TenantContext tenantContext;
    private final IntelliReleaseProperties.Jobs config;
    private final Map<String, JobHandler> handlers;
    private final String workerId;

    public JobQueue(JobRepository jobRepository,
                    JsonMapper jsonMapper,
                    TenantContext tenantContext,
                    IntelliReleaseProperties properties,
                    List<JobHandler> handlerList) {
        this.jobRepository = jobRepository;
        this.jsonMapper = jsonMapper;
        this.tenantContext = tenantContext;
        this.config = properties.jobs();
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(JobHandler::kind, Function.identity()));
        this.workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Enqueues work. The payload is stored as JSONB. */
    @Transactional
    public Job enqueue(String kind, Object payload) {
        Job job = Job.create(tenantContext.currentTenantId(), kind, jsonMapper.toJson(payload));
        Job saved = jobRepository.save(job);
        log.debug("Enqueued job kind={} id={}", kind, saved.getJobId());
        return saved;
    }

    /**
     * Polls for due work and runs it. Each job runs in its own transaction so a
     * poison message cannot roll back its siblings.
     *
     * <p>{@code @Transactional} is required here, not just on the mark* methods
     * below: {@link JobRepository#claim} is a {@code @Modifying} query with
     * {@code flushAutomatically = true}, and a flush has nowhere to go without an
     * active transaction on this thread. The per-job try/catch in
     * {@link #runClaimed} still absorbs any single job's failure before it can
     * reach this method, so one poison message still cannot roll back its
     * siblings even though they now share a physical transaction.
     */
    @Scheduled(fixedDelayString = "${intellirelease.jobs.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        if (!config.workerEnabled()) {
            return;
        }
        List<Job> candidates = jobRepository.findClaimable(
                JobStatus.PENDING, OffsetDateTime.now(), PageRequest.of(0, config.batchSize()));

        for (Job candidate : candidates) {
            // Exactly one worker wins the conditional UPDATE; the rest see 0 rows.
            if (jobRepository.claim(candidate.getJobId(), workerId, OffsetDateTime.now()) == 0) {
                continue;
            }
            runClaimed(candidate.getJobId());
        }
    }

    private void runClaimed(UUID jobId) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }

        MDC.put(CorrelationIdFilter.MDC_KEY, "job-" + jobId);
        try {
            JobHandler handler = handlers.get(job.getKind());
            if (handler == null) {
                failPermanently(job, "No handler registered for job kind " + job.getKind());
                return;
            }
            handler.handle(job);
            markSucceeded(job);
        } catch (Exception exception) {
            log.warn("Job {} kind={} attempt {}/{} failed: {}",
                    job.getJobId(), job.getKind(), job.getAttempts(), job.getMaxAttempts(),
                    exception.getMessage());
            markFailed(job, exception);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    @Transactional
    protected void markSucceeded(Job job) {
        job.setStatus(JobStatus.SUCCEEDED);
        job.setLockedBy(null);
        job.setLockedAt(null);
        job.setLastError(null);
        jobRepository.save(job);
    }

    @Transactional
    protected void markFailed(Job job, Exception exception) {
        job.setLastError(truncate(exception.toString()));
        job.setLockedBy(null);
        job.setLockedAt(null);

        if (job.getAttempts() >= job.getMaxAttempts()) {
            job.setStatus(JobStatus.DEAD_LETTER);
            log.error("Job {} kind={} exhausted {} attempts and moved to DEAD_LETTER",
                    job.getJobId(), job.getKind(), job.getMaxAttempts());
        } else {
            job.setStatus(JobStatus.PENDING);
            job.setNextRunAt(job.backoffFrom(OffsetDateTime.now()));
        }
        jobRepository.save(job);
    }

    @Transactional
    protected void failPermanently(Job job, String reason) {
        job.setStatus(JobStatus.DEAD_LETTER);
        job.setLastError(reason);
        job.setLockedBy(null);
        job.setLockedAt(null);
        jobRepository.save(job);
        log.error("Job {} moved to DEAD_LETTER: {}", job.getJobId(), reason);
    }

    /** Requeues a dead-lettered job. Used by the DLQ recovery endpoint. */
    @Transactional
    public Job reprocess(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown job " + jobId));
        job.setStatus(JobStatus.PENDING);
        job.setAttempts(0);
        job.setNextRunAt(OffsetDateTime.now());
        job.setLastError(null);
        return jobRepository.save(job);
    }

    private static String truncate(String message) {
        return message == null || message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
