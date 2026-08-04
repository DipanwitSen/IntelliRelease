package com.gyansys.intellirelease.domain.cleanup;

import java.math.BigDecimal;

/**
 * Observed execution and growth statistics for one Hybris cleanup cronjob.
 *
 * <p>Provenance: FACT. In the POC these are seeded from
 * {@code sample-data/cronjobs.json} for a representative Hybris installation;
 * in a pilot they come from the customer's own cronjob history and DB stats.
 * The analysis is identical either way — only the source of the numbers differs.
 *
 * @param cronjobName            e.g. SavedValuesCleanupCronJob
 * @param currentRetentionDays   configured retention
 * @param businessRetentionDays  retention the business policy actually requires
 * @param tableSizeGb            current size of the table it prunes
 * @param executionsAnalyzed     how many historical runs were examined
 * @param failureCount           failed runs among those analysed
 * @param averageDurationMinutes mean successful run duration
 */
public record CronjobStat(
        String cronjobName,
        int currentRetentionDays,
        int businessRetentionDays,
        BigDecimal tableSizeGb,
        int executionsAnalyzed,
        int failureCount,
        double averageDurationMinutes
) {

    public boolean isHealthy() {
        return failureCount == 0;
    }

    public double failureRate() {
        return executionsAnalyzed == 0 ? 0.0 : (double) failureCount / executionsAnalyzed;
    }
}
