package com.gyansys.intellirelease.domain.deployment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.model.enums.DeploymentStrategyType;

import java.util.List;

/**
 * One changed file's contribution to the deployment strategy decision — the
 * SAP Commerce artifact type it resolved to, the strategy that artifact type
 * requires, and the priority that decided whether it won.
 *
 * <p>Kept on {@link DeploymentStrategyResult} so a Release Manager can trace
 * the final recommendation back to the exact file that drove it, the same
 * evidence-to-verdict traceability every other engine in this platform
 * provides.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeploymentStrategyMatch(
        String filePath,
        String artifactType,
        String artifactDisplayName,
        DeploymentStrategyType strategy,
        int priority,
        String reason,
        List<String> recommendedActions
) {
    public DeploymentStrategyMatch {
        recommendedActions = recommendedActions == null ? List.of() : List.copyOf(recommendedActions);
    }
}
