package com.gyansys.intellirelease.domain.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyansys.intellirelease.domain.errors.ErrorModel.ErrorExplanation;
import com.gyansys.intellirelease.domain.integration.IntegrationCatalog;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The behaviour that matters here is not "does it match" — it is
 * <em>"does it refuse to match when it should not"</em>. A confidently wrong
 * root cause sends an engineer down the wrong path for an hour; these tests
 * pin the honesty as tightly as the recognition.
 */
class ErrorIntelligenceEngineTest {

    private static ErrorIntelligenceEngine engine;

    @BeforeAll
    static void loadCatalogue() {
        ObjectMapper objectMapper = new ObjectMapper();
        engine = new ErrorIntelligenceEngine(objectMapper, new IntegrationCatalog(objectMapper));
    }

    @Test
    @DisplayName("loads the catalogue with patterns")
    void loadsCatalogue() {
        assertThat(engine.patterns()).isNotEmpty();
        assertThat(engine.version()).isNotBlank();
    }

    @Test
    @DisplayName("recognises a missing-column import failure")
    void recognisesNoColumn() {
        ErrorExplanation result = engine.explain(
                "com.example.impex.ImportException: no column found for header 'MIN_QTY' in price feed",
                null, null);

        assertThat(result.matched()).isTrue();
        assertThat(result.pattern().code()).isEqualTo("NO_COLUMN");
        assertThat(result.provenance()).isEqualTo(ProvenanceClass.RULE_OUTPUT);
        assertThat(result.pattern().rootCauses()).isNotEmpty();
        assertThat(result.pattern().recommendedTests()).isNotEmpty();
    }

    @Test
    @DisplayName("recognises an authentication failure from an HTTP status")
    void recognisesAuthFailure() {
        ErrorExplanation result = engine.explain(
                "Call to the order service failed: HTTP 401 invalid_client, token expired", null, null);

        assertThat(result.matched()).isTrue();
        assertThat(result.pattern().code()).isEqualTo("AUTH_FAILED");
    }

    @Test
    @DisplayName("does not mistake a large order value for an HTTP 500")
    void statusCodesNeedWordBoundaries() {
        // Without the boundary check in the status matcher, "1500.00" contains
        // "500" and every large invoice would look like a server error.
        ErrorExplanation result = engine.explain(
                "Order total was 1500.00 USD and the shipment weight was 2408 kg.", null, null);

        assertThat(result.matched())
                .as("a money amount containing 500 must not match the HTTP-status signature")
                .isFalse();
    }

    @Test
    @DisplayName("reports no match rather than guessing at unrecognised text")
    void refusesToGuess() {
        ErrorExplanation result = engine.explain(
                "The quick brown fox jumps over the lazy dog on a Tuesday afternoon.", null, null);

        assertThat(result.matched()).isFalse();
        assertThat(result.pattern()).isNull();
        assertThat(result.matchConfidence()).isZero();
        assertThat(result.provenance()).isEqualTo(ProvenanceClass.UNKNOWN);
        assertThat(result.unmatchedGuidance())
                .as("an honest non-answer must still tell the user what to do next")
                .isNotBlank();
    }

    @Test
    @DisplayName("handles blank input without throwing")
    void handlesBlankInput() {
        ErrorExplanation result = engine.explain("   ", null, null);

        assertThat(result.matched()).isFalse();
        assertThat(result.unmatchedGuidance()).contains("No text was supplied");
    }

    @Test
    @DisplayName("prefers the specific pattern when several could fire")
    void prefersTheSpecificPattern() {
        // This text carries a generic transport signal (a 500) and a specific
        // one (the material message). The specific pattern must win.
        ErrorExplanation result = engine.explain(
                "HTTP 500 from target. faultstring: Material MAT-88213 not maintained for plant 1010",
                null, null);

        assertThat(result.matched()).isTrue();
        assertThat(result.pattern().code())
                .as("a precise master-data message should outrank a bare status code")
                .isEqualTo("MATERIAL_MISSING");
    }

    @Test
    @DisplayName("surfaces the field named in the error text")
    void extractsQuotedFields() {
        ErrorExplanation result = engine.explain(
                "MappingException: mapping failed, could not resolve 'shippingCondition'", null, null);

        assertThat(result.matched()).isTrue();
        assertThat(result.affectedPayloadFields()).contains("shippingCondition");
    }

    @Test
    @DisplayName("honours an explicit layer hint over the catalogue's candidates")
    void layerHintNarrowsLocation() {
        ErrorExplanation broad = engine.explain("no column found for header 'X'", null, null);
        ErrorExplanation narrowed = engine.explain("no column found for header 'X'", "Import job", null);

        assertThat(narrowed.whereItFailed()).hasSize(1);
        assertThat(narrowed.whereItFailed().get(0).layer()).isEqualTo("Import job");
        assertThat(narrowed.whereItFailed().get(0).confidence()).isEqualTo("HIGH");
        assertThat(broad.whereItFailed().size())
                .as("without a hint every candidate layer is offered, honestly, at lower confidence")
                .isGreaterThanOrEqualTo(narrowed.whereItFailed().size());
    }
}
