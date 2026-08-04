package com.gyansys.intellirelease.adapters.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;

/**
 * The AI service's explanations. Schema-validated on the Python side before it
 * ever reaches here.
 *
 * <p>Every field is prose. There is no score, no verdict and no boolean the
 * platform acts on — the model cannot return a risk number or a readiness
 * status because this record has nowhere to put one.
 *
 * @param fallback true when the model failed validation twice and deterministic
 *                 templates produced this content instead. Surfaced in the UI:
 *                 a reader is entitled to know which they are reading.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiAnalysisResponse(
        String technicalSummary,
        String qaSummary,
        String businessSummary,
        String clientSummary,
        String riskExplanation,
        String regressionGuidance,
        String readinessExplanation,
        ProvenanceClass provenanceClass,
        boolean fallback,
        String provider,
        String model,
        Integer tokensUsed
) {

    public AiAnalysisResponse {
        provenanceClass = provenanceClass == null ? ProvenanceClass.AI_INFERENCE : provenanceClass;
    }

    /** Marks a response as deterministic template output rather than model output. */
    public AiAnalysisResponse asFallback() {
        return new AiAnalysisResponse(
                technicalSummary, qaSummary, businessSummary, clientSummary,
                riskExplanation, regressionGuidance, readinessExplanation,
                ProvenanceClass.RULE_OUTPUT, true, "deterministic-template", "none", 0);
    }
}
