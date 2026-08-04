package com.gyansys.intellirelease.domain.drift;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.model.enums.DriftClassification;

/**
 * One configuration difference between two environments.
 *
 * @param settingKey     the flattened configuration key
 * @param baselineValue  value in the baseline (typically production)
 * @param currentValue   value in the candidate release
 * @param classification MATERIAL (behaviour-affecting) or COSMETIC
 * @param potentialImpact what this difference could do in production
 * @param evidence       why the engine classified it this way
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DriftItem(
        String settingKey,
        String baselineValue,
        String currentValue,
        DriftClassification classification,
        String potentialImpact,
        String evidence
) {

    public boolean isMaterial() {
        return classification == DriftClassification.MATERIAL;
    }
}
