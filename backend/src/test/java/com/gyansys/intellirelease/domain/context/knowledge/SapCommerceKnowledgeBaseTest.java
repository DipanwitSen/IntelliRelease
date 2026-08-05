package com.gyansys.intellirelease.domain.context.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the knowledge base actually loads from the classpath resource and
 * that Java NIO glob matching behaves the way the SAP Commerce team's
 * patterns assume — in particular that a leading {@code **}{@code /} matches
 * zero directory segments, since several real changed-file paths (a root
 * {@code manifest.json}, a shallow {@code resources/*-items.xml}) rely on
 * that.
 */
class SapCommerceKnowledgeBaseTest {

    private SapCommerceKnowledgeBase knowledgeBase;

    @BeforeEach
    void setUp() {
        knowledgeBase = new SapCommerceKnowledgeBase(new ObjectMapper());
    }

    @Test
    void loadsTheKnowledgeBase() {
        assertThat(knowledgeBase.ruleCount()).isGreaterThan(50);
        assertThat(knowledgeBase.artifactTypeCount()).isGreaterThan(40);
        assertThat(knowledgeBase.version()).isEqualTo("1.0.0");
    }

    @Test
    void classifiesItemsXmlNestedUnderAnExtension() {
        assertArtifact("coreextension/resources/customcore-items.xml", "items_xml");
    }

    @Test
    void classifiesItemsXmlWithNoLeadingDirectory() {
        // Regression guard: a leading **/ must match zero segments too, or a
        // file sitting directly under "resources/" is missed entirely.
        assertArtifact("resources/customcore-items.xml", "items_xml");
    }

    @Test
    void classifiesAFacadeOverAGenericService() {
        // *Facade*.java (priority 85) must win over the generic *Service*.java
        // fallback (priority 78) when a name could plausibly match both.
        assertArtifact("facadesextension/src/com/client/facades/impl/DefaultCheckoutFacade.java", "facade");
    }

    @Test
    void classifiesSecurityImpexAsCritical() {
        SapCommerceKnowledgeBase.Match match = assertArtifact(
                "coreextension/resources/import/security/access-rights.impex", "impex_security");
        assertThat(match.definition().deploymentRisk()).isEqualTo("critical");
    }

    @Test
    void classifiesAnOccController() {
        assertArtifact("occextension/src/com/client/occ/controllers/CartController.java", "occ_controller");
    }

    @Test
    void classifiesARootLevelManifest() {
        assertArtifact("manifest.json", "cloud_manifest");
    }

    @Test
    void classifiesAFrontendComponent() {
        assertArtifact("storefrontapp/src/app/product/product.component.ts", "frontend_component");
    }

    @Test
    void classifiesCronjobImpexAcrossAnIntermediateDirectory() {
        assertArtifact("coreextension/resources/common/cronjobs.impex", "impex_cronjob");
    }

    @Test
    void leavesAnUnrecognisedPathUnmatched() {
        assertThat(knowledgeBase.classify("some/random/file.xyz")).isEmpty();
    }

    private SapCommerceKnowledgeBase.Match assertArtifact(String path, String expectedArtifactType) {
        Optional<SapCommerceKnowledgeBase.Match> match = knowledgeBase.classify(path);
        assertThat(match).as("classification for " + path).isPresent();
        assertThat(match.get().artifactType()).isEqualTo(expectedArtifactType);
        assertThat(match.get().definition()).isNotNull();
        return match.get();
    }
}
