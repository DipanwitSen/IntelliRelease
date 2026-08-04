package com.gyansys.intellirelease.domain.readiness;

import com.gyansys.intellirelease.domain.cleanup.CleanupAdvice;
import com.gyansys.intellirelease.domain.drift.DriftResult;
import com.gyansys.intellirelease.domain.impact.ImpactResult;
import com.gyansys.intellirelease.domain.risk.RiskResult;

import java.util.List;

/**
 * Everything Deployment Readiness aggregates. Assembled by the application
 * layer so the engine itself stays a pure function of its inputs — same inputs,
 * same score, which is what makes it reproducible in an audit.
 *
 * @param risk                the aggregate risk verdict
 * @param impact              confirmed and potential impact
 * @param drift               configuration drift against the production baseline
 * @param cleanupAdvice       latest scheduled cleanup analysis
 * @param regressionSuggested number of suites the Regression engine suggested
 * @param regressionExecuted  number QA has actually executed
 * @param qaSignedOff         QA's own sign-off state
 * @param humanApproved       whether a human approved external communication
 */
public record ReadinessInput(
        RiskResult risk,
        ImpactResult impact,
        DriftResult drift,
        List<CleanupAdvice> cleanupAdvice,
        int regressionSuggested,
        int regressionExecuted,
        boolean qaSignedOff,
        boolean humanApproved
) {

    public ReadinessInput {
        cleanupAdvice = cleanupAdvice == null ? List.of() : List.copyOf(cleanupAdvice);
    }

    public static ReadinessInput forPullRequest(RiskResult risk, ImpactResult impact,
                                                DriftResult drift, int regressionSuggested) {
        // A single PR carries no QA sign-off or approval state of its own; those
        // are release-level facts. Scored as pending rather than assumed.
        return new ReadinessInput(risk, impact, drift, List.of(),
                regressionSuggested, 0, false, false);
    }
}
