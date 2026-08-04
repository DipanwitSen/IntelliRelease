package com.gyansys.intellirelease.domain.regression;

import com.gyansys.intellirelease.domain.impact.ImpactItem;
import com.gyansys.intellirelease.domain.impact.ImpactResult;
import com.gyansys.intellirelease.domain.risk.RiskResult;
import com.gyansys.intellirelease.model.enums.ConfidenceLevel;
import com.gyansys.intellirelease.model.enums.RiskLevel;
import com.gyansys.intellirelease.model.enums.SapCapability;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns QA scoping from discovery into review.
 *
 * <p>Confirmed impact always produces suggestions. Potential impact only widens
 * the net once risk warrants it — a LOW-risk change that suggests twenty suites
 * teaches QA to ignore the tool, and an ignored tool is worse than no tool.
 *
 * <p>Every suggestion is labelled <em>suggested focus</em>, never
 * <em>sufficient coverage</em>. See {@link RegressionResult#disclaimer()}.
 */
@Service
public class RegressionRecommender {

    private final RegressionSuiteLibrary library;

    public RegressionRecommender(RegressionSuiteLibrary library) {
        this.library = library;
    }

    public RegressionResult recommend(ImpactResult impact, RiskResult risk) {
        if (impact == null) {
            return RegressionResult.empty();
        }

        RiskLevel level = risk == null ? RiskLevel.LOW : risk.level();

        // Suite name -> suggestion. First writer wins, so a suite justified by
        // confirmed impact is never downgraded by a later potential match.
        Map<String, RegressionSuggestion> bySuite = new LinkedHashMap<>();

        for (ImpactItem item : impact.confirmedImpact()) {
            addSuites(bySuite, item, ConfidenceLevel.HIGH,
                    "Confirmed impact on " + item.capability().getDisplayName());
        }

        // Risk decides how far past the confirmed set we look.
        if (level != RiskLevel.LOW) {
            for (ImpactItem item : impact.potentialImpact()) {
                // On MEDIUM, only follow the strong edges. On HIGH, follow all.
                if (level == RiskLevel.MEDIUM && item.confidence() == ConfidenceLevel.LOW) {
                    continue;
                }
                addSuites(bySuite, item, downgrade(item.confidence()),
                        "Potential impact on " + item.capability().getDisplayName()
                                + " — " + item.evidence());
            }
        }

        if (level == RiskLevel.HIGH) {
            bySuite.putIfAbsent("Full Regression Smoke", new RegressionSuggestion(
                    "Full Regression Smoke",
                    SapCapability.UNCLASSIFIED,
                    ConfidenceLevel.MEDIUM,
                    "Release-level risk scored HIGH (" + risk.score() + "): a broad smoke pass is "
                            + "advised in addition to the targeted suites above",
                    List.of()
            ));
        }

        List<RegressionSuggestion> ordered = new ArrayList<>(bySuite.values());
        ordered.sort(Comparator
                .comparingInt((RegressionSuggestion suggestion) -> suggestion.confidence().ordinal())
                .thenComparing(RegressionSuggestion::suite));

        return RegressionResult.of(ordered);
    }

    /** Merges several per-PR results into one release-level recommendation. */
    public RegressionResult merge(List<RegressionResult> results) {
        if (results == null || results.isEmpty()) {
            return RegressionResult.empty();
        }

        Map<String, RegressionSuggestion> bySuite = new LinkedHashMap<>();
        for (RegressionResult result : results) {
            for (RegressionSuggestion suggestion : result.suggestions()) {
                bySuite.merge(suggestion.suite(), suggestion, RegressionRecommender::keepStronger);
            }
        }

        List<RegressionSuggestion> ordered = new ArrayList<>(bySuite.values());
        ordered.sort(Comparator
                .comparingInt((RegressionSuggestion suggestion) -> suggestion.confidence().ordinal())
                .thenComparing(RegressionSuggestion::suite));

        return RegressionResult.of(ordered);
    }

    private void addSuites(Map<String, RegressionSuggestion> target, ImpactItem item,
                           ConfidenceLevel confidence, String evidence) {
        for (String suite : library.suitesFor(item.capability())) {
            target.putIfAbsent(suite, new RegressionSuggestion(
                    suite, item.capability(), confidence, evidence, item.sourceFiles()));
        }
    }

    /** A suite reachable only by inference is one notch less certain. */
    private static ConfidenceLevel downgrade(ConfidenceLevel confidence) {
        return switch (confidence) {
            case HIGH -> ConfidenceLevel.MEDIUM;
            case MEDIUM, LOW -> ConfidenceLevel.LOW;
        };
    }

    private static RegressionSuggestion keepStronger(RegressionSuggestion left, RegressionSuggestion right) {
        return left.confidence().ordinal() <= right.confidence().ordinal() ? left : right;
    }
}
