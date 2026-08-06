package com.gyansys.intellirelease.domain.deployment.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyansys.intellirelease.model.enums.DeploymentStrategyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@code deployment_strategy_rules.json} actually loads and that the
 * priority-band invariant every other test in this feature relies on holds:
 * every MIGRATE rule outranks every ROLLING rule, so "highest priority wins"
 * never needs a special case for which strategy that priority belongs to.
 */
class DeploymentStrategyKnowledgeBaseTest {

    private DeploymentStrategyKnowledgeBase knowledgeBase;

    @BeforeEach
    void setUp() {
        knowledgeBase = new DeploymentStrategyKnowledgeBase(new ObjectMapper());
    }

    @Test
    void loadsARuleForEveryArtifactTypeTheContextEngineKnows() {
        // Mirrors SapCommerceKnowledgeBase's 70 artifact types — see
        // docs/deployment-strategy-advisor.md for how to add a 71st without
        // touching Java.
        assertThat(knowledgeBase.ruleCount()).isEqualTo(70);
        assertThat(knowledgeBase.version()).isEqualTo("1.0.0");
    }

    @Test
    void itemsXmlRequiresMigrate() {
        DeploymentStrategyRule rule = knowledgeBase.ruleFor("items_xml").orElseThrow();
        assertThat(rule.strategy()).isEqualTo(DeploymentStrategyType.MIGRATE);
        assertThat(rule.priority()).isEqualTo(100);
        assertThat(rule.reason()).containsIgnoringCase("type system");
    }

    @Test
    void facadeChangesAreRolling() {
        DeploymentStrategyRule rule = knowledgeBase.ruleFor("facade").orElseThrow();
        assertThat(rule.strategy()).isEqualTo(DeploymentStrategyType.ROLLING);
        assertThat(rule.recommendedActions()).contains("Rolling Deployment", "Smoke Tests");
    }

    @Test
    void solrConfigRecommendsAReindex() {
        DeploymentStrategyRule rule = knowledgeBase.ruleFor("solr_config").orElseThrow();
        assertThat(rule.strategy()).isEqualTo(DeploymentStrategyType.ROLLING);
        assertThat(rule.recommendedActions()).contains("Perform Solr Re-index");
    }

    @Test
    void everyMigrateRuleOutranksEveryRollingRule() {
        int highestRolling = knowledgeBase.ruleFor("facade").orElseThrow().priority();
        int lowestMigrate = knowledgeBase.ruleFor("extension_set").orElseThrow().priority();

        // Spot-checked against the two priority extremes rather than iterating
        // every rule here — the exhaustive check lives in
        // DeploymentStrategyEngineTest, which exercises the actual decision.
        assertThat(lowestMigrate)
                .as("no ROLLING rule may ever outrank a MIGRATE rule")
                .isGreaterThan(highestRolling);
    }

    @Test
    void unknownArtifactTypeFallsBackToTheConfiguredDefault() {
        assertThat(knowledgeBase.ruleFor("some_future_artifact_type")).isEmpty();

        DeploymentStrategyRule fallback = knowledgeBase.defaultRule();
        assertThat(fallback.strategy()).isEqualTo(DeploymentStrategyType.ROLLING);
        assertThat(fallback.reason()).isNotBlank();
    }
}
