package com.gyansys.intellirelease.domain.readiness;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import com.gyansys.intellirelease.model.enums.ReadinessStatus;

import java.util.List;

/**
 * The readiness verdict: a score, a status, and every signal behind them.
 *
 * <p>Decision support, never decision authority. The release manager owns
 * go/no-go; this exists so that call is made against evidence instead of
 * tribal memory.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadinessResult(
        int score,
        ReadinessStatus status,
        List<ReadinessFactor> factors,
        List<String> warnings,
        String engineVersion,
        ProvenanceClass provenanceClass
) {

    public ReadinessResult {
        factors = factors == null ? List.of() : List.copyOf(factors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** Factors that reduced the score. What a release manager reads first. */
    public List<ReadinessFactor> attentionFlags() {
        return factors.stream().filter(factor -> factor.contribution() < 0).toList();
    }

    public boolean isBlocked() {
        return factors.stream().anyMatch(factor -> factor.blocker() && factor.contribution() == 0);
    }
}
