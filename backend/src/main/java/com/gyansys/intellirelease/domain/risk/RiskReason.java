package com.gyansys.intellirelease.domain.risk;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One contribution to a risk score, with the evidence that produced it.
 *
 * <p>Scores are only defensible if they decompose. "Risk 40" is a number;
 * "type system change +20 because items.xml changed" is an argument.
 *
 * @param rule       stable rule identifier
 * @param label      what a reader sees, e.g. "Type system change"
 * @param weight     points contributed
 * @param evidence   why the rule fired
 * @param sourceFiles the files that triggered it
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RiskReason(
        String rule,
        String label,
        int weight,
        String evidence,
        List<String> sourceFiles
) {

    public RiskReason {
        sourceFiles = sourceFiles == null ? List.of() : List.copyOf(sourceFiles);
    }

    public static RiskReason of(String rule, String label, int weight, String evidence, List<String> sourceFiles) {
        return new RiskReason(rule, label, weight, evidence, sourceFiles);
    }
}
