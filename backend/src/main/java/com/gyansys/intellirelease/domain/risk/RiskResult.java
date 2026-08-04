package com.gyansys.intellirelease.domain.risk;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import com.gyansys.intellirelease.model.enums.RiskLevel;

import java.util.List;

/**
 * A risk verdict: the number, the band, and every reason behind them.
 *
 * <p>Provenance is always {@link ProvenanceClass#RULE_OUTPUT}. The AI service
 * receives this record and may narrate it; it can never alter
 * {@link #score()} or {@link #level()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RiskResult(
        int score,
        RiskLevel level,
        List<RiskReason> reasons,
        String policyVersion,
        ProvenanceClass provenanceClass
) {

    public RiskResult {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static RiskResult of(int score, List<RiskReason> reasons) {
        int capped = Math.min(score, RiskPolicy.MAX_SCORE);
        return new RiskResult(
                capped,
                RiskLevel.fromScore(capped, RiskPolicy.LOW_THRESHOLD, RiskPolicy.HIGH_THRESHOLD),
                reasons,
                RiskPolicy.POLICY_VERSION,
                ProvenanceClass.RULE_OUTPUT
        );
    }

    public static RiskResult none() {
        return of(0, List.of());
    }

    /** e.g. "type system change +20, no linked tests +10". For narration. */
    public String summarise() {
        if (reasons.isEmpty()) {
            return "No risk rules fired for this change";
        }
        return reasons.stream()
                .map(reason -> reason.label().toLowerCase() + " +" + reason.weight())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }
}
