package com.gyansys.intellirelease.domain.deployment;

import com.gyansys.intellirelease.domain.context.ContextResult;
import com.gyansys.intellirelease.domain.context.FileContext;
import com.gyansys.intellirelease.domain.deployment.knowledge.DeploymentStrategyKnowledgeBase;
import com.gyansys.intellirelease.domain.deployment.knowledge.DeploymentStrategyRule;
import com.gyansys.intellirelease.model.enums.ConfidenceLevel;
import com.gyansys.intellirelease.model.enums.DeploymentStrategyType;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recommends ROLLING or MIGRATE for a pull request, from SAP Commerce
 * knowledge alone.
 *
 * <pre>
 *   SAP Commerce Context Engine (already ran)
 *         |
 *         v
 *   Deployment Strategy Engine   &lt;-- this class
 *         |
 *         v
 *   Impact Analysis / Risk Analysis (unaffected, run independently)
 * </pre>
 *
 * <p>This engine reads only the changed-file classifications the Context
 * Engine already produced — never source code, never a diff. It is a pure
 * lookup against {@link DeploymentStrategyKnowledgeBase}'s configuration
 * plus one comparison (highest priority wins); there is no inference, no
 * heuristic and no AI call anywhere in this class. The AI service is handed
 * this result afterward, strictly to write prose explaining it — see
 * {@code AiAnalysisRequest#deploymentStrategy()}.
 */
@Service
public class DeploymentStrategyEngine {

    private static final List<String> MIGRATE_CONFIRMATIONS =
            List.of("Database schema update expected", "System update required");
    private static final List<String> ROLLING_CONFIRMATIONS =
            List.of("No database schema changes", "No Type System changes");

    private final DeploymentStrategyKnowledgeBase knowledgeBase;

    public DeploymentStrategyEngine(DeploymentStrategyKnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    /**
     * @param context the SAP Commerce meaning of every changed file
     *                (DERIVED_FACT) — the sole input to this decision
     * @return the recommended strategy, its evidence, and the action
     *         checklist (RULE_OUTPUT)
     */
    public DeploymentStrategyResult evaluate(ContextResult context) {
        if (context == null || context.fileCount() == 0) {
            return DeploymentStrategyResult.unavailable("No changed files to evaluate.");
        }

        List<DeploymentStrategyMatch> matches = new ArrayList<>();
        int unclassified = 0;
        for (FileContext file : context.files()) {
            if (file.artifactType() == null) {
                unclassified++;
                continue;
            }
            DeploymentStrategyRule rule = knowledgeBase.ruleFor(file.artifactType())
                    .orElseGet(knowledgeBase::defaultRule);
            matches.add(toMatch(file, rule));
        }

        if (matches.isEmpty()) {
            return new DeploymentStrategyResult(
                    DeploymentStrategyType.ROLLING, ConfidenceLevel.LOW,
                    List.of("No changed file could be classified against the SAP Commerce knowledge base — "
                            + "defaulting to rolling deployment; review this change manually."),
                    List.of("Rolling Deployment", "Manual Review"),
                    List.of(), 0, unclassified, knowledgeBase.version(), ProvenanceClass.RULE_OUTPUT);
        }

        return resultFrom(matches, context.files().size() - unclassified, unclassified);
    }

    /**
     * Release-level recommendation: the release inherits the strategy any of
     * its pull requests already required. One PR touching {@code items.xml}
     * is enough to make the whole release a migrate deployment, no matter how
     * many other PRs in it were pure rolling changes.
     *
     * @param prResults each included pull request's own, already-computed
     *                  {@link #evaluate(ContextResult)} result
     */
    public DeploymentStrategyResult aggregate(List<DeploymentStrategyResult> prResults) {
        if (prResults == null || prResults.isEmpty()) {
            return DeploymentStrategyResult.unavailable("No pull requests resolved into this release yet.");
        }

        List<DeploymentStrategyMatch> allMatches = new ArrayList<>();
        int classified = 0;
        int unclassified = 0;
        for (DeploymentStrategyResult result : prResults) {
            allMatches.addAll(result.matches());
            classified += result.classifiedFileCount();
            unclassified += result.unclassifiedFileCount();
        }

        if (allMatches.isEmpty()) {
            return new DeploymentStrategyResult(
                    DeploymentStrategyType.ROLLING, ConfidenceLevel.LOW,
                    List.of("No pull request in this release had a classified change — defaulting to "
                            + "rolling deployment; review manually."),
                    List.of("Rolling Deployment", "Manual Review"),
                    List.of(), classified, unclassified, knowledgeBase.version(), ProvenanceClass.RULE_OUTPUT);
        }

        return resultFrom(allMatches, classified, unclassified);
    }

    // ------------------------------------------------------------------

    private DeploymentStrategyResult resultFrom(List<DeploymentStrategyMatch> matches, int classifiedFileCount,
                                                int unclassifiedFileCount) {
        DeploymentStrategyType winningStrategy = matches.stream()
                .max(Comparator.comparingInt(DeploymentStrategyMatch::priority)
                        // Priority bands never actually tie a ROLLING rule against a MIGRATE
                        // rule (see the rule set's documented invariant) — this is a defensive
                        // tie-break, not the mechanism this decision normally relies on.
                        .thenComparing(match -> match.strategy() == DeploymentStrategyType.MIGRATE ? 1 : 0))
                .map(DeploymentStrategyMatch::strategy)
                .orElse(DeploymentStrategyType.ROLLING);

        List<DeploymentStrategyMatch> winningMatches = matches.stream()
                .filter(match -> match.strategy() == winningStrategy)
                .toList();

        List<String> reasons = new ArrayList<>();
        Set<String> seenReasons = new LinkedHashSet<>();
        Map<String, List<String>> filesByArtifactType = new LinkedHashMap<>();
        for (DeploymentStrategyMatch match : winningMatches) {
            filesByArtifactType.computeIfAbsent(match.artifactType(), key -> new ArrayList<>())
                    .add(match.filePath());
        }
        for (DeploymentStrategyMatch match : winningMatches) {
            if (seenReasons.add(match.artifactType())) {
                List<String> files = filesByArtifactType.get(match.artifactType());
                String evidence = files.size() == 1
                        ? files.get(0)
                        : files.get(0) + " (+" + (files.size() - 1) + " more)";
                reasons.add(evidence + " — " + match.reason());
            }
        }
        reasons.addAll(winningStrategy == DeploymentStrategyType.MIGRATE ? MIGRATE_CONFIRMATIONS : ROLLING_CONFIRMATIONS);

        List<String> recommendedActions = new ArrayList<>();
        Set<String> seenActions = new LinkedHashSet<>();
        for (DeploymentStrategyMatch match : winningMatches) {
            for (String action : match.recommendedActions()) {
                if (seenActions.add(action)) {
                    recommendedActions.add(action);
                }
            }
        }

        ConfidenceLevel confidence = unclassifiedFileCount == 0 ? ConfidenceLevel.HIGH : ConfidenceLevel.MEDIUM;

        return new DeploymentStrategyResult(
                winningStrategy, confidence, reasons, recommendedActions, matches,
                classifiedFileCount, unclassifiedFileCount, knowledgeBase.version(), ProvenanceClass.RULE_OUTPUT);
    }

    private static DeploymentStrategyMatch toMatch(FileContext file, DeploymentStrategyRule rule) {
        String displayName = file.artifactDisplayName() != null ? file.artifactDisplayName() : file.artifactType();
        return new DeploymentStrategyMatch(
                file.filePath(), file.artifactType(), displayName,
                rule.strategy(), rule.priority(), rule.reason(), rule.recommendedActions());
    }
}
