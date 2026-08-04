package com.gyansys.intellirelease.domain.regression;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;

import java.util.List;

/**
 * Suggested regression focus for a change or a release.
 *
 * <p>{@link #disclaimer()} ships with every result and is rendered in the UI.
 * The engine narrows the search; it does not certify coverage, and QA still owns
 * that decision. Claiming otherwise would be the fastest way to lose a QA
 * team's trust permanently.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegressionResult(
        List<RegressionSuggestion> suggestions,
        int suiteCount,
        String disclaimer,
        ProvenanceClass provenanceClass
) {

    public static final String STANDARD_DISCLAIMER =
            "Suggested focus, not sufficient coverage. QA owns the coverage decision.";

    public RegressionResult {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    public static RegressionResult of(List<RegressionSuggestion> suggestions) {
        return new RegressionResult(
                suggestions,
                suggestions == null ? 0 : suggestions.size(),
                STANDARD_DISCLAIMER,
                ProvenanceClass.RULE_OUTPUT
        );
    }

    public static RegressionResult empty() {
        return of(List.of());
    }

    public List<String> suiteNames() {
        return suggestions.stream().map(RegressionSuggestion::suite).distinct().toList();
    }
}
