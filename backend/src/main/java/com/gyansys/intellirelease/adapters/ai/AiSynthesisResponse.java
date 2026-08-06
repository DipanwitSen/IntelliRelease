package com.gyansys.intellirelease.adapters.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;

import java.util.List;

/**
 * The four audience notes, a per-PR changelog, and release-level narration.
 *
 * <p>All of it is generated from one underlying source of truth, so the
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
        List<ChangelogBullet> changelogBullets,
        ProvenanceClass provenanceClass,
        boolean fallback,
        String provider,
        String model,
        Integer tokensUsed
) {

    /** One changelog line for one shipped PR — what a reader of the release notes actually wants. */
    public record ChangelogBullet(Integer prNumber, String ticketKey, String text) {
    }

    public AiSynthesisResponse {
        provenanceClass = provenanceClass == null ? ProvenanceClass.AI_INFERENCE : provenanceClass;
        changelogBullets = changelogBullets == null ? List.of() : List.copyOf(changelogBullets);
    }
}
