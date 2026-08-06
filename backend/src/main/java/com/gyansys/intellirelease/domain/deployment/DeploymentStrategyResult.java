package com.gyansys.intellirelease.domain.deployment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.model.enums.ConfidenceLevel;
import com.gyansys.intellirelease.model.enums.DeploymentStrategyType;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;

import java.util.List;

/**
 * The Deployment Strategy Engine's verdict: ROLLING or MIGRATE, why, and
 * what to do about it.
 *
 * <p>Provenance is always {@link ProvenanceClass#RULE_OUTPUT} — this is a
 * deterministic rule-engine conclusion. The AI service may explain it in
 * {@code deploymentStrategyExplanation}; it can never alter {@link #strategy()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeploymentStrategyResult(
        DeploymentStrategyType strategy,
        ConfidenceLevel confidence,
        List<String> reasons,
        List<String> recommendedActions,
        List<DeploymentStrategyMatch> matches,
        int classifiedFileCount,
        int unclassifiedFileCount,
        String knowledgeBaseVersion,
        ProvenanceClass provenanceClass
) {
    public DeploymentStrategyResult {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        recommendedActions = recommendedActions == null ? List.of() : List.copyOf(recommendedActions);
        matches = matches == null ? List.of() : List.copyOf(matches);
        provenanceClass = provenanceClass == null ? ProvenanceClass.RULE_OUTPUT : provenanceClass;
    }

    /** No changed files to evaluate — never asserted as a real recommendation. */
    public static DeploymentStrategyResult unavailable(String reason) {
        return new DeploymentStrategyResult(
                DeploymentStrategyType.ROLLING, ConfidenceLevel.LOW, List.of(reason), List.of(), List.of(),
                0, 0, null, ProvenanceClass.RULE_OUTPUT);
    }

    /** One line per contributing rule, for narration and audit — e.g. "items.xml modified +100". */
    public String summarise() {
        if (matches.isEmpty()) {
            return "No files matched a deployment strategy rule";
        }
        return matches.stream()
                .map(match -> match.artifactDisplayName() + " (" + match.filePath() + ") +" + match.priority())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }
}
