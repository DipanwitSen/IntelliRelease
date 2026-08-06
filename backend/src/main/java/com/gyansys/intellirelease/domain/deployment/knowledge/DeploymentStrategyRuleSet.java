package com.gyansys.intellirelease.domain.deployment.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gyansys.intellirelease.model.enums.DeploymentStrategyType;

import java.util.List;

/**
 * Root shape of {@code deployment_strategy_rules.json}.
 *
 * <p>{@code defaultStrategy}/{@code defaultPriority}/{@code defaultReason}
 * are the safety net for an artifact type the SAP Commerce knowledge base
 * can classify but this file has no rule for yet — new artifact types are
 * occasionally added to {@code sap_context.json}, and this platform never
 * lets a missing rule become a silent {@code NullPointerException} or an
 * unexplained recommendation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeploymentStrategyRuleSet(
        Meta meta,
        DeploymentStrategyType defaultStrategy,
        int defaultPriority,
        String defaultReason,
        List<DeploymentStrategyRule> rules
) {
    public DeploymentStrategyRuleSet {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(String name, String version, String purpose) {
    }
}
