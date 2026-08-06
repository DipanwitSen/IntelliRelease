package com.gyansys.intellirelease.domain.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyansys.intellirelease.domain.context.ContextResult;
import com.gyansys.intellirelease.domain.context.FileContext;
import com.gyansys.intellirelease.domain.deployment.knowledge.DeploymentStrategyKnowledgeBase;
import com.gyansys.intellirelease.model.enums.ConfidenceLevel;
import com.gyansys.intellirelease.model.enums.DeploymentStrategyType;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The behaviour that matters most here is priority resolution: a pull
 * request touching both a facade and {@code items.xml} must still come out
 * MIGRATE, exactly as the "Rule Priority" example in the feature spec
 * describes. Everything else is confirming the engine never invents a
 * recommendation from nothing.
 */
class DeploymentStrategyEngineTest {

    private DeploymentStrategyEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DeploymentStrategyEngine(new DeploymentStrategyKnowledgeBase(new ObjectMapper()));
    }

    @Test
    @DisplayName("recommends MIGRATE for an items.xml change")
    void itemsXmlRecommendsMigrate() {
        DeploymentStrategyResult result = engine.evaluate(
                context(fileContext("core/resources/core-items.xml", "items_xml", "Type System Definition")));

        assertThat(result.strategy()).isEqualTo(DeploymentStrategyType.MIGRATE);
        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(result.reasons()).anySatisfy(reason -> assertThat(reason).contains("core-items.xml"));
        assertThat(result.recommendedActions()).contains("Execute Migrate Deployment");
        assertThat(result.provenanceClass()).isEqualTo(ProvenanceClass.RULE_OUTPUT);
    }

    @Test
    @DisplayName("recommends ROLLING for a facade-only change")
    void facadeOnlyRecommendsRolling() {
        DeploymentStrategyResult result = engine.evaluate(context(
                fileContext("core/facades/impl/DefaultCheckoutFacade.java", "facade", "Facade")));

        assertThat(result.strategy()).isEqualTo(DeploymentStrategyType.ROLLING);
        assertThat(result.reasons()).contains("No database schema changes", "No Type System changes");
        assertThat(result.recommendedActions()).containsExactly("Rolling Deployment", "Smoke Tests");
    }

    @Test
    @DisplayName("a facade change and an items.xml change in the same PR still resolve to MIGRATE")
    void migrateWinsOverRollingRegardlessOfOrder() {
        DeploymentStrategyResult result = engine.evaluate(context(
                fileContext("core/facades/impl/DefaultCheckoutFacade.java", "facade", "Facade"),
                fileContext("web/controllers/ProductController.java", "occ_controller", "OCC REST Controller"),
                fileContext("core/resources/core-items.xml", "items_xml", "Type System Definition")));

        assertThat(result.strategy())
                .as("one migrate-tier file among several rolling-tier files must still win")
                .isEqualTo(DeploymentStrategyType.MIGRATE);
        // The facade/controller reasons must not leak into a MIGRATE verdict —
        // only the migrate-tier evidence belongs in the explanation.
        assertThat(result.reasons()).noneMatch(reason -> reason.contains("Facade") || reason.contains("Controller"));
    }

    @Test
    @DisplayName("a solr.impex change recommends rolling deployment plus a reindex")
    void solrChangeRecommendsReindex() {
        DeploymentStrategyResult result = engine.evaluate(
                context(fileContext("resources/solr/solr.impex", "solr_config", "Solr Search Configuration")));

        assertThat(result.strategy()).isEqualTo(DeploymentStrategyType.ROLLING);
        assertThat(result.recommendedActions()).contains("Perform Solr Re-index");
    }

    @Test
    @DisplayName("multiple files sharing an artifact type are deduplicated into one reason")
    void deduplicatesReasonsPerArtifactType() {
        DeploymentStrategyResult result = engine.evaluate(context(
                fileContext("core/facades/impl/DefaultCheckoutFacade.java", "facade", "Facade"),
                fileContext("core/facades/impl/DefaultProductFacade.java", "facade", "Facade")));

        long facadeReasons = result.reasons().stream().filter(reason -> reason.contains("Facade")).count();
        assertThat(facadeReasons).isEqualTo(1);
        assertThat(result.reasons().get(0)).contains("+1 more");
    }

    @Test
    @DisplayName("an unclassified file lowers confidence but never fabricates a strategy")
    void unclassifiedFileLowersConfidence() {
        DeploymentStrategyResult result = engine.evaluate(context(
                fileContext("core/facades/impl/DefaultCheckoutFacade.java", "facade", "Facade"),
                FileContext.unclassified("some/unknown/path.txt")));

        assertThat(result.strategy()).isEqualTo(DeploymentStrategyType.ROLLING);
        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(result.unclassifiedFileCount()).isEqualTo(1);
        assertThat(result.classifiedFileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("no changed files never asserts a real recommendation")
    void emptyContextIsUnavailable() {
        DeploymentStrategyResult result = engine.evaluate(null);

        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.matches()).isEmpty();
    }

    @Test
    @DisplayName("release aggregation inherits MIGRATE from a single pull request among many ROLLING ones")
    void releaseAggregationInheritsTheRiskiestPr() {
        DeploymentStrategyResult rollingPr = engine.evaluate(
                context(fileContext("web/controllers/ProductController.java", "occ_controller", "OCC REST Controller")));
        DeploymentStrategyResult migratePr = engine.evaluate(
                context(fileContext("core/resources/core-items.xml", "items_xml", "Type System Definition")));

        DeploymentStrategyResult release = engine.aggregate(List.of(rollingPr, migratePr));

        assertThat(release.strategy())
                .as("the release inherits MIGRATE even though only one of two pull requests needed it")
                .isEqualTo(DeploymentStrategyType.MIGRATE);
    }

    @Test
    @DisplayName("a release with no resolved pull requests never asserts a recommendation")
    void releaseAggregationWithNoPrsIsUnavailable() {
        DeploymentStrategyResult release = engine.aggregate(List.of());

        assertThat(release.confidence()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(release.matches()).isEmpty();
    }

    // ------------------------------------------------------------------

    private static ContextResult context(FileContext... files) {
        List<FileContext> fileList = List.of(files);
        long unclassified = fileList.stream().filter(file -> file.artifactType() == null).count();
        return new ContextResult(fileList, Set.of(), fileList.size(), (int) unclassified, false, "test",
                ProvenanceClass.DERIVED_FACT);
    }

    private static FileContext fileContext(String path, String artifactType, String displayName) {
        return new FileContext(
                path, null, null, ProvenanceClass.DERIVED_FACT, List.of(), ConfidenceLevel.HIGH,
                "test fixture", "test", artifactType, displayName, "test role", List.of(), "medium",
                List.of(), List.of());
    }
}
