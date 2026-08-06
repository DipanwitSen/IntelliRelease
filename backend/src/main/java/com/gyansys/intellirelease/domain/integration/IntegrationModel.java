package com.gyansys.intellirelease.domain.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;

import java.util.List;
import java.util.Map;

/**
 * The integration domain, as data.
 *
 * <p><strong>Why none of this is an enum.</strong> The obvious modelling here
 * is {@code enum Topology { CPI, DIRECT }} and a fixed
 * {@code Commerce → CPI → Mapping → S4} pipeline. That models SAP's reference
 * architecture, not a customer's landscape. Real ones include:
 *
 * <ul>
 *   <li>Commerce talking straight to S/4 with no middleware at all</li>
 *   <li>A nightly CSV drop on SFTP consumed by any ERP</li>
 *   <li>MuleSoft, Boomi or webMethods where CPI would be</li>
 *   <li>An event broker fanning out to several consumers</li>
 * </ul>
 *
 * <p>So a flow is an <em>ordered list of stages</em> of unbounded length, every
 * stage may be optional, and topology/protocol/format are strings validated
 * against a catalogue rather than compiled into the type system. A customer can
 * add a protocol by editing {@code integration_catalog.json}; they cannot add
 * an enum constant without a release of this application.
 *
 * <p>All records are {@code @JsonIgnoreProperties(ignoreUnknown = true)} so a
 * catalogue authored against a newer schema still loads on an older binary —
 * the fields this version understands bind, the rest are skipped, and nobody
 * has to coordinate a data change with a deployment.
 */
public final class IntegrationModel {

    private IntegrationModel() {
    }

    /* ===================================================================
       Vocabulary — the closed-ish sets, kept as constants rather than enums
       so the catalogue can extend them and the code can still name them.
       =================================================================== */

    public static final String DIRECTION_INBOUND = "INBOUND";
    public static final String DIRECTION_OUTBOUND = "OUTBOUND";
    public static final String DIRECTION_BIDIRECTIONAL = "BIDIRECTIONAL";

    public static final String STYLE_SYNC = "SYNC";
    public static final String STYLE_ASYNC = "ASYNC";
    public static final String STYLE_BATCH = "BATCH";
    public static final String STYLE_STREAMING = "STREAMING";

    public static final String TOPOLOGY_DIRECT = "DIRECT";
    public static final String TOPOLOGY_MIDDLEWARE = "MIDDLEWARE";
    public static final String TOPOLOGY_FILE_BASED = "FILE_BASED";
    public static final String TOPOLOGY_EVENT_STREAM = "EVENT_STREAM";
    public static final String TOPOLOGY_HYBRID = "HYBRID";

    /* ===================================================================
       Catalogue root
       =================================================================== */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Catalogue(
            Meta meta,
            List<TopologyProfile> topologies,
            List<InterfaceDefinition> interfaces,
            List<FlowDefinition> flows,
            List<MappingSetDefinition> mappingSets,
            List<ApiDefinition> apis,
            List<SamplePayload> samplePayloads,
            /** Path glob -> interface ids, so a changed file can name what it touches. */
            List<DiscoveryRule> discoveryRules
    ) {
        public Catalogue {
            topologies = nullSafe(topologies);
            interfaces = nullSafe(interfaces);
            flows = nullSafe(flows);
            mappingSets = nullSafe(mappingSets);
            apis = nullSafe(apis);
            samplePayloads = nullSafe(samplePayloads);
            discoveryRules = nullSafe(discoveryRules);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(String name, String version, String purpose, String generalizationPolicy) {
    }

    /* ===================================================================
       Topology
       =================================================================== */

    /**
     * One shape a landscape can take, with the stage chain it implies.
     *
     * @param stageChain ordered stages. A {@code FILE_BASED} profile has three;
     *                   a middleware-mediated one has seven. Neither is padded
     *                   to match the other.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopologyProfile(
            String id,
            String label,
            String description,
            List<TopologyStage> stageChain
    ) {
        public TopologyProfile {
            stageChain = nullSafe(stageChain);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopologyStage(
            String id,
            String label,
            String kind,
            String detail,
            List<String> protocols,
            String tone
    ) {
        public TopologyStage {
            protocols = nullSafe(protocols);
            tone = tone == null || tone.isBlank() ? "neutral" : tone;
        }
    }

    /* ===================================================================
       Interfaces
       =================================================================== */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InterfaceDefinition(
            String id,
            String name,
            String description,
            String direction,
            String style,
            String topology,
            List<String> protocols,
            List<String> formats,
            String auth,
            String businessObject,
            String domain,
            String sourceSystem,
            String targetSystem,
            /** Null when the topology has no middleware hop. Never a placeholder. */
            String middleware,
            String flowId,
            List<RelatedArtifact> relatedArtifacts,
            List<String> tags
    ) {
        public InterfaceDefinition {
            protocols = nullSafe(protocols);
            formats = nullSafe(formats);
            relatedArtifacts = nullSafe(relatedArtifacts);
            tags = nullSafe(tags);
        }

        public boolean isInbound() {
            return DIRECTION_INBOUND.equals(direction) || DIRECTION_BIDIRECTIONAL.equals(direction);
        }

        public boolean isOutbound() {
            return DIRECTION_OUTBOUND.equals(direction) || DIRECTION_BIDIRECTIONAL.equals(direction);
        }

        /** Everything a free-text search should look at, lowercased once by the caller. */
        public String searchableText() {
            return String.join(" ", name, description, businessObject, domain,
                    sourceSystem, targetSystem, middleware == null ? "" : middleware,
                    String.join(" ", protocols), String.join(" ", formats), String.join(" ", tags));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RelatedArtifact(String kind, String name, String path, String system) {
    }

    /* ===================================================================
       Flows
       =================================================================== */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FlowDefinition(
            String id,
            String name,
            String summary,
            String direction,
            String style,
            String topology,
            String businessObject,
            List<FlowStage> stages,
            List<String> interfaceIds,
            List<FlowBranch> branches,
            List<String> tags
    ) {
        public FlowDefinition {
            stages = nullSafe(stages);
            interfaceIds = nullSafe(interfaceIds);
            branches = nullSafe(branches);
            tags = nullSafe(tags);
        }
    }

    /**
     * One hop.
     *
     * <p>Everything past identity is nullable because a CSV drop genuinely has
     * no converters and no DTO. Emitting {@code "converters": "N/A"} to keep a
     * uniform shape would be the rigidity this model exists to avoid.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FlowStage(
            String id,
            String name,
            String kind,
            String owner,
            String purpose,
            String input,
            String output,
            String format,
            String protocol,
            List<StageError> possibleErrors,
            List<String> debugTips,
            List<String> logLocations,
            List<RelatedArtifact> relatedClasses,
            boolean optional
    ) {
        public FlowStage {
            possibleErrors = nullSafe(possibleErrors);
            debugTips = nullSafe(debugTips);
            logLocations = nullSafe(logLocations);
            relatedClasses = nullSafe(relatedClasses);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StageError(String code, String title, String severity, String errorPatternId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FlowBranch(String id, String label, String fromStageId, String toStageId,
                             String condition, String kind) {
    }

    /* ===================================================================
       Mappings
       =================================================================== */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MappingSetDefinition(
            String id,
            String name,
            String interfaceId,
            String businessObject,
            String direction,
            /** Ordered layer names. Two for a CSV column map, five for a CPI chain. */
            List<String> layers,
            String version,
            List<MappingLinkDefinition> links
    ) {
        public MappingSetDefinition {
            layers = nullSafe(layers);
            links = nullSafe(links);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MappingLinkDefinition(
            String id,
            List<MappingNode> nodes,
            String status,
            boolean required,
            String transformation,
            String notes,
            String confidence
    ) {
        public MappingLinkDefinition {
            nodes = nullSafe(nodes);
            status = status == null || status.isBlank() ? "MAPPED" : status;
            confidence = confidence == null || confidence.isBlank() ? "MEDIUM" : confidence;
        }

        /** A link is an issue when it is anything other than cleanly mapped. */
        public boolean isIssue() {
            return !"MAPPED".equals(status);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MappingNode(String layer, String field, String type, String system, String artifact) {
    }

    /* ===================================================================
       APIs
       =================================================================== */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiDefinition(
            String id,
            String name,
            String kind,
            String description,
            String basePath,
            String version,
            String system,
            String specFormat,
            String specUrl,
            String auth,
            List<ApiOperation> operations,
            List<String> tags,
            /** Path globs that, when changed, mean this API's contract moved. */
            List<String> contractPaths
    ) {
        public ApiDefinition {
            operations = nullSafe(operations);
            tags = nullSafe(tags);
            contractPaths = nullSafe(contractPaths);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiOperation(
            String id,
            String name,
            String method,
            String path,
            String summary,
            String requestFormat,
            String responseFormat,
            boolean deprecated,
            String samplePayloadId
    ) {
    }

    /* ===================================================================
       Sample payloads
       =================================================================== */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SamplePayload(
            String id,
            String name,
            String format,
            String interfaceId,
            String businessObject,
            String direction,
            String description,
            List<String> tags,
            /** Inline content. Catalogue payloads are illustrative, not captured traffic. */
            String content
    ) {
        public SamplePayload {
            tags = nullSafe(tags);
            content = content == null ? "" : content;
        }
    }

    /* ===================================================================
       Discovery
       =================================================================== */

    /**
     * Links a changed-file pattern to the interfaces it affects.
     *
     * <p>This is what turns "someone edited OrderExportConverter.java" into
     * "the outbound order interface is impacted" without asking a model. The
     * rule carries its own reason string so the UI can show <em>why</em> the
     * link was drawn, not just that it was.
     *
     * @param severity how bad a change here is for the named interfaces
     * @param evidence human-readable justification, surfaced verbatim
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiscoveryRule(
            String id,
            String pattern,
            int priority,
            List<String> interfaceIds,
            List<String> apiIds,
            List<String> mappingSetIds,
            String severity,
            String confidence,
            String evidence,
            /** Categorises what kind of integration artifact this is. */
            String artifactKind
    ) {
        public DiscoveryRule {
            interfaceIds = nullSafe(interfaceIds);
            apiIds = nullSafe(apiIds);
            mappingSetIds = nullSafe(mappingSetIds);
            severity = severity == null || severity.isBlank() ? "MEDIUM" : severity;
            confidence = confidence == null || confidence.isBlank() ? "MEDIUM" : confidence;
        }
    }

    /* ===================================================================
       Runtime views — what the API returns, assembled from the catalogue
       plus whatever live signals exist.
       =================================================================== */

    /**
     * Health for one interface.
     *
     * <p>{@code unavailableReason} is the important field. No monitoring source
     * is wired into this build, so reporting a 0% success rate would be a
     * fabrication. The UI renders the reason instead of a number, which is the
     * honest answer and also tells the reader what to connect.
     */
    public record InterfaceHealth(
            String status,
            Double successRate,
            Integer avgResponseMs,
            Integer p95ResponseMs,
            Integer volume24h,
            Integer failures24h,
            Integer retries24h,
            Integer queueDepth,
            String lastSuccessAt,
            String lastFailureAt,
            String unavailableReason
    ) {
        public static InterfaceHealth unknown(String reason) {
            return new InterfaceHealth("UNKNOWN", null, null, null, null, null, null, null, null, null, reason);
        }
    }

    /** An interface plus the runtime facts the platform can honestly attach. */
    public record InterfaceView(
            String id, String name, String description, String direction, String style,
            String topology, List<String> protocols, List<String> formats, String auth,
            String businessObject, String domain, String sourceSystem, String targetSystem,
            String middleware, String flowId, InterfaceHealth health,
            List<RelatedArtifact> relatedArtifacts, List<String> tags,
            ProvenanceClass provenance, boolean discovered
    ) {
    }

    public record MappingSetView(
            String id, String name, String interfaceId, String businessObject, String direction,
            List<String> layers, int linkCount, int issueCount, String version, String updatedAt
    ) {
    }

    public record MappingLinkView(
            String id, String mappingSetId, List<MappingNode> nodes, String status,
            boolean required, String transformation, String notes, String confidence,
            ProvenanceClass provenance
    ) {
    }

    public record ApiView(
            String id, String name, String kind, String description, String basePath, String version,
            String system, String specFormat, String specUrl, String auth,
            List<ApiOperationView> operations, List<String> tags,
            boolean changedInWindow, ProvenanceClass provenance
    ) {
    }

    public record ApiOperationView(
            String id, String name, String method, String path, String summary,
            String requestFormat, String responseFormat, boolean deprecated,
            boolean changed, String samplePayloadId
    ) {
    }

    public record TopologyStageView(
            String id, String label, String kind, String detail,
            List<String> protocols, int interfaceCount, String tone
    ) {
    }

    public record TopologySummaryView(
            String topology, String label, String description, int interfaceCount,
            List<TopologyStageView> stageChain, String health
    ) {
    }

    public record CountEntry(String key, String label, long count, String tone) {
        public static CountEntry of(String key, long count) {
            return new CountEntry(key, key, count, null);
        }
    }

    public record IntegrationHealthSnapshot(
            String status, Double successRate, Integer avgResponseMs, Integer p95ResponseMs,
            int failedInterfaces, int totalInterfaces, Integer retryCount, Integer queueBacklog,
            Integer avgProcessingMs, Integer dailyVolume, Integer peakVolume, String peakAt,
            List<CountEntry> topFailures, List<CountEntry> topInterfaces,
            List<Map<String, Object>> volumeTrend, List<Map<String, Object>> successTrend,
            String unavailableReason, ProvenanceClass provenance
    ) {
    }

    public record IntegrationOverview(
            List<TopologySummaryView> topologies,
            int totalInterfaces, int inboundCount, int outboundCount,
            int syncCount, int asyncCount, int batchCount,
            List<CountEntry> protocolBreakdown, List<CountEntry> formatBreakdown,
            List<CountEntry> domainBreakdown, IntegrationHealthSnapshot health
    ) {
    }

    /* ===================================================================
       Change impact
       =================================================================== */

    /**
     * What a set of changed files means for integrations.
     *
     * <p>{@code touched == false} with every list empty is a legitimate and
     * common answer — a storefront CSS change reaches no interface — and the
     * UI says exactly that rather than manufacturing impact to fill a panel.
     */
    public record IntegrationContext(
            boolean touched,
            List<String> directions,
            List<ImpactedInterface> impactedInterfaces,
            List<String> impactedPayloads,
            List<String> impactedMappings,
            List<String> impactedDtos,
            List<String> impactedCommerceModels,
            List<String> impactedTargetObjects,
            List<String> impactedMiddlewareFlows,
            List<String> detectedTopologies,
            List<String> evidence,
            ProvenanceClass provenance
    ) {
        public static IntegrationContext untouched() {
            return new IntegrationContext(false, List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), ProvenanceClass.DERIVED_FACT);
        }
    }

    public record ImpactedInterface(
            String interfaceId, String name, String direction, String reason,
            String severity, String confidence
    ) {
    }

    private static <T> List<T> nullSafe(List<T> value) {
        return value == null ? List.of() : List.copyOf(value);
    }
}
