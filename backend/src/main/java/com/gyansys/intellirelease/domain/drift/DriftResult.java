package com.gyansys.intellirelease.domain.drift;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;

import java.util.List;

/**
 * Configuration drift between a baseline environment and a candidate release.
 *
 * <p>Only {@link #materialCount()} reaches Deployment Readiness. Cosmetic drift
 * is recorded — it is still a difference — but it never becomes a warning,
 * because a readiness score that fires on reformatted comments is a score
 * people learn to override.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DriftResult(
        String baselineEnvironment,
        String candidateEnvironment,
        List<DriftItem> drifts,
        int materialCount,
        int cosmeticCount,
        boolean baselineAvailable,
        ProvenanceClass provenanceClass
) {

    public DriftResult {
        drifts = drifts == null ? List.of() : List.copyOf(drifts);
    }

    /**
     * No baseline configured. Reported explicitly rather than as "no drift" —
     * the difference between "we checked and found nothing" and "we could not
     * check" matters to whoever signs the release off.
     */
    public static DriftResult unavailable(String reason) {
        return new DriftResult(null, null, List.of(), 0, 0, false, ProvenanceClass.UNKNOWN);
    }

    public List<DriftItem> materialDrifts() {
        return drifts.stream().filter(DriftItem::isMaterial).toList();
    }
}
