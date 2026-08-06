package com.gyansys.intellirelease.domain.integration;

import com.gyansys.intellirelease.domain.integration.IntegrationModel.DiscoveryRule;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.ImpactedInterface;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.IntegrationContext;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.InterfaceDefinition;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.MappingNode;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.MappingSetDefinition;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Answers "what does this change mean for our integrations?" deterministically.
 *
 * <p>This is the integration counterpart of {@code SAPCommerceContextEngine},
 * and it holds the same line: every impacted interface traces back to one named
 * discovery rule matching one changed path, with the rule's own evidence string
 * carried through to the UI. Nothing here is inferred by a model, and nothing
 * is guessed.
 *
 * <p><strong>An empty result is a real answer.</strong> A storefront CSS change
 * touches no interface, and this returns {@code touched = false} rather than
 * reaching for the nearest plausible interface to fill a panel. Over-reporting
 * impact is worse than under-reporting it: it trains release managers to ignore
 * the integration section entirely.
 */
@Service
public class IntegrationContextExtractor {

    private final IntegrationCatalog catalog;

    public IntegrationContextExtractor(IntegrationCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * @param changedPaths file paths from the Git provider (FACT)
     * @return what those paths mean for integrations (DERIVED_FACT)
     */
    public IntegrationContext extract(List<String> changedPaths) {
        if (changedPaths == null || changedPaths.isEmpty()) {
            return IntegrationContext.untouched();
        }

        // Keyed by interface id so the strongest reason for each interface wins
        // rather than the last one encountered.
        Map<String, ImpactedInterface> impacted = new LinkedHashMap<>();
        Set<String> mappingSetIds = new LinkedHashSet<>();
        Set<String> evidence = new LinkedHashSet<>();
        Set<String> artifactKinds = new LinkedHashSet<>();

        for (String path : changedPaths) {
            for (DiscoveryRule rule : catalog.rulesFor(path)) {
                if (rule.artifactKind() != null) {
                    artifactKinds.add(rule.artifactKind());
                }
                evidence.add(path + " — " + rule.evidence());
                mappingSetIds.addAll(rule.mappingSetIds());

                for (String interfaceId : rule.interfaceIds()) {
                    catalog.findInterface(interfaceId).ifPresent(definition ->
                            impacted.merge(
                                    interfaceId,
                                    toImpacted(definition, rule, path),
                                    IntegrationContextExtractor::strongerOf));
                }
            }
        }

        List<String> touchedApis = catalog.apisTouchedBy(changedPaths);

        if (impacted.isEmpty() && mappingSetIds.isEmpty() && touchedApis.isEmpty()) {
            return IntegrationContext.untouched();
        }

        List<ImpactedInterface> interfaces = impacted.values().stream()
                .sorted(Comparator.comparingInt(item -> -severityRank(item.severity())))
                .toList();

        List<InterfaceDefinition> definitions = interfaces.stream()
                .map(item -> catalog.findInterface(item.interfaceId()).orElseThrow())
                .toList();

        return new IntegrationContext(
                true,
                distinct(definitions.stream().map(InterfaceDefinition::direction).toList()),
                interfaces,
                impactedPayloads(definitions),
                List.copyOf(mappingSetIds),
                impactedDtos(mappingSetIds),
                impactedCommerceModels(mappingSetIds),
                impactedTargetObjects(mappingSetIds),
                impactedMiddlewareFlows(definitions),
                distinct(definitions.stream().map(InterfaceDefinition::topology).toList()),
                List.copyOf(evidence),
                ProvenanceClass.DERIVED_FACT);
    }

    /* ------------------------------------------------------------ helpers */

    private ImpactedInterface toImpacted(InterfaceDefinition definition, DiscoveryRule rule, String path) {
        return new ImpactedInterface(
                definition.id(),
                definition.name(),
                definition.direction(),
                rule.evidence() + " (matched " + path + ")",
                rule.severity(),
                rule.confidence());
    }

    /** Keeps whichever of two findings for the same interface is more severe. */
    private static ImpactedInterface strongerOf(ImpactedInterface existing, ImpactedInterface candidate) {
        return severityRank(candidate.severity()) > severityRank(existing.severity()) ? candidate : existing;
    }

    private static int severityRank(String severity) {
        return switch (severity == null ? "" : severity) {
            case "CRITICAL" -> 5;
            case "HIGH" -> 4;
            case "MEDIUM" -> 3;
            case "LOW" -> 2;
            case "INFO" -> 1;
            default -> 0;
        };
    }

    /** Sample payloads belonging to the impacted interfaces. */
    private List<String> impactedPayloads(List<InterfaceDefinition> definitions) {
        Set<String> interfaceIds = definitions.stream()
                .map(InterfaceDefinition::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return catalog.samplePayloads().stream()
                .filter(payload -> payload.interfaceId() != null && interfaceIds.contains(payload.interfaceId()))
                .map(IntegrationModel.SamplePayload::name)
                .distinct()
                .toList();
    }

    /**
     * Field names from the DTO layer of every impacted mapping set.
     *
     * <p>Layer names are matched case-insensitively on a substring rather than
     * against a fixed enum, because the layer vocabulary is customer data — one
     * landscape calls it "DTO", another "Canonical", a third "Integration
     * object". Hard-coding the names would silently return nothing for the
     * second and third.
     */
    private List<String> impactedDtos(Set<String> mappingSetIds) {
        return fieldsInLayerMatching(mappingSetIds, "dto", "canonical", "integration object");
    }

    private List<String> impactedCommerceModels(Set<String> mappingSetIds) {
        return fieldsInLayerMatching(mappingSetIds, "commerce");
    }

    private List<String> impactedTargetObjects(Set<String> mappingSetIds) {
        return fieldsInLayerMatching(mappingSetIds, "target", "sap field");
    }

    private List<String> fieldsInLayerMatching(Set<String> mappingSetIds, String... layerHints) {
        List<String> fields = new ArrayList<>();
        for (String setId : mappingSetIds) {
            MappingSetDefinition set = catalog.findMappingSet(setId).orElse(null);
            if (set == null) {
                continue;
            }
            for (var link : set.links()) {
                for (MappingNode node : link.nodes()) {
                    String layer = node.layer() == null ? "" : node.layer().toLowerCase();
                    for (String hint : layerHints) {
                        if (layer.contains(hint) && node.field() != null && !node.field().startsWith("—")) {
                            fields.add(node.field());
                            break;
                        }
                    }
                }
            }
        }
        return fields.stream().distinct().sorted().toList();
    }

    /** Flow names for impacted interfaces that actually run through middleware. */
    private List<String> impactedMiddlewareFlows(List<InterfaceDefinition> definitions) {
        return definitions.stream()
                .filter(definition -> definition.middleware() != null && !definition.middleware().isBlank())
                .map(InterfaceDefinition::flowId)
                .filter(flowId -> flowId != null && !flowId.isBlank())
                .distinct()
                .map(flowId -> catalog.findFlow(flowId)
                        .map(IntegrationModel.FlowDefinition::name)
                        .orElse(flowId))
                .toList();
    }

    private static List<String> distinct(List<String> values) {
        return values.stream().filter(value -> value != null && !value.isBlank()).distinct().sorted().toList();
    }
}
