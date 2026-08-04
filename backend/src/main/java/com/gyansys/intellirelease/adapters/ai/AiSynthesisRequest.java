package com.gyansys.intellirelease.adapters.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.domain.drift.DriftResult;
import com.gyansys.intellirelease.domain.impact.ImpactResult;
import com.gyansys.intellirelease.domain.readiness.ReadinessResult;
import com.gyansys.intellirelease.domain.regression.RegressionResult;
import com.gyansys.intellirelease.domain.risk.RiskResult;

import java.util.List;

/**
 * Release-level inputs for the four audience notes.
 *
 * <p>Same containment as {@link AiAnalysisRequest}: aggregated deterministic
 * output only. {@code excludedPrs} is included deliberately — the client note
 * must not announce a change that was reverted before it shipped, and the model
 * can only avoid that if it is told.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiSynthesisRequest(
        String version,
        String repoName,
        String fromRef,
        String toRef,
        int includedPrCount,
        List<PrSummary> pullRequests,
        List<ExcludedPr> excludedPrs,
        ImpactResult impactAnalysis,
        RiskResult aggregateRisk,
        RegressionResult regressionSuggestions,
        DriftResult configurationDrift,
        ReadinessResult deploymentReadiness
) {

    /** One line per shipped change. Title and conclusions, never code. */
    public record PrSummary(int prNumber, String title, String ticketKey,
                            int riskScore, String riskLevel, List<String> capabilities) {
    }

    /** A change that did not ship, and the evidence for why it is excluded. */
    public record ExcludedPr(Integer prNumber, String sha, String reason, String evidence) {
    }

    public AiSynthesisRequest {
        pullRequests = pullRequests == null ? List.of() : List.copyOf(pullRequests);
        excludedPrs = excludedPrs == null ? List.of() : List.copyOf(excludedPrs);
    }
}
