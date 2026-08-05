package com.gyansys.intellirelease.domain.context;

import com.gyansys.intellirelease.domain.context.knowledge.SapCommerceKnowledgeBase;
import com.gyansys.intellirelease.model.enums.ConfidenceLevel;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import com.gyansys.intellirelease.model.enums.SapCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Understands what each changed file means inside SAP Commerce —
 * deterministically, by applying {@link CapabilityLibrary}, never by asking a
 * model.
 *
 * <p>This engine is the heart of the platform. Impact, Regression, Risk, Drift
 * and Readiness all consume its output, so its discipline sets the ceiling for
 * everything downstream.
 *
 * <p><strong>Explicit non-goal: the engine never guesses.</strong> A path that
 * matches no curated rule is tagged {@link SapCapability#UNCLASSIFIED} with
 * provenance {@link ProvenanceClass#UNKNOWN}, and downstream engines treat it
 * conservatively rather than optimistically. Saying "I don't know" is a
 * feature here, not a gap.
 */
@Service
public class SAPCommerceContextEngine {

    private static final Logger log = LoggerFactory.getLogger(SAPCommerceContextEngine.class);

    private final CapabilityLibrary library;

    public SAPCommerceContextEngine(CapabilityLibrary library) {
        this.library = library;
    }

    /**
     * Classifies every changed file in a pull request.
     *
     * @param changedFiles the file manifest reported by the Git provider (FACT)
     * @return the pull request's SAP Commerce meaning (DERIVED_FACT)
     */
    public ContextResult analyze(List<ChangedFile> changedFiles) {
        List<ChangedFile> input = changedFiles == null ? List.of() : changedFiles;

        List<FileContext> classified = new ArrayList<>(input.size());
        Set<SapCapability> capabilities = new LinkedHashSet<>();
        int unclassified = 0;
        boolean testsIncluded = false;

        for (ChangedFile file : input) {
            FileContext context = classify(file.path());
            classified.add(context);

            if (context.isClassified()) {
                capabilities.addAll(context.allCapabilities());
                if (context.sapCapability() == SapCapability.TEST) {
                    testsIncluded = true;
                }
            } else {
                unclassified++;
            }
        }

        // UNCLASSIFIED is never a capability — it is the absence of one. Keeping
        // it out of the set stops downstream engines treating "unknown" as a
        // thing they can reason about.
        capabilities.remove(SapCapability.UNCLASSIFIED);

        if (unclassified > 0) {
            log.debug("Context engine left {} of {} files unclassified — downstream engines will treat them conservatively",
                    unclassified, input.size());
        }

        return new ContextResult(
                classified,
                capabilities,
                input.size(),
                unclassified,
                testsIncluded,
                library.libraryVersion(),
                ProvenanceClass.DERIVED_FACT
        );
    }

    /**
     * Classifies a single path. Public so the rule set can be exercised
     * directly by tests and by the demo's "why did you say that?" drilldown.
     */
    public FileContext classify(String path) {
        PathFacts facts = PathFacts.of(path);
        Optional<SapCommerceKnowledgeBase.Match> match = library.classify(facts);

        if (match.isEmpty()) {
            return FileContext.unclassified(facts.path());
        }

        SapCommerceKnowledgeBase.Match knowledgeMatch = match.get();
        var definition = knowledgeMatch.definition();
        SapCapability capability = library.legacyCapability(knowledgeMatch.artifactType());
        SapCapability businessDomain = resolveBusinessDomain(facts, capability);
        String displayName = definition == null ? knowledgeMatch.artifactType() : definition.displayName();
        String evidence = definition == null
                ? "Matched knowledge base pattern " + knowledgeMatch.matchedPattern()
                : displayName + " — " + definition.generalizedMeaning();

        return new FileContext(
                facts.path(),
                capability,
                businessDomain,
                ProvenanceClass.DERIVED_FACT,
                definition == null ? List.of() : definition.potentialImpact(),
                confidenceFor(knowledgeMatch.priority()),
                evidence,
                knowledgeMatch.artifactType(),
                knowledgeMatch.artifactType(),
                displayName,
                definition == null ? null : definition.generalRole(),
                definition == null ? List.of() : definition.businessCapability(),
                definition == null ? null : definition.deploymentRisk(),
                definition == null ? List.of() : definition.potentialImpact(),
                definition == null ? List.of() : definition.regressionAreas()
        );
    }

    /**
     * The knowledge base ranks patterns by specificity (priority), not
     * confidence — this derives one from the other rather than adding a
     * second, redundant dimension to every rule in the data file.
     */
    private static ConfidenceLevel confidenceFor(int priority) {
        if (priority >= 90) {
            return ConfidenceLevel.HIGH;
        }
        return priority >= 70 ? ConfidenceLevel.MEDIUM : ConfidenceLevel.LOW;
    }

    /**
     * A file's architectural layer and its business capability are different
     * questions. {@code OrderHistoryService.java} is business logic to an
     * architect and the Order capability to QA; both get recorded.
     *
     * <p>Suppressed when the matched rule already <em>is</em> a business
     * capability, so we never restate the same fact twice.
     */
    private SapCapability resolveBusinessDomain(PathFacts facts, SapCapability matched) {
        if (matched.isBusinessCapability()) {
            return null;
        }
        SapCapability detected = library.detectBusinessDomain(facts);
        return detected == matched ? null : detected;
    }
}
