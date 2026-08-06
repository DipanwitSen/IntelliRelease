package com.gyansys.intellirelease.domain.deployment.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads {@code deployment_strategy_rules.json} once at startup and answers
 * "what deployment strategy does this SAP Commerce artifact type require?"
 *
 * <p>Deliberately data, not Java: the same guarantee
 * {@link com.gyansys.intellirelease.domain.context.knowledge.SapCommerceKnowledgeBase}
 * makes for file classification. A release engineer who knows SAP Commerce —
 * not a Java developer — can teach the platform about a new artifact type,
 * or change an existing recommendation, by editing one JSON file. See
 * {@code docs/deployment-strategy-advisor.md} for the walkthrough.
 */
@Component
public class DeploymentStrategyKnowledgeBase {

    private static final String RESOURCE_PATH = "/deployment-strategy/deployment_strategy_rules.json";

    private final DeploymentStrategyRuleSet ruleSet;
    private final Map<String, DeploymentStrategyRule> rulesByArtifactType;

    public DeploymentStrategyKnowledgeBase(ObjectMapper objectMapper) {
        this.ruleSet = load(objectMapper);
        Map<String, DeploymentStrategyRule> byType = new LinkedHashMap<>();
        for (DeploymentStrategyRule rule : ruleSet.rules()) {
            byType.put(rule.artifactType(), rule);
        }
        this.rulesByArtifactType = Map.copyOf(byType);
    }

    /** The rule for this artifact type, if one is configured. */
    public Optional<DeploymentStrategyRule> ruleFor(String artifactType) {
        return Optional.ofNullable(rulesByArtifactType.get(artifactType));
    }

    /**
     * The safety net for an artifact type the Context Engine can classify but
     * this rule set has no explicit entry for — see
     * {@link DeploymentStrategyRuleSet}.
     */
    public DeploymentStrategyRule defaultRule() {
        return new DeploymentStrategyRule(
                null, ruleSet.defaultStrategy(), ruleSet.defaultPriority(), ruleSet.defaultReason(),
                java.util.List.of());
    }

    public String version() {
        return ruleSet.meta() == null ? "unknown" : ruleSet.meta().version();
    }

    public int ruleCount() {
        return rulesByArtifactType.size();
    }

    private static DeploymentStrategyRuleSet load(ObjectMapper objectMapper) {
        try (InputStream in = DeploymentStrategyKnowledgeBase.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Deployment strategy rule set not found on classpath: " + RESOURCE_PATH);
            }
            return objectMapper.readValue(in, DeploymentStrategyRuleSet.class);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load deployment strategy rule set", exception);
        }
    }
}
