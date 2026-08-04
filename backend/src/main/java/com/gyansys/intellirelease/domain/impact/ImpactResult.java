package com.gyansys.intellirelease.domain.impact;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import com.gyansys.intellirelease.model.enums.SapCapability;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Two deliberately separated lists.
 *
 * <p>Confirmed is what changed. Potential is what <em>might</em> be reachable
 * from what changed. The engine never promotes the second into the first —
 * that boundary is the reason an auditor can trust either one.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImpactResult(
        List<ImpactItem> confirmedImpact,
        List<ImpactItem> potentialImpact,
        ProvenanceClass provenanceClass
) {

    public ImpactResult {
        confirmedImpact = confirmedImpact == null ? List.of() : List.copyOf(confirmedImpact);
        potentialImpact = potentialImpact == null ? List.of() : List.copyOf(potentialImpact);
    }

    public static ImpactResult empty() {
        return new ImpactResult(List.of(), List.of(), ProvenanceClass.RULE_OUTPUT);
    }

    public int confirmedCount() {
        return confirmedImpact.size();
    }

    public int potentialCount() {
        return potentialImpact.size();
    }

    public Set<SapCapability> confirmedCapabilities() {
        return confirmedImpact.stream().map(ImpactItem::capability).collect(Collectors.toSet());
    }

    public Set<SapCapability> allCapabilities() {
        Set<SapCapability> all = confirmedCapabilities();
        all.addAll(potentialImpact.stream().map(ImpactItem::capability).collect(Collectors.toSet()));
        return all;
    }
}
