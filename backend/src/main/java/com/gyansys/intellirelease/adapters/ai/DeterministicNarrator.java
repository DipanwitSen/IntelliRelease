package com.gyansys.intellirelease.adapters.ai;

import com.gyansys.intellirelease.domain.drift.DriftItem;
import com.gyansys.intellirelease.domain.impact.ImpactItem;
import com.gyansys.intellirelease.domain.readiness.ReadinessFactor;
import com.gyansys.intellirelease.domain.readiness.ReadinessResult;
import com.gyansys.intellirelease.domain.regression.RegressionSuggestion;
import com.gyansys.intellirelease.domain.risk.RiskReason;
import com.gyansys.intellirelease.domain.risk.RiskResult;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Templated narration from deterministic output alone.
 *
 * <p>Used when the model is down or its output fails schema validation twice.
 * The prose is drier, but nothing factual is missing — because nothing factual
 * ever came from the model in the first place. Remove the LLM entirely and
 * IntelliRelease still tells you what shipped, what is risky and whether you
 * are ready.
 *
 * <p>Output is tagged {@link ProvenanceClass#RULE_OUTPUT} and flagged
 * {@code fallback = true}, so a reader always knows which they are looking at.
 * Silently substituting templates for model output would be the dishonest
 * version of this feature.
 */
@Component
public class DeterministicNarrator {

    public AiAnalysisResponse narrate(AiAnalysisRequest request) {
        RiskResult risk = request.riskResult();
        var impact = request.impactAnalysis();
        var regression = request.regressionSuggestions();
        var drift = request.configurationDrift();
        ReadinessResult readiness = request.deploymentReadiness();

        String capabilities = impact == null ? "none identified"
                : impact.confirmedImpact().stream()
                        .map(ImpactItem::displayName)
                        .collect(Collectors.joining(", "));

        String technical = "PR #" + request.prNumber() + " — " + safe(request.title()) + ". "
                + "SAP Commerce capabilities directly changed: " + capabilities + ". "
                + (request.sapCommerceContext() == null ? ""
                   : request.sapCommerceContext().fileCount() + " file(s) analysed, "
                     + request.sapCommerceContext().unclassifiedCount() + " unclassified. ")
                + riskSentence(risk);

        String qa = "Suggested regression focus: "
                + (regression == null || regression.suggestions().isEmpty()
                   ? "no suites suggested"
                   : regression.suggestions().stream()
                           .map(RegressionSuggestion::suite)
                           .collect(Collectors.joining(", ")))
                + ". " + (regression == null ? "" : regression.disclaimer());

        String business = "This change affects " + capabilities + ". "
                + riskSentence(risk) + " "
                + (impact == null || impact.potentialImpact().isEmpty() ? ""
                   : "Areas that may also be affected and should be verified: "
                     + impact.potentialImpact().stream()
                             .map(ImpactItem::displayName)
                             .collect(Collectors.joining(", ")) + ".");

        String client = "An update was made to " + capabilities.toLowerCase()
                + ". This change has been analysed and risk-assessed before release.";

        return new AiAnalysisResponse(
                technical,
                qa,
                business,
                client,
                explainRisk(risk),
                regression == null ? "" : regression.disclaimer(),
                explainReadiness(readiness),
                ProvenanceClass.RULE_OUTPUT,
                true,
                "deterministic-template",
                "none",
                0
        );
    }

    public AiSynthesisResponse narrateRelease(AiSynthesisRequest request) {
        String prList = request.pullRequests().isEmpty()
                ? "no changes resolved"
                : request.pullRequests().stream()
                        .map(pr -> "#" + pr.prNumber() + " " + safe(pr.title())
                                + " (risk " + pr.riskScore() + " " + pr.riskLevel() + ")")
                        .collect(Collectors.joining("; "));

        String excluded = request.excludedPrs().isEmpty()
                ? "No changes were excluded from this release."
                : request.excludedPrs().size() + " change(s) were excluded: "
                  + request.excludedPrs().stream()
                          .map(pr -> (pr.prNumber() == null ? pr.sha() : "#" + pr.prNumber())
                                  + " (" + pr.reason() + ")")
                          .collect(Collectors.joining("; ")) + ".";

        String driftLine = describeDrift(request);

        String developer = "Release " + request.version() + " (" + request.fromRef()
                + " -> " + request.toRef() + ") contains " + request.includedPrCount()
                + " change(s): " + prList + ". " + excluded + " " + driftLine;

        String qa = "Suggested regression focus for release " + request.version() + ": "
                + (request.regressionSuggestions() == null ? "none suggested"
                   : String.join(", ", request.regressionSuggestions().suiteNames()))
                + ". " + (request.regressionSuggestions() == null ? ""
                          : request.regressionSuggestions().disclaimer());

        String business = "Release " + request.version() + " delivers " + request.includedPrCount()
                + " change(s). " + riskSentence(request.aggregateRisk()) + " " + excluded;

        String client = "Release " + request.version() + " includes " + request.includedPrCount()
                + " update(s) to the platform. " + excluded
                + " This summary is generated from the changes that actually shipped.";

        return new AiSynthesisResponse(
                developer,
                qa,
                business,
                client,
                "Release " + request.version() + ": " + request.includedPrCount() + " change(s) shipped.",
                explainRisk(request.aggregateRisk()),
                driftLine,
                explainReadiness(request.deploymentReadiness()),
                ProvenanceClass.RULE_OUTPUT,
                true,
                "deterministic-template",
                "none",
                0
        );
    }

    // ------------------------------------------------------------------

    private String riskSentence(RiskResult risk) {
        if (risk == null) {
            return "No risk analysis is available.";
        }
        return "Risk scored " + risk.score() + " (" + risk.level() + ").";
    }

    private String explainRisk(RiskResult risk) {
        if (risk == null || risk.reasons().isEmpty()) {
            return "No risk rules fired for this change.";
        }
        String breakdown = risk.reasons().stream()
                .map(reason -> reason.label() + " +" + reason.weight() + " (" + reason.evidence() + ")")
                .collect(Collectors.joining("; "));
        return "Risk " + risk.score() + " " + risk.level() + " under policy "
                + risk.policyVersion() + ". Contributing rules: " + breakdown + ".";
    }

    private String explainReadiness(ReadinessResult readiness) {
        if (readiness == null) {
            return "Deployment readiness has not been evaluated for this release.";
        }
        String factors = readiness.factors().stream()
                .map(factor -> factor.label() + " " + factor.contribution()
                        + (factor.maximum() > 0 ? "/" + factor.maximum() : ""))
                .collect(Collectors.joining(", "));
        String warnings = readiness.warnings().isEmpty() ? ""
                : " Attention: " + String.join(" ", readiness.warnings());
        return "Deployment readiness " + readiness.score() + "/100 — " + readiness.status()
                + ". Composition: " + factors + "." + warnings;
    }

    private String describeDrift(AiSynthesisRequest request) {
        if (request.configurationDrift() == null || !request.configurationDrift().baselineAvailable()) {
            return "Configuration drift could not be verified: no production baseline was available.";
        }
        List<DriftItem> material = request.configurationDrift().materialDrifts();
        if (material.isEmpty()) {
            return "No material configuration drift was detected against the production baseline.";
        }
        return material.size() + " material configuration drift(s) detected: "
                + material.stream()
                        .map(item -> item.settingKey() + " " + item.baselineValue()
                                + " -> " + item.currentValue())
                        .collect(Collectors.joining(", ")) + ".";
    }

    private static String safe(String value) {
        return value == null ? "(untitled)" : value;
    }
}
