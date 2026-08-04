package com.gyansys.intellirelease.adapters.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;

/**
 * The four audience notes plus release-level narration.
 *
 * <p>All four are generated from one underlying source of truth, so the
 * business note and the client note cannot contradict each other — a failure
 * mode of hand-written release comms that this design removes structurally.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiSynthesisResponse(
        String developerNote,
        String qaNote,
        String businessNote,
        String clientNote,
        String releaseSummary,
        String knownRisks,
        String knownConsiderations,
        String deploymentRecommendation,
        ProvenanceClass provenanceClass,
        boolean fallback,
        String provider,
        String model,
        Integer tokensUsed
) {

    public AiSynthesisResponse {
        provenanceClass = provenanceClass == null ? ProvenanceClass.AI_INFERENCE : provenanceClass;
    }
}
