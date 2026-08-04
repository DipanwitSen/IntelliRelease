package com.gyansys.intellirelease.infra;

import com.gyansys.intellirelease.model.Job;

/**
 * Handles one kind of queued work.
 *
 * <p>Implementations must be idempotent: a worker can crash after doing the
 * work but before marking the job succeeded, and the next worker will run it
 * again. Upserting on a natural key rather than inserting is the usual shape.
 */
public interface JobHandler {

    /** The {@code Job.kind} this handler claims. */
    String kind();

    /**
     * Performs the work. Throwing schedules a retry with exponential backoff
     * until {@code maxAttempts} is exhausted, after which the job is moved to
     * DEAD_LETTER rather than discarded.
     */
    void handle(Job job) throws Exception;
}
