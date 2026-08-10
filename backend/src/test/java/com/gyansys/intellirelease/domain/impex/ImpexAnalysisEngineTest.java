package com.gyansys.intellirelease.domain.impex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the engine against realistic ImpEx: a CMS content-slot/component
 * script (the shape the feature was built for) and the actual
 * IntegrationObject script from the test repository's PR #8, so both the
 * "made up example" and "real data" paths are covered.
 */
class ImpexAnalysisEngineTest {

    private final ImpexAnalysisEngine engine = new ImpexAnalysisEngine();

    @Test
    @DisplayName("no .impex file touched -> untouched, not an empty populated shape")
    void untouchedWhenNothingToAnalyze() {
        ImpexModel.ImpexAnalysis result = engine.analyze(Map.of(), List.of());

        assertThat(result.touched()).isFalse();
        assertThat(result.contentUnavailable()).isFalse();
        assertThat(result.operationCounts().total()).isZero();
    }

    @Test
    @DisplayName(".impex touched but content could not be fetched -> unavailable, not zero counts")
    void unavailableWhenContentMissing() {
        ImpexModel.ImpexAnalysis result = engine.analyze(Map.of(), List.of("resources/cms.impex"));

        assertThat(result.touched()).isTrue();
        assertThat(result.contentUnavailable()).isTrue();
        assertThat(result.filesWithoutContent()).containsExactly("resources/cms.impex");
        assertThat(result.items()).isEmpty();
    }

    @Test
    @DisplayName("counts INSERT_UPDATE/UPDATE/REMOVE per CMS type and links components to slots and pages")
    void countsAndLinksCmsContent() {
        String impex = """
                $lang=en

                INSERT_UPDATE ContentSlot; uid[unique=true]; name; cmsComponents(uid)
                ; HeaderSlot                ; Header Slot ; HeaderBannerComponent,HeaderLinksComponent
                ; FooterSlot                ; Footer Slot ; FooterLinksComponent

                INSERT_UPDATE CMSParagraphComponent; uid[unique=true]; content
                ; HeaderBannerComponent              ; Welcome banner text

                INSERT_UPDATE CMSLinkComponent; uid[unique=true]; url
                ; HeaderLinksComponent          ; /help
                ; FooterLinksComponent          ; /contact

                UPDATE CMSParagraphComponent; uid[unique=true]; content
                ; HeaderBannerComponent     ; Updated welcome banner text

                INSERT_UPDATE ContentSlotForPage; uid[unique=true]; contentSlot(uid); page(uid); position
                ; HeaderSlotForHomepage               ; HeaderSlot       ; Homepage  ; TopHeaderSlot

                REMOVE ContentSlot; uid[unique=true]
                ; OldPromoSlot
                """;

        ImpexModel.ImpexAnalysis result = engine.analyze(Map.of("resources/cms.impex", impex), List.of());

        assertThat(result.touched()).isTrue();
        assertThat(result.contentUnavailable()).isFalse();
        assertThat(result.filesAnalyzed()).isEqualTo(1);

        ImpexModel.ImpexOperationCounts counts = result.operationCounts();
        assertThat(counts.insertUpdate()).isEqualTo(6); // 2 slots + 1 paragraph + 2 links + 1 slot-for-page
        assertThat(counts.update()).isEqualTo(1);
        assertThat(counts.remove()).isEqualTo(1);
        assertThat(counts.insert()).isZero();
        assertThat(counts.total()).isEqualTo(8);

        assertThat(result.byType())
                .extracting(ImpexModel.ImpexTypeSummary::itemType, ImpexModel.ImpexTypeSummary::totalCount)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("ContentSlot", 3), // 2 insert_update + 1 remove
                        org.assertj.core.groups.Tuple.tuple("CMSParagraphComponent", 2), // 1 insert_update + 1 update
                        org.assertj.core.groups.Tuple.tuple("CMSLinkComponent", 2),
                        org.assertj.core.groups.Tuple.tuple("ContentSlotForPage", 1));

        // The HeaderSlot row links to both components it carries.
        ImpexModel.ImpexItemView headerSlot = result.items().stream()
                .filter(item -> "ContentSlot".equals(item.itemType()) && "HeaderSlot".equals(item.key()))
                .findFirst().orElseThrow();
        assertThat(headerSlot.links()).hasSize(1);
        assertThat(headerSlot.links().get(0).field()).isEqualTo("cmsComponents");
        assertThat(headerSlot.links().get(0).targetKeys())
                .containsExactly("HeaderBannerComponent", "HeaderLinksComponent");

        // ContentSlotForPage links to both the slot and the page it binds together.
        ImpexModel.ImpexItemView slotForPage = result.items().stream()
                .filter(item -> "ContentSlotForPage".equals(item.itemType()))
                .findFirst().orElseThrow();
        assertThat(slotForPage.links())
                .extracting(ImpexModel.ImpexLink::field)
                .containsExactlyInAnyOrder("contentSlot", "page");
        assertThat(slotForPage.fields()).containsEntry("position", "TopHeaderSlot");

        // The REMOVE row is counted and surfaced even though it has no other columns.
        ImpexModel.ImpexItemView removed = result.items().stream()
                .filter(item -> "REMOVE".equals(item.mode()))
                .findFirst().orElseThrow();
        assertThat(removed.itemType()).isEqualTo("ContentSlot");
        assertThat(removed.key()).isEqualTo("OldPromoSlot");
    }

    @Test
    @DisplayName("real ImpEx from the test repository (PR #8, loyalty-integration-setup.impex) parses correctly")
    void parsesRealIntegrationObjectImpex() {
        // Verbatim from github.com/sbkanungo/test-the-IntelliRelease PR #8.
        String impex = """
                $lang=en
                INSERT_UPDATE IntegrationObject; code[unique=true]
                ; LoyaltyPointsSync

                INSERT_UPDATE IntegrationObjectItem; integrationObject(code)[unique=true]; code[unique=true]
                ; LoyaltyPointsSync           ; LoyaltyPointsBalance
                """;

        ImpexModel.ImpexAnalysis result = engine.analyze(
                Map.of("core/resources/loyalty-integration-setup.impex", impex), List.of());

        assertThat(result.operationCounts().insertUpdate()).isEqualTo(2);
        assertThat(result.operationCounts().total()).isEqualTo(2);
        assertThat(result.byType())
                .extracting(ImpexModel.ImpexTypeSummary::itemType)
                .containsExactlyInAnyOrder("IntegrationObject", "IntegrationObjectItem");

        ImpexModel.ImpexItemView item = result.items().stream()
                .filter(i -> "IntegrationObjectItem".equals(i.itemType()))
                .findFirst().orElseThrow();
        assertThat(item.key()).isEqualTo("LoyaltyPointsSync"); // first unique column wins
        assertThat(item.links()).hasSize(1);
        assertThat(item.links().get(0).field()).isEqualTo("integrationObject");
        assertThat(item.links().get(0).targetKeys()).containsExactly("LoyaltyPointsSync");
    }
}
