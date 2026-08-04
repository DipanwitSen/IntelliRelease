package com.gyansys.intellirelease.domain.cleanup;

import com.gyansys.intellirelease.model.enums.ConfidenceLevel;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Understands Hybris cleanup cronjobs and recommends retention tuning.
 *
 * <p>Runs on a schedule, not per pull request — it answers a question about the
 * platform's health rather than about a change. Its output feeds Deployment
 * Readiness on the next release cycle.
 *
 * <p>Long-lived commerce platforms accumulate cronjob debt: retention policies
 * set at go-live that nobody has revisited since. Nobody has time to audit them
 * by hand, so nobody does.
 */
@Service
public class CleanupIntelligenceEngine {

    public static final String ENGINE_VERSION = "cleanup-engine-2026.1";

    /** Below this margin, tightening retention is not worth the change control. */
    private static final int MINIMUM_MEANINGFUL_REDUCTION_DAYS = 30;

    /** Never recommend below this, whatever the stats say. In-flight windows matter. */
    private static final int RETENTION_FLOOR_DAYS = 30;

    public List<CleanupAdvice> analyze(List<CronjobStat> stats) {
        if (stats == null || stats.isEmpty()) {
            return List.of();
        }
        List<CleanupAdvice> advice = new ArrayList<>(stats.size());
        for (CronjobStat stat : stats) {
            advice.add(evaluate(stat));
        }
        return advice;
    }

    private CleanupAdvice evaluate(CronjobStat stat) {
        List<String> evidence = new ArrayList<>();
        evidence.add(stat.executionsAnalyzed() + " historical executions analysed");
        evidence.add("Current retention: " + stat.currentRetentionDays() + " days");
        evidence.add("Configured business retention requirement: " + stat.businessRetentionDays() + " days");
        evidence.add("Table size: " + stat.tableSizeGb() + " GB");

        if (!stat.isHealthy()) {
            evidence.add(stat.failureCount() + " of " + stat.executionsAnalyzed()
                    + " runs failed — fix execution before tuning retention");
            return new CleanupAdvice(
                    stat.cronjobName(),
                    stat.currentRetentionDays(),
                    null,
                    stat.tableSizeGb(),
                    0,
                    ConfidenceLevel.LOW,
                    false,
                    "This cleanup job is failing. Retention tuning is premature while the job "
                            + "is not completing successfully — a shorter window on a job that never "
                            + "runs changes nothing.",
                    evidence,
                    ProvenanceClass.RULE_OUTPUT
            );
        }

        int recommended = Math.max(RETENTION_FLOOR_DAYS, stat.businessRetentionDays());
        int reductionDays = stat.currentRetentionDays() - recommended;

        if (reductionDays < MINIMUM_MEANINGFUL_REDUCTION_DAYS) {
            evidence.add("Current retention is already close to the business requirement");
            return new CleanupAdvice(
                    stat.cronjobName(),
                    stat.currentRetentionDays(),
                    null,
                    stat.tableSizeGb(),
                    0,
                    ConfidenceLevel.HIGH,
                    true,
                    "Retention is appropriate. No change recommended.",
                    evidence,
                    ProvenanceClass.RULE_OUTPUT
            );
        }

        int reductionPct = estimateReductionPct(stat.currentRetentionDays(), recommended);
        ConfidenceLevel confidence = confidenceFor(stat);

        evidence.add("Estimated table reduction: ~" + reductionPct + "%");
        evidence.add("Recommendation floors at " + RETENTION_FLOOR_DAYS
                + " days to retain in-flight approval windows");

        String rationale = stat.cronjobName() + " runs successfully ("
                + stat.executionsAnalyzed() + " executions analysed, no failures). "
                + "Retention of " + stat.currentRetentionDays() + " days exceeds the configured "
                + "business need of " + stat.businessRetentionDays() + " days; "
                + recommended + " days retains all in-flight windows while reclaiming an estimated "
                + reductionPct + "% of a " + stat.tableSizeGb() + " GB table.";

        return new CleanupAdvice(
                stat.cronjobName(),
                stat.currentRetentionDays(),
                recommended,
                stat.tableSizeGb(),
                reductionPct,
                confidence,
                true,
                rationale,
                evidence,
                ProvenanceClass.RULE_OUTPUT
        );
    }

    /**
     * Row volume is assumed roughly uniform over time, so the proportion of the
     * window removed approximates the proportion of rows removed. Stated as an
     * estimate, and never presented as a measurement.
     */
    private int estimateReductionPct(int currentDays, int recommendedDays) {
        if (currentDays <= 0) {
            return 0;
        }
        BigDecimal removed = BigDecimal.valueOf(currentDays - recommendedDays);
        BigDecimal ratio = removed.divide(BigDecimal.valueOf(currentDays), 4, RoundingMode.HALF_UP);
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /**
     * Confidence tracks how much history backs the recommendation. Twelve runs
     * is a pattern; three is an anecdote.
     */
    private ConfidenceLevel confidenceFor(CronjobStat stat) {
        if (stat.executionsAnalyzed() >= 30) {
            return ConfidenceLevel.HIGH;
        }
        if (stat.executionsAnalyzed() >= 10) {
            return ConfidenceLevel.MEDIUM;
        }
        return ConfidenceLevel.LOW;
    }
}
