package com.gyansys.intellirelease.domain.cleanup;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.model.enums.ConfidenceLevel;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;

import java.math.BigDecimal;
import java.util.List;

/**
 * One retention or frequency recommendation, with its reasoning.
 *
 * <p>Advisory, always. Nothing here changes a retention policy — a human
 * approves any change through the same workflow as any other communication.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CleanupAdvice(
        String cronjobName,
        int currentRetentionDays,
        Integer recommendedRetentionDays,
        BigDecimal tableSizeGb,
        int estimatedReductionPct,
        ConfidenceLevel confidence,
        boolean healthy,
        String rationale,
        List<String> evidence,
        ProvenanceClass provenanceClass
) {

    public CleanupAdvice {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public boolean hasRecommendation() {
        return recommendedRetentionDays != null
                && recommendedRetentionDays != currentRetentionDays;
    }
}
