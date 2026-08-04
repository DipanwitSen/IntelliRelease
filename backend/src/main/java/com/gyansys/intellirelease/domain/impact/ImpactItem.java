package com.gyansys.intellirelease.domain.impact;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.model.enums.ConfidenceLevel;
import com.gyansys.intellirelease.model.enums.ImpactType;
import com.gyansys.intellirelease.model.enums.SapCapability;

import java.util.List;

/**
 * One capability this change may have affected, with the reason attached.
 *
 * <p>An item without {@code evidence} is a claim nobody can defend, so the
 * analyzer never emits one.
 *
 * @param capability  the affected SAP Commerce capability
 * @param impactType  CONFIRMED (directly changed) or POTENTIAL (reachable via a dependency)
 * @param confidence  strength of the claim
 * @param evidence    why this capability is listed, in words
 * @param sourceFiles the changed files that led here
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImpactItem(
        SapCapability capability,
        ImpactType impactType,
        ConfidenceLevel confidence,
        String evidence,
        List<String> sourceFiles
) {

    public ImpactItem {
        sourceFiles = sourceFiles == null ? List.of() : List.copyOf(sourceFiles);
    }

    public String displayName() {
        return capability.getDisplayName();
    }
}
