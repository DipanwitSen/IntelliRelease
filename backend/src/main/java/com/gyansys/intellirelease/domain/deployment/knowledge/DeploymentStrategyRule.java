package com.gyansys.intellirelease.domain.deployment.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gyansys.intellirelease.model.enums.DeploymentStrategyType;

import java.util.List;

/**
 * One entry from {@code deployment_strategy_rules.json}: the deployment
 * strategy this platform recommends whenever a changed file resolves to the
 * given SAP Commerce artifact type.
 *
 * <p>{@code priority} is what lets a pull request touching several artifact
 * types resolve to one recommendation: the engine takes the highest-priority
 * matched rule across every changed file. Priorities are intentionally
 * banded so that {@code MIGRATE} rules (75-100) always outrank {@code
 * ROLLING} rules (1-35) — see {@link DeploymentStrategyRuleSet} for the
 * invariant this file must preserve.
 *
 * @param artifactType      the knowledge base's stable artifact type key
 *                          (e.g. {@code "items_xml"}), matching {@code
 *                          FileContext#artifactType()}
 * @param strategy          the recommended deployment strategy
 * @param priority          resolves conflicts across multiple changed files;
 *                          higher wins
 * @param reason            human-readable justification, shown verbatim on
 *                          the dashboard's "Reason" list
 * @param recommendedActions the checklist shown on the dashboard's
 *                          "Recommendations" list
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeploymentStrategyRule(
        String artifactType,
        DeploymentStrategyType strategy,
        int priority,
        String reason,
        List<String> recommendedActions
) {
    public DeploymentStrategyRule {
        recommendedActions = recommendedActions == null ? List.of() : List.copyOf(recommendedActions);
    }
}
