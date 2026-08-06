package com.gyansys.intellirelease.api;

import com.gyansys.intellirelease.domain.integration.IntegrationCatalog;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.ApiDefinition;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.CountEntry;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.FlowDefinition;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.IntegrationHealthSnapshot;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.IntegrationOverview;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.InterfaceDefinition;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.InterfaceHealth;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.InterfaceView;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.MappingLinkView;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.MappingSetDefinition;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.MappingSetView;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.TopologyProfile;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.TopologyStageView;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.TopologySummaryView;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Integration Center's read API.
 *
 * <p>Health fields are reported as {@code null} with an
 * {@code unavailableReason} rather than as zeros. No monitoring source is wired
 * into this build, and a dashboard confidently showing "0% success rate" or
 * "100% healthy" for a landscape it cannot see is worse than one that says so —
 * the first gets believed.
 */
@RestController
@RequestMapping("/api/v1/integration")
@Tag(name = "Integration", description = "The integration landscape: topologies, interfaces and health")
public class IntegrationController {

    /** Shown wherever a live metric would otherwise be invented. */
    private static final String NO_TELEMETRY =
            "No integration monitoring source is connected, so runtime health cannot be reported. "
                    + "Structure, contracts and change impact below are derived deterministically and are accurate.";

    private final IntegrationCatalog catalog;

    public IntegrationController(IntegrationCatalog catalog) {
        this.catalog = catalog;
    }

    /* ------------------------------------------------------------ overview */

    @GetMapping("/overview")
    @Operation(
            summary = "The landscape at a glance",
            description = """
                    Returns one clickable stage chain per topology actually present, not a
                    canonical Commerce-to-middleware-to-ERP diagram. A landscape running CPI for
                    orders and a nightly CSV drop for prices gets two accurate diagrams instead
                    of one that is half wrong.
                    """)
    public IntegrationOverview overview() {
        List<InterfaceDefinition> interfaces = catalog.interfaces();

        List<TopologySummaryView> topologies = catalog.activeTopologies().stream()
                .map(profile -> toTopologyView(profile, interfaces))
                .toList();

        return new IntegrationOverview(
                topologies,
                interfaces.size(),
                (int) interfaces.stream().filter(InterfaceDefinition::isInbound).count(),
                (int) interfaces.stream().filter(InterfaceDefinition::isOutbound).count(),
                countStyle(interfaces, "SYNC"),
                countStyle(interfaces, "ASYNC"),
                countStyle(interfaces, "BATCH"),
                breakdown(interfaces, InterfaceDefinition::protocols),
                breakdown(interfaces, InterfaceDefinition::formats),
                breakdownOf(interfaces, InterfaceDefinition::domain),
                healthSnapshot(interfaces.size()));
    }

    @GetMapping("/health")
    @Operation(summary = "Landscape-wide integration health")
    public IntegrationHealthSnapshot health() {
        return healthSnapshot(catalog.interfaces().size());
    }

    /* ---------------------------------------------------------- interfaces */

    @GetMapping("/interfaces")
    @Operation(summary = "Search and filter the interface catalogue")
    public PageResponse<InterfaceView> interfaces(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String style,
            @RequestParam(required = false) String topology,
            @RequestParam(required = false) String protocol,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String health,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        String term = q == null ? "" : q.trim().toLowerCase();

        List<InterfaceView> matches = catalog.interfaces().stream()
                .filter(definition -> term.isEmpty()
                        || definition.searchableText().toLowerCase().contains(term))
                .filter(definition -> matchesDirection(definition, direction))
                .filter(definition -> style == null || style.equalsIgnoreCase(definition.style()))
                .filter(definition -> topology == null || topology.equalsIgnoreCase(definition.topology()))
                .filter(definition -> protocol == null || containsIgnoreCase(definition.protocols(), protocol))
                .filter(definition -> format == null || containsIgnoreCase(definition.formats(), format))
                .filter(definition -> domain == null || domain.equalsIgnoreCase(definition.domain()))
                // Every interface reports UNKNOWN health while no telemetry source
                // exists, so this filter is honoured rather than silently ignored.
                .filter(definition -> health == null || "UNKNOWN".equalsIgnoreCase(health))
                .sorted(Comparator.comparing(InterfaceDefinition::name))
                .map(this::toView)
                .toList();

        return PageResponse.slice(matches, page, size);
    }

    @GetMapping("/interfaces/{id}")
    @Operation(summary = "One interface in full")
    public ResponseEntity<InterfaceView> interfaceDetail(@PathVariable String id) {
        return catalog.findInterface(id)
                .map(this::toView)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /* ------------------------------------------------------------ mapping */

    private TopologySummaryView toTopologyView(TopologyProfile profile, List<InterfaceDefinition> interfaces) {
        List<InterfaceDefinition> using = interfaces.stream()
                .filter(definition -> profile.id().equals(definition.topology()))
                .toList();

        List<TopologyStageView> stages = profile.stageChain().stream()
                .map(stage -> new TopologyStageView(
                        stage.id(), stage.label(), stage.kind(), stage.detail(),
                        stage.protocols(),
                        // Interfaces through a stage that declares protocols are those
                        // speaking one of them; a stage with no protocols (a mapping
                        // step) is on the path of every interface in the topology.
                        stage.protocols().isEmpty()
                                ? using.size()
                                : (int) using.stream()
                                        .filter(definition -> definition.protocols().stream()
                                                .anyMatch(stage.protocols()::contains))
                                        .count(),
                        stage.tone()))
                .toList();

        return new TopologySummaryView(
                profile.id(), profile.label(), profile.description(), using.size(), stages, "UNKNOWN");
    }

    private InterfaceView toView(InterfaceDefinition definition) {
        return new InterfaceView(
                definition.id(), definition.name(), definition.description(),
                definition.direction(), definition.style(), definition.topology(),
                definition.protocols(), definition.formats(), definition.auth(),
                definition.businessObject(), definition.domain(),
                definition.sourceSystem(), definition.targetSystem(), definition.middleware(),
                definition.flowId(),
                InterfaceHealth.unknown(NO_TELEMETRY),
                definition.relatedArtifacts(), definition.tags(),
                // The catalogue is curated knowledge applied by rules, not a
                // measurement and not a model's opinion.
                ProvenanceClass.RULE_OUTPUT,
                false);
    }

    private IntegrationHealthSnapshot healthSnapshot(int totalInterfaces) {
        return new IntegrationHealthSnapshot(
                "UNKNOWN", null, null, null, 0, totalInterfaces,
                null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(),
                NO_TELEMETRY, ProvenanceClass.UNKNOWN);
    }

    /* ---------------------------------------------------------- internals */

    private static boolean matchesDirection(InterfaceDefinition definition, String direction) {
        if (direction == null) {
            return true;
        }
        // BIDIRECTIONAL interfaces legitimately answer to both filters.
        return switch (direction.toUpperCase()) {
            case "INBOUND" -> definition.isInbound();
            case "OUTBOUND" -> definition.isOutbound();
            default -> direction.equalsIgnoreCase(definition.direction());
        };
    }

    private static boolean containsIgnoreCase(List<String> values, String candidate) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(candidate));
    }

    private static int countStyle(List<InterfaceDefinition> interfaces, String style) {
        return (int) interfaces.stream().filter(definition -> style.equals(definition.style())).count();
    }

    /** Frequency of each value across a multi-valued attribute, most common first. */
    private static List<CountEntry> breakdown(
            List<InterfaceDefinition> interfaces,
            java.util.function.Function<InterfaceDefinition, List<String>> extractor) {

        Map<String, Long> counts = new LinkedHashMap<>();
        for (InterfaceDefinition definition : interfaces) {
            for (String value : extractor.apply(definition)) {
                counts.merge(value, 1L, Long::sum);
            }
        }
        return toEntries(counts);
    }

    /** Frequency of each value across a single-valued attribute. */
    private static List<CountEntry> breakdownOf(
            List<InterfaceDefinition> interfaces,
            java.util.function.Function<InterfaceDefinition, String> extractor) {

        Map<String, Long> counts = new LinkedHashMap<>();
        for (InterfaceDefinition definition : interfaces) {
            String value = extractor.apply(definition);
            if (value != null && !value.isBlank()) {
                counts.merge(value, 1L, Long::sum);
            }
        }
        return toEntries(counts);
    }

    private static List<CountEntry> toEntries(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new CountEntry(entry.getKey(), entry.getKey(), entry.getValue(), null))
                .toList();
    }

    /* ======================================================================
       Flows, mappings and the API catalogue live here too: they are views of
       the same catalogue, and splitting them across four controllers would
       mean four copies of the same dependency and the same paging code.
       ====================================================================== */

    @RestController
    @RequestMapping("/api/v1")
    @Tag(name = "Integration catalogue", description = "Flows, mappings and API contracts")
    public static class CatalogueController {

        private final IntegrationCatalog catalog;

        public CatalogueController(IntegrationCatalog catalog) {
            this.catalog = catalog;
        }

        /* ---------------------------------------------------------- flows */

        @GetMapping("/flows")
        @Operation(
                summary = "End-to-end flows",
                description = """
                        Each flow is an ordered, variable-length stage chain. A middleware-mediated
                        order export has eight stages; a nightly CSV price import has three. Neither
                        is padded to match the other.
                        """)
        public List<FlowDefinition> flows(@RequestParam(required = false) String q) {
            String term = q == null ? "" : q.trim().toLowerCase();
            if (term.isEmpty()) {
                return catalog.flows();
            }
            return catalog.flows().stream()
                    .filter(flow -> (flow.name() + " " + flow.summary() + " " + flow.businessObject()
                            + " " + String.join(" ", flow.tags())).toLowerCase().contains(term))
                    .toList();
        }

        @GetMapping("/flows/{id}")
        @Operation(summary = "One flow with every stage's purpose, errors, debug tips and logs")
        public ResponseEntity<FlowDefinition> flow(@PathVariable String id) {
            return catalog.findFlow(id)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }

        /* -------------------------------------------------------- mappings */

        @GetMapping("/mappings")
        @Operation(summary = "Mapping sets, with their issue counts")
        public PageResponse<MappingSetView> mappingSets(
                @RequestParam(required = false) String q,
                @RequestParam(required = false) String interfaceId,
                @RequestParam(required = false) String direction,
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "100") int size) {

            String term = q == null ? "" : q.trim().toLowerCase();

            List<MappingSetView> matches = catalog.mappingSets().stream()
                    .filter(set -> term.isEmpty()
                            || (set.name() + " " + set.businessObject()).toLowerCase().contains(term))
                    .filter(set -> interfaceId == null || interfaceId.equals(set.interfaceId()))
                    .filter(set -> direction == null || direction.equalsIgnoreCase(set.direction()))
                    .map(CatalogueController::toSetView)
                    .toList();

            return PageResponse.slice(matches, page, size);
        }

        @GetMapping("/mappings/{id}/links")
        @Operation(
                summary = "Every field journey in a mapping set",
                description = """
                        The node chain is returned as the set declares it — two nodes for a CSV
                        column map, five for a middleware chain — so the UI renders what is
                        actually there rather than a fixed set of columns.
                        """)
        public ResponseEntity<PageResponse<MappingLinkView>> links(
                @PathVariable String id,
                @RequestParam(required = false) String q,
                @RequestParam(required = false) String status,
                @RequestParam(required = false) Boolean issuesOnly,
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "500") int size) {

            MappingSetDefinition set = catalog.findMappingSet(id).orElse(null);
            if (set == null) {
                return ResponseEntity.notFound().build();
            }

            String term = q == null ? "" : q.trim().toLowerCase();

            List<MappingLinkView> matches = set.links().stream()
                    .filter(link -> term.isEmpty() || link.nodes().stream()
                            .anyMatch(node -> node.field() != null
                                    && node.field().toLowerCase().contains(term)))
                    .filter(link -> status == null || status.equalsIgnoreCase(link.status()))
                    .filter(link -> !Boolean.TRUE.equals(issuesOnly) || link.isIssue())
                    .map(link -> new MappingLinkView(
                            link.id(), set.id(), link.nodes(), link.status(), link.required(),
                            link.transformation(), link.notes(), link.confidence(),
                            ProvenanceClass.RULE_OUTPUT))
                    .toList();

            return ResponseEntity.ok(PageResponse.slice(matches, page, size));
        }

        /* ------------------------------------------------------------ APIs */

        @GetMapping("/api-catalog")
        @Operation(summary = "REST, SOAP, OData and OCC contracts")
        public PageResponse<IntegrationModelApiView> apis(
                @RequestParam(required = false) String q,
                @RequestParam(required = false) String kind,
                @RequestParam(required = false) String system,
                @RequestParam(required = false) Boolean changedOnly,
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "200") int size) {

            String term = q == null ? "" : q.trim().toLowerCase();

            List<IntegrationModelApiView> matches = catalog.apis().stream()
                    .filter(api -> term.isEmpty()
                            || (api.name() + " " + api.description() + " " + api.basePath())
                            .toLowerCase().contains(term))
                    .filter(api -> kind == null || kind.equalsIgnoreCase(api.kind()))
                    .filter(api -> system == null || system.equalsIgnoreCase(api.system()))
                    // "Changed in this window" needs a change window to compare
                    // against. Until the caller supplies one, nothing is marked
                    // changed and the filter honestly returns nothing.
                    .filter(api -> !Boolean.TRUE.equals(changedOnly))
                    .map(CatalogueController::toApiView)
                    .toList();

            return PageResponse.slice(matches, page, size);
        }

        @GetMapping("/api-catalog/{id}")
        @Operation(summary = "One API contract with its operations")
        public ResponseEntity<IntegrationModelApiView> api(@PathVariable String id) {
            return catalog.findApi(id)
                    .map(CatalogueController::toApiView)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }

        /* ------------------------------------------------------ internals */

        private static MappingSetView toSetView(MappingSetDefinition set) {
            return new MappingSetView(
                    set.id(), set.name(), set.interfaceId(), set.businessObject(), set.direction(),
                    set.layers(), set.links().size(),
                    (int) set.links().stream().filter(link -> link.isIssue()).count(),
                    set.version(), null);
        }

        private static IntegrationModelApiView toApiView(ApiDefinition api) {
            List<IntegrationModelApiOperationView> operations = api.operations().stream()
                    .map(operation -> new IntegrationModelApiOperationView(
                            operation.id(), operation.name(), operation.method(), operation.path(),
                            operation.summary(), operation.requestFormat(), operation.responseFormat(),
                            operation.deprecated(), false, operation.samplePayloadId()))
                    .toList();

            return new IntegrationModelApiView(
                    api.id(), api.name(), api.kind(), api.description(), api.basePath(), api.version(),
                    api.system(), api.specFormat(), api.specUrl(), api.auth(), operations, api.tags(),
                    false, ProvenanceClass.RULE_OUTPUT);
        }
    }

    /** Wire shape for an API. Mirrors the frontend's {@code ApiDefinition}. */
    public record IntegrationModelApiView(
            String id, String name, String kind, String description, String basePath, String version,
            String system, String specFormat, String specUrl, String auth,
            List<IntegrationModelApiOperationView> operations, List<String> tags,
            boolean changedInWindow, ProvenanceClass provenance
    ) {
    }

    public record IntegrationModelApiOperationView(
            String id, String name, String method, String path, String summary,
            String requestFormat, String responseFormat, boolean deprecated,
            boolean changed, String samplePayloadId
    ) {
    }
}
