package com.gyansys.intellirelease.domain.errors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;

import java.util.List;

/**
 * Error Intelligence domain types.
 *
 * <p>The layer a failure surfaced in is a plain {@code String}, never an enum
 * of {Commerce, CPI, S4}. A landscape with no middleware, or with MuleSoft
 * where CPI would be, has to be describable without the model forcing a lie —
 * and a customer must be able to add their own layer names to the catalogue
 * without a release of this application.
 */
public final class ErrorModel {

    private ErrorModel() {
    }

    /* ===================================================================
       Catalogue
       =================================================================== */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Catalogue(Meta meta, int confidenceFloor, List<ErrorPattern> patterns) {
        public Catalogue {
            patterns = patterns == null ? List.of() : List.copyOf(patterns);
            // A floor of zero would make every unmatched blob "match" the first
            // pattern with one weak signature. 25 is the documented default.
            confidenceFloor = confidenceFloor <= 0 ? 25 : confidenceFloor;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(String name, String version, String purpose, String generalizationPolicy) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorPattern(
            String id,
            String code,
            String title,
            String businessExplanation,
            String technicalExplanation,
            String category,
            String severity,
            List<ErrorLayer> layers,
            List<ErrorSignature> signatures,
            List<RootCause> rootCauses,
            List<SuggestedFix> suggestedFixes,
            List<String> recommendedTests,
            List<RelatedArtifact> relatedArtifacts,
            List<String> relatedPatternIds,
            List<DocumentationLink> documentationLinks,
            List<String> tags
    ) {
        public ErrorPattern {
            layers = nullSafe(layers);
            signatures = nullSafe(signatures);
            rootCauses = nullSafe(rootCauses);
            suggestedFixes = nullSafe(suggestedFixes);
            recommendedTests = nullSafe(recommendedTests);
            relatedArtifacts = nullSafe(relatedArtifacts);
            relatedPatternIds = nullSafe(relatedPatternIds);
            documentationLinks = nullSafe(documentationLinks);
            tags = nullSafe(tags);
        }

        /** Total signature weight — the denominator for match confidence. */
        public int totalWeight() {
            return signatures.stream().mapToInt(ErrorSignature::weight).sum();
        }

        public String searchableText() {
            return String.join(" ", code, title, businessExplanation, technicalExplanation,
                    category, String.join(" ", tags));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorLayer(String layer, String whatHappensHere, String typicalSymptom,
                             List<String> whereToLook) {
        public ErrorLayer {
            whereToLook = nullSafe(whereToLook);
        }
    }

    /**
     * @param kind   EXCEPTION_CLASS, MESSAGE_REGEX, HTTP_STATUS, SOAP_FAULT_CODE, ERROR_CODE
     * @param weight how much this signature contributes. Specific signatures
     *               carry more weight than generic ones, which is what stops a
     *               bare "500" outranking a precise exception class.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorSignature(String kind, String value, int weight) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RootCause(String id, String cause, String likelihood, String howToConfirm, String layer) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SuggestedFix(String id, String summary, String detail, String appliesTo,
                               String effort, String risk, ProvenanceClass provenance) {
        public SuggestedFix {
            // Catalogue fixes are curated rules, not model output. Stating that
            // here means the UI cannot accidentally present one as AI advice.
            provenance = provenance == null ? ProvenanceClass.RULE_OUTPUT : provenance;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RelatedArtifact(String kind, String name, String path, String system) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DocumentationLink(String label, String url, String reference, String kind) {
    }

    /* ===================================================================
       Classification output
       =================================================================== */

    public record ErrorExplanation(
            String inputExcerpt,
            boolean matched,
            ErrorPattern pattern,
            int matchConfidence,
            List<String> matchedSignatures,
            String whatHappened,
            List<ErrorLocation> whereItFailed,
            List<InterfaceRef> affectedInterfaces,
            List<String> affectedPayloadFields,
            List<AffectedRelease> affectedReleases,
            List<SimilarIncident> similarIncidents,
            String narrative,
            ProvenanceClass provenance,
            String unmatchedGuidance
    ) {
        /**
         * Nothing in the catalogue recognised the input.
         *
         * <p>Returned deliberately rather than falling back to the
         * highest-scoring near-miss. A confidently wrong explanation sends an
         * engineer down the wrong path for an hour; "I do not recognise this,
         * here is what to check" costs them a minute.
         */
        public static ErrorExplanation unmatched(String excerpt, String guidance) {
            return new ErrorExplanation(excerpt, false, null, 0, List.of(),
                    "No catalogued pattern matched this text.", List.of(), List.of(), List.of(),
                    List.of(), List.of(), null, ProvenanceClass.UNKNOWN, guidance);
        }
    }

    public record ErrorLocation(String layer, String component, String detail,
                                String evidence, String confidence) {
    }

    /** Enough to link back into the Integration Center without a second lookup. */
    public record InterfaceRef(String id, String name) {
    }

    public record AffectedRelease(String releaseId, String version, String repoName, String deployedAt) {
    }

    public record SimilarIncident(String id, String title, String occurredAt,
                                  String resolution, int similarity) {
    }

    /** A recorded occurrence, so the module has a history and not just a catalogue. */
    public record ErrorOccurrence(
            String id, String patternId, String title, String category, String severity,
            String layer, String interfaceId, String correlationId, String occurredAt,
            int count, boolean resolved, String excerpt
    ) {
    }

    private static <T> List<T> nullSafe(List<T> value) {
        return value == null ? List.of() : List.copyOf(value);
    }
}
