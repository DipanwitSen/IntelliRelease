package com.gyansys.intellirelease.domain.impact;

import com.gyansys.intellirelease.domain.context.ContextResult;
import com.gyansys.intellirelease.model.enums.ConfidenceLevel;
import com.gyansys.intellirelease.model.enums.ImpactType;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import com.gyansys.intellirelease.model.enums.SapCapability;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Answers "what parts of the platform may be affected?" — with the emphasis on
 * <em>may</em>, kept visible.
 *
 * <p>Confirmed impact comes from files that actually changed. Potential impact
 * comes from one hop across {@link CapabilityGraph}. The engine deliberately
 * does <strong>not</strong> traverse transitively: two hops from a checkout
 * change reaches most of the platform, which is true, useless, and the fastest
 * way to train a release manager to ignore the report.
 */
@Service
public class ImpactAnalyzer {

    private final CapabilityGraph graph;

    public ImpactAnalyzer(CapabilityGraph graph) {
        this.graph = graph;
    }

    public ImpactResult analyze(ContextResult context) {
        if (context == null || context.capabilities().isEmpty()) {
            return ImpactResult.empty();
        }

        List<ImpactItem> confirmed = buildConfirmed(context);
        Set<SapCapability> confirmedCapabilities = context.capabilities();
        List<ImpactItem> potential = buildPotential(context, confirmedCapabilities);

        return new ImpactResult(confirmed, potential, ProvenanceClass.RULE_OUTPUT);
    }

    /**
     * Directly changed capabilities. Evidence is the changed file itself, which
     * is why these are HIGH confidence without qualification.
     */
    private List<ImpactItem> buildConfirmed(ContextResult context) {
        List<ImpactItem> confirmed = new ArrayList<>();

        for (SapCapability capability : context.capabilities()) {
            // A test file is evidence about the change, not a capability the
            // change affects. It informs Risk, not Impact.
            if (capability == SapCapability.TEST) {
                continue;
            }
            List<String> sources = context.pathsFor(capability);
            confirmed.add(new ImpactItem(
                    capability,
                    ImpactType.CONFIRMED,
                    ConfidenceLevel.HIGH,
                    "Directly changed: " + describeSources(sources),
                    sources
            ));
        }

        confirmed.sort(Comparator.comparing(item -> item.capability().name()));
        return confirmed;
    }

    /**
     * One hop out. When several confirmed capabilities point at the same
     * neighbour the reasons are merged rather than duplicated, and the strongest
     * confidence wins — a capability reachable by two independent routes is not
     * less likely to be affected.
     */
    private List<ImpactItem> buildPotential(ContextResult context, Set<SapCapability> confirmed) {
        Map<SapCapability, List<String>> reasons = new LinkedHashMap<>();
        Map<SapCapability, ConfidenceLevel> confidences = new LinkedHashMap<>();
        Map<SapCapability, List<String>> sources = new LinkedHashMap<>();

        for (SapCapability origin : confirmed) {
            if (origin == SapCapability.TEST) {
                continue;
            }
            List<String> originFiles = context.pathsFor(origin);

            for (CapabilityGraph.Edge edge : graph.neighboursOf(origin)) {
                // Already confirmed by a direct file change — never restate a
                // fact as a possibility.
                if (confirmed.contains(edge.target())) {
                    continue;
                }
                reasons.computeIfAbsent(edge.target(), key -> new ArrayList<>())
                        .add(origin.getDisplayName() + " changed: " + edge.reason());
                confidences.merge(edge.target(), edge.confidence(), ImpactAnalyzer::strongest);
                sources.computeIfAbsent(edge.target(), key -> new ArrayList<>())
                        .addAll(originFiles);
            }
        }

        List<ImpactItem> potential = new ArrayList<>(reasons.size());
        reasons.forEach((capability, why) -> potential.add(new ImpactItem(
                capability,
                ImpactType.POTENTIAL,
                confidences.getOrDefault(capability, ConfidenceLevel.LOW),
                String.join("; ", why) + " — verify before relying on this",
                sources.getOrDefault(capability, List.of()).stream().distinct().toList()
        )));

        potential.sort(Comparator
                .comparing((ImpactItem item) -> item.confidence().ordinal())
                .thenComparing(item -> item.capability().name()));
        return potential;
    }

    private static ConfidenceLevel strongest(ConfidenceLevel left, ConfidenceLevel right) {
        return left.ordinal() <= right.ordinal() ? left : right;
    }

    private static String describeSources(List<String> sources) {
        if (sources.isEmpty()) {
            return "no file recorded";
        }
        if (sources.size() <= 3) {
            return String.join(", ", sources);
        }
        return String.join(", ", sources.subList(0, 3)) + " and " + (sources.size() - 3) + " more";
    }
}
