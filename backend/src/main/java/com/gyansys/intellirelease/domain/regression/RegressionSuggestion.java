package com.gyansys.intellirelease.domain.regression;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.model.enums.ConfidenceLevel;
import com.gyansys.intellirelease.model.enums.SapCapability;

import java.util.List;

/**
 * One suggested regression suite, and why QA should run it.
 *
 * @param suite       the regression suite name
 * @param capability  the capability that put it on the list
 * @param confidence  how strongly the evidence supports running it
 * @param evidence    the reason, phrased for a QA engineer
 * @param sourceFiles the changed files behind the suggestion
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegressionSuggestion(
        String suite,
        SapCapability capability,
        ConfidenceLevel confidence,
        String evidence,
        List<String> sourceFiles
) {

    public RegressionSuggestion {
        sourceFiles = sourceFiles == null ? List.of() : List.copyOf(sourceFiles);
    }
}
