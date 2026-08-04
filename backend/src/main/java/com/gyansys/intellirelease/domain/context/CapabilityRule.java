package com.gyansys.intellirelease.domain.context;

import com.gyansys.intellirelease.model.enums.ConfidenceLevel;
import com.gyansys.intellirelease.model.enums.SapCapability;

import java.util.List;
import java.util.function.Predicate;

/**
 * One curated mapping from a file path pattern to its SAP Commerce meaning.
 *
 * <p>Rules are evaluated in declaration order and the first match wins, so the
 * library is ordered most-specific first: {@code DefaultCheckoutFacade.java} is
 * checkout before it is a facade.
 *
 * @param name         stable identifier stored on every classification for traceability
 * @param predicate    the path test
 * @param capability   the Hybris meaning this rule asserts
 * @param consequences downstream considerations that follow from that meaning
 * @param confidence   how firmly the pattern identifies the capability
 * @param evidence     the reason, phrased for a reader rather than a log
 */
public record CapabilityRule(
        String name,
        Predicate<PathFacts> predicate,
        SapCapability capability,
        List<String> consequences,
        ConfidenceLevel confidence,
        String evidence
) {

    public CapabilityRule {
        consequences = consequences == null ? List.of() : List.copyOf(consequences);
    }

    public boolean matches(PathFacts facts) {
        return predicate.test(facts);
    }
}
