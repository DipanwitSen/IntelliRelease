package com.gyansys.intellirelease.domain.risk;

import com.gyansys.intellirelease.domain.context.ContextResult;
import com.gyansys.intellirelease.model.enums.RiskLevel;
import com.gyansys.intellirelease.model.enums.SapCapability;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces an explainable, rule-based risk score for a pull request or release.
 *
 * <p>Deterministic by construction: same evidence, same score, every time. The
 * AI never produces the number and never adjusts it — it can only read
 * {@link RiskResult#reasons()} back in plain language.
 *
 * <p>Every rule below appends a {@link RiskReason} carrying its weight and the
 * files that triggered it, so the score decomposes all the way down to paths.
 */
@Service
public class RiskEngine {

    /**
     * @param context the SAP Commerce meaning of the change (DERIVED_FACT)
     * @return score, band and every contributing reason (RULE_OUTPUT)
     */
    public RiskResult evaluate(ContextResult context) {
        if (context == null || context.fileCount() == 0) {
            return RiskResult.none();
        }

        List<RiskReason> reasons = new ArrayList<>();
        int score = 0;

        if (context.hasCapability(SapCapability.TYPE_SYSTEM)) {
            score += add(reasons, "TYPE_SYSTEM_CHANGE", "Type system change",
                    RiskPolicy.TYPE_SYSTEM_CHANGE,
                    "items.xml changed: database schema, generated models and DTOs are affected, "
                            + "a system update is required and a Solr reindex may be needed",
                    context.pathsFor(SapCapability.TYPE_SYSTEM));
        }

        if (context.hasCapability(SapCapability.SECURITY)) {
            score += add(reasons, "SECURITY_CHANGE", "Security change",
                    RiskPolicy.SECURITY_CHANGE,
                    "Authentication, authorization or session handling was modified",
                    context.pathsFor(SapCapability.SECURITY));
        }

        if (context.hasCapability(SapCapability.OCC_API)) {
            score += add(reasons, "API_CHANGE", "Public API change",
                    RiskPolicy.API_CHANGE,
                    "A published API contract changed: Spartacus, the mobile app and any "
                            + "downstream consumer bind to this surface",
                    context.pathsFor(SapCapability.OCC_API));
        }

        // Checkout and payment share a weight because they share a failure mode:
        // the customer cannot complete a purchase.
        if (context.hasAnyCapability(SapCapability.CHECKOUT_CAPABILITY, SapCapability.PAYMENT)) {
            List<String> sources = new ArrayList<>(context.pathsFor(SapCapability.CHECKOUT_CAPABILITY));
            sources.addAll(context.pathsFor(SapCapability.PAYMENT));
            score += add(reasons, "CHECKOUT_CHANGE", "Checkout or payment modified",
                    RiskPolicy.CHECKOUT_CHANGE,
                    "A revenue-critical path was modified",
                    sources.stream().distinct().toList());
        }

        if (context.hasCapability(SapCapability.CONFIGURATION)) {
            score += add(reasons, "CONFIGURATION_CHANGE", "Configuration change",
                    RiskPolicy.CONFIGURATION_CHANGE,
                    "Environment behaviour changed without a code change — verify against the "
                            + "production baseline",
                    context.pathsFor(SapCapability.CONFIGURATION));
        }

        if (context.hasAnyCapability(SapCapability.SEARCH_CONFIGURATION, SapCapability.SEARCH)) {
            List<String> sources = new ArrayList<>(context.pathsFor(SapCapability.SEARCH_CONFIGURATION));
            sources.addAll(context.pathsFor(SapCapability.SEARCH));
            score += add(reasons, "SEARCH_CHANGE", "Search configuration change",
                    RiskPolicy.SEARCH_CHANGE,
                    "Search behaviour may change for affected terms, facets and listing pages",
                    sources.stream().distinct().toList());
        }

        if (context.hasCapability(SapCapability.DATA_IMPORT)) {
            score += add(reasons, "DATA_IMPORT", "Impex data import",
                    RiskPolicy.DATA_IMPORT,
                    "An Impex script changes data state on the target environment",
                    context.pathsFor(SapCapability.DATA_IMPORT));
        }

        if (context.fileCount() > RiskPolicy.LARGE_CHANGE_FILE_COUNT) {
            score += add(reasons, "LARGE_CHANGE", "Large change",
                    RiskPolicy.LARGE_CHANGE,
                    context.fileCount() + " files changed, above the review threshold of "
                            + RiskPolicy.LARGE_CHANGE_FILE_COUNT,
                    List.of());
        }

        // Absence of evidence, stated as such. We do not claim the change is
        // untested — only that no test file arrived with it.
        if (!context.testsIncluded()) {
            score += add(reasons, "NO_TESTS", "No linked tests",
                    RiskPolicy.NO_TESTS,
                    "No test file accompanied this change",
                    List.of());
        }

        if (context.unclassifiedCount() > 0) {
            score += add(reasons, "UNCLASSIFIED_FILES", "Unclassified files",
                    RiskPolicy.UNCLASSIFIED_FILES,
                    context.unclassifiedCount() + " file(s) matched no curated SAP Commerce pattern "
                            + "and are treated conservatively",
                    context.files().stream()
                            .filter(file -> !file.isClassified())
                            .map(file -> file.filePath())
                            .toList());
        }

        return RiskResult.of(score, reasons);
    }

    /**
     * Release-level risk. Deliberately not a sum: twelve low-risk PRs do not
     * make a catastrophic release. The release inherits its riskiest change and
     * is nudged upward for breadth, which is how a release manager actually
     * reasons about a batch.
     */
    public RiskResult aggregate(List<RiskResult> prRisks) {
        if (prRisks == null || prRisks.isEmpty()) {
            return RiskResult.none();
        }

        RiskResult riskiest = prRisks.stream()
                .max(java.util.Comparator.comparingInt(RiskResult::score))
                .orElse(RiskResult.none());

        int highRiskCount = (int) prRisks.stream().filter(r -> r.level() == RiskLevel.HIGH).count();
        int mediumRiskCount = (int) prRisks.stream().filter(r -> r.level() == RiskLevel.MEDIUM).count();

        List<RiskReason> reasons = new ArrayList<>();
        reasons.add(RiskReason.of("RELEASE_MAX_PR_RISK", "Riskiest change in the release",
                riskiest.score(),
                "The highest-scoring pull request in this release scored " + riskiest.score()
                        + " (" + riskiest.level() + "): " + riskiest.summarise(),
                List.of()));

        int breadth = 0;
        if (highRiskCount > 0) {
            breadth += Math.min(15, highRiskCount * 5);
            reasons.add(RiskReason.of("RELEASE_HIGH_RISK_BREADTH", "Multiple high-risk changes",
                    Math.min(15, highRiskCount * 5),
                    highRiskCount + " pull request(s) in this release scored HIGH individually",
                    List.of()));
        }
        if (mediumRiskCount > 1) {
            int weight = Math.min(10, (mediumRiskCount - 1) * 2);
            breadth += weight;
            reasons.add(RiskReason.of("RELEASE_MEDIUM_RISK_BREADTH", "Several medium-risk changes",
                    weight,
                    mediumRiskCount + " pull requests scored MEDIUM; combined surface area is wider "
                            + "than any single change",
                    List.of()));
        }

        return RiskResult.of(riskiest.score() + breadth, reasons);
    }

    private static int add(List<RiskReason> reasons, String rule, String label, int weight,
                           String evidence, List<String> sources) {
        reasons.add(RiskReason.of(rule, label, weight, evidence, sources));
        return weight;
    }
}
