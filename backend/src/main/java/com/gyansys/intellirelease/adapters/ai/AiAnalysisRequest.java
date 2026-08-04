package com.gyansys.intellirelease.adapters.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.domain.context.ContextResult;
import com.gyansys.intellirelease.domain.drift.DriftResult;
import com.gyansys.intellirelease.domain.impact.ImpactResult;
import com.gyansys.intellirelease.domain.readiness.ReadinessResult;
import com.gyansys.intellirelease.domain.regression.RegressionResult;
import com.gyansys.intellirelease.domain.risk.RiskResult;

/**
 * The complete set of inputs the AI service is ever given for one pull request.
 *
 * <p>Read the field list as a security control, because that is what it is.
 * There is no field for source code, no field for a diff, no field for a
 * repository URL and no field for a credential. The model receives the
 * deterministic engines' conclusions and nothing else — so the worst case for a
 * compromised model is bad prose about facts it was already shown, reviewed by
 * a human before it reaches anyone.
 *
 * <p>Adding a field to this record is a security decision, not a feature.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiAnalysisRequest(
        int prNumber,
        String title,
        String ticketKey,
        String repoName,
        ContextResult sapCommerceContext,
        ImpactResult impactAnalysis,
        RiskResult riskResult,
        RegressionResult regressionSuggestions,
        DriftResult configurationDrift,
        ReadinessResult deploymentReadiness
) {
}
