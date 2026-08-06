import { ConfidenceLevel, CountEntry, HealthStatus, ProvenanceClass, Severity, Tone, TrendPoint } from './common';

/* =========================================================================
   TOPOLOGY — why this model is deliberately not "Commerce → CPI → S/4"
   -------------------------------------------------------------------------
   That chain is one topology, and it is common, but it is not universal. Real
   landscapes also look like:

     • Commerce → S/4 directly (no middleware at all)
     • Commerce → SFTP drop → nightly CSV batch → any ERP
     • Commerce → event broker → several consumers
     • Commerce → MuleSoft / Boomi / webMethods → non-SAP ERP

   So a flow is an ORDERED LIST OF STAGES, not a fixed four-hop pipeline, and
   the middleware stage is optional like every other stage. A CSV-only
   retailer gets a two-stage flow and the same UI renders it correctly.
   ========================================================================= */

/** The shape of a landscape. Extensible: the backend ships these, customers may add more. */
export type IntegrationTopology =
  /** Commerce talks straight to the target system. No middleware hop. */
  | 'DIRECT'
  /** An integration platform sits in the middle (SAP CPI, MuleSoft, Boomi, webMethods, custom ESB). */
  | 'MIDDLEWARE'
  /** Files exchanged over SFTP/S3/shared folder, usually batch. The CSV-only case. */
  | 'FILE_BASED'
  /** Published to a broker; consumers subscribe. Kafka, Event Mesh, SQS. */
  | 'EVENT_STREAM'
  /** More than one of the above on the same interface, e.g. sync API with a batch fallback. */
  | 'HYBRID'
  | string;

/** What a stage *is*, which decides how the visualiser draws it and what detail it asks for. */
export type StageKind =
  | 'SOURCE'
  | 'DOMAIN_MODEL'
  | 'CONVERSION'
  | 'DTO'
  | 'PAYLOAD_BUILD'
  | 'TRANSPORT'
  | 'MIDDLEWARE'
  | 'TRANSFORMATION'
  | 'VALIDATION'
  | 'ROUTING'
  | 'QUEUE'
  | 'PERSISTENCE'
  | 'TARGET'
  | 'ACKNOWLEDGEMENT'
  | string;

export type Direction = 'INBOUND' | 'OUTBOUND' | 'BIDIRECTIONAL';

export type ExchangeStyle = 'SYNC' | 'ASYNC' | 'BATCH' | 'STREAMING';

/** Wire protocols. Open by design — an org may run something not listed. */
export type Protocol =
  | 'REST' | 'SOAP' | 'ODATA' | 'IDOC' | 'RFC' | 'GRAPHQL'
  | 'FILE' | 'SFTP' | 'FTP' | 'S3' | 'JDBC' | 'JMS' | 'AMQP' | 'KAFKA'
  | 'EMAIL' | 'AS2' | 'EDI' | 'WEBHOOK'
  | string;

/** Payload formats the platform can parse, pretty-print, diff and validate. */
export type PayloadFormat =
  | 'JSON' | 'XML' | 'SOAP_ENVELOPE' | 'EDMX' | 'WSDL' | 'XSD' | 'IDOC_XML' | 'IDOC_FLAT'
  | 'CSV' | 'TSV' | 'FIXED_WIDTH' | 'EXCEL' | 'YAML' | 'PROPERTIES' | 'TEXT'
  | 'MULTIPART' | 'BINARY' | 'PDF' | 'IMAGE' | 'ZIP' | 'EDIFACT' | 'X12' | 'IMPEX' | 'AVRO' | 'PROTOBUF'
  | string;

export type AuthScheme =
  | 'NONE' | 'BASIC' | 'OAUTH2_CLIENT_CREDENTIALS' | 'OAUTH2_AUTH_CODE' | 'OAUTH2_SAML_BEARER'
  | 'CERTIFICATE' | 'API_KEY' | 'SAML' | 'JWT' | 'SSH_KEY' | 'PRINCIPAL_PROPAGATION'
  | string;

/* =========================================================================
   INTERFACES
   ========================================================================= */

/** One integration interface: the unit users search, filter and open. */
export interface IntegrationInterface {
  readonly id: string;
  readonly name: string;
  readonly description: string;
  readonly direction: Direction;
  readonly style: ExchangeStyle;
  readonly topology: IntegrationTopology;
  readonly protocols: readonly Protocol[];
  readonly formats: readonly PayloadFormat[];
  readonly auth: AuthScheme;
  /** Business object moved: Order, Invoice, Product, Customer, Price, Inventory… */
  readonly businessObject: string;
  /** Free-form domain grouping, e.g. "Order Management", "Master Data". */
  readonly domain: string;
  readonly sourceSystem: string;
  readonly targetSystem: string;
  /** Present only when the topology actually has a middleware hop. */
  readonly middleware?: string;
  readonly flowId?: string;
  readonly health: InterfaceHealth;
  /** Commerce-side classes, CPI iFlow names, SAP objects — whatever is known. */
  readonly relatedArtifacts: readonly RelatedArtifact[];
  readonly tags: readonly string[];
  readonly provenance: ProvenanceClass;
  /** True when this interface was inferred from repository contents rather than declared. */
  readonly discovered: boolean;
  /** Real captured pull requests whose changed files reached this interface. Empty on the list endpoint. */
  readonly recentChanges: readonly RecentInterfaceChange[];
}

/** One real, captured pull request that reached this interface — not a catalogue entry. */
export interface RecentInterfaceChange {
  readonly prId: string;
  readonly repoName: string;
  readonly prNumber?: number;
  readonly title: string;
  readonly author?: string;
  readonly mergedAt?: string;
  readonly reason: string;
  readonly severity: Severity;
  readonly riskLevel?: string;
}

export interface InterfaceHealth {
  readonly status: HealthStatus;
  readonly successRate?: number;
  readonly avgResponseMs?: number;
  readonly p95ResponseMs?: number;
  readonly volume24h?: number;
  readonly failures24h?: number;
  readonly retries24h?: number;
  readonly queueDepth?: number;
  readonly lastSuccessAt?: string;
  readonly lastFailureAt?: string;
  /** Why we cannot report health — e.g. "No monitoring source configured". */
  readonly unavailableReason?: string;
}

export interface RelatedArtifact {
  readonly kind: string;
  readonly name: string;
  readonly path?: string;
  readonly system?: string;
}

/* =========================================================================
   FLOWS
   ========================================================================= */

/** An end-to-end flow: an ordered, variable-length chain of stages. */
export interface FlowDefinition {
  readonly id: string;
  readonly name: string;
  readonly summary: string;
  readonly direction: Direction;
  readonly style: ExchangeStyle;
  readonly topology: IntegrationTopology;
  readonly businessObject: string;
  readonly stages: readonly FlowStage[];
  readonly interfaceIds: readonly string[];
  /** Alternate paths — retries, fallbacks, error routes. Drawn as branches. */
  readonly branches: readonly FlowBranch[];
  readonly tags: readonly string[];
  readonly provenance: ProvenanceClass;
}

/**
 * One hop. Every field beyond identity is optional because a CSV drop
 * genuinely has no "converters" and pretending otherwise is the rigidity we
 * are avoiding.
 */
export interface FlowStage {
  readonly id: string;
  readonly name: string;
  readonly kind: StageKind;
  /** Which system owns this hop: COMMERCE, MIDDLEWARE, TARGET, EXTERNAL. */
  readonly owner: string;
  readonly purpose: string;
  readonly input?: string;
  readonly output?: string;
  readonly format?: PayloadFormat;
  readonly protocol?: Protocol;
  readonly possibleErrors: readonly StageError[];
  readonly debugTips: readonly string[];
  readonly logLocations: readonly string[];
  readonly relatedClasses: readonly RelatedArtifact[];
  readonly optional: boolean;
}

export interface StageError {
  readonly code: string;
  readonly title: string;
  readonly severity: Severity;
  /** Link into Error Intelligence for the full explanation. */
  readonly errorPatternId?: string;
}

export interface FlowBranch {
  readonly id: string;
  readonly label: string;
  readonly fromStageId: string;
  readonly toStageId: string;
  readonly condition: string;
  readonly kind: 'RETRY' | 'FALLBACK' | 'ERROR' | 'CONDITIONAL' | string;
}

/* =========================================================================
   MAPPINGS
   ========================================================================= */

/**
 * A field's journey across systems. The chain is a list, not a fixed
 * Commerce→DTO→Payload→CPI→SAP quintet, so a direct or file-based interface
 * can describe a two-node chain honestly.
 */
export interface MappingLink {
  readonly id: string;
  readonly mappingSetId: string;
  readonly nodes: readonly MappingNode[];
  readonly status: MappingStatus;
  readonly required: boolean;
  readonly transformation?: string;
  readonly notes?: string;
  readonly confidence: ConfidenceLevel;
  readonly provenance: ProvenanceClass;
}

export interface MappingNode {
  /** Which layer this node lives in — free-form so unusual layers are expressible. */
  readonly layer: string;
  readonly field: string;
  readonly type?: string;
  readonly system?: string;
  readonly artifact?: string;
}

export type MappingStatus =
  | 'MAPPED'
  | 'MISSING'
  | 'RENAMED'
  | 'DELETED'
  | 'ADDED'
  | 'TYPE_CHANGED'
  | 'UNMAPPED_SOURCE'
  | 'UNMAPPED_TARGET'
  | string;

export interface MappingSet {
  readonly id: string;
  readonly name: string;
  readonly interfaceId?: string;
  readonly businessObject: string;
  readonly direction: Direction;
  /** Ordered layer names this set's chains traverse. Drives the column headers. */
  readonly layers: readonly string[];
  readonly linkCount: number;
  readonly issueCount: number;
  readonly version?: string;
  readonly updatedAt?: string;
}

/** Result of diffing two versions of a mapping set. */
export interface MappingComparison {
  readonly baseVersion: string;
  readonly targetVersion: string;
  readonly added: readonly MappingLink[];
  readonly removed: readonly MappingLink[];
  readonly changed: readonly MappingChange[];
  readonly unchangedCount: number;
}

export interface MappingChange {
  readonly link: MappingLink;
  readonly before: MappingLink;
  readonly reason: string;
}

/* =========================================================================
   PAYLOADS
   ========================================================================= */

export interface PayloadDocument {
  readonly id: string;
  readonly name: string;
  readonly format: PayloadFormat;
  readonly interfaceId?: string;
  readonly businessObject?: string;
  readonly direction?: Direction;
  readonly sizeBytes: number;
  readonly version?: string;
  readonly capturedAt?: string;
  readonly description?: string;
  readonly tags: readonly string[];
}

export interface PayloadContent {
  readonly document: PayloadDocument;
  readonly raw: string;
  /** Format-normalised tree so JSON, XML, CSV and IDoc all render in one viewer. */
  readonly tree?: PayloadNode;
  readonly validation: PayloadValidation;
  /** Present when the payload is tabular (CSV/Excel/fixed-width). */
  readonly table?: PayloadTable;
}

/** One node of the normalised payload tree. */
export interface PayloadNode {
  readonly path: string;
  readonly name: string;
  readonly kind: 'OBJECT' | 'ARRAY' | 'FIELD' | 'ATTRIBUTE' | 'TEXT' | string;
  readonly value?: string;
  readonly dataType?: string;
  readonly children?: readonly PayloadNode[];
  readonly required?: boolean;
  readonly repeating?: boolean;
}

export interface PayloadTable {
  readonly delimiter?: string;
  readonly headers: readonly string[];
  readonly rows: readonly (readonly string[])[];
  readonly truncated: boolean;
  readonly totalRows: number;
}

export interface PayloadValidation {
  readonly valid: boolean;
  readonly schemaApplied?: string;
  readonly issues: readonly ValidationIssue[];
}

export interface ValidationIssue {
  readonly path: string;
  readonly message: string;
  readonly severity: Severity;
  readonly line?: number;
  readonly column?: number;
  readonly rule?: string;
}

/** Structural diff of two payloads or two schemas. */
export interface PayloadComparison {
  readonly leftId: string;
  readonly rightId: string;
  readonly leftLabel: string;
  readonly rightLabel: string;
  readonly differences: readonly FieldDifference[];
  readonly identical: boolean;
  readonly summary: PayloadComparisonSummary;
}

export interface PayloadComparisonSummary {
  readonly added: number;
  readonly removed: number;
  readonly changed: number;
  readonly typeChanged: number;
  readonly unchanged: number;
}

export interface FieldDifference {
  readonly path: string;
  readonly change: 'ADDED' | 'REMOVED' | 'VALUE_CHANGED' | 'TYPE_CHANGED' | 'CARDINALITY_CHANGED' | string;
  readonly leftValue?: string;
  readonly rightValue?: string;
  readonly leftType?: string;
  readonly rightType?: string;
  /** Set when the difference is likely to break a consumer. */
  readonly breaking: boolean;
  readonly note?: string;
}

/* =========================================================================
   API CATALOGUE
   ========================================================================= */

export interface ApiDefinition {
  readonly id: string;
  readonly name: string;
  readonly kind: 'REST' | 'SOAP' | 'ODATA' | 'OCC' | 'GRAPHQL' | 'WEBHOOK' | string;
  readonly description: string;
  readonly basePath?: string;
  readonly version?: string;
  readonly system: string;
  readonly specFormat?: 'OPENAPI' | 'WSDL' | 'EDMX' | 'NONE' | string;
  readonly specUrl?: string;
  readonly auth: AuthScheme;
  readonly operations: readonly ApiOperation[];
  readonly tags: readonly string[];
  /** True when a PR in the current window touched this API's contract. */
  readonly changedInWindow: boolean;
  readonly provenance: ProvenanceClass;
}

export interface ApiOperation {
  readonly id: string;
  readonly name: string;
  readonly method?: string;
  readonly path?: string;
  readonly summary: string;
  readonly requestFormat?: PayloadFormat;
  readonly responseFormat?: PayloadFormat;
  readonly deprecated: boolean;
  readonly changed: boolean;
  readonly samplePayloadId?: string;
}

/* =========================================================================
   INTEGRATION HEALTH DASHBOARD
   ========================================================================= */

export interface IntegrationHealthSnapshot {
  readonly status: HealthStatus;
  readonly successRate?: number;
  readonly avgResponseMs?: number;
  readonly p95ResponseMs?: number;
  readonly failedInterfaces: number;
  readonly totalInterfaces: number;
  readonly retryCount?: number;
  readonly queueBacklog?: number;
  readonly avgProcessingMs?: number;
  readonly dailyVolume?: number;
  readonly peakVolume?: number;
  readonly peakAt?: string;
  readonly topFailures: readonly CountEntry[];
  readonly topInterfaces: readonly CountEntry[];
  readonly volumeTrend: readonly TrendPoint[];
  readonly successTrend: readonly TrendPoint[];
  /** Populated when no telemetry source is wired up, so the UI says so rather than showing zeros. */
  readonly unavailableReason?: string;
  readonly provenance: ProvenanceClass;
}

/** Landscape summary shown at the top of the Integration Center. */
export interface IntegrationOverview {
  readonly topologies: readonly TopologySummary[];
  readonly totalInterfaces: number;
  readonly inboundCount: number;
  readonly outboundCount: number;
  readonly syncCount: number;
  readonly asyncCount: number;
  readonly batchCount: number;
  readonly protocolBreakdown: readonly CountEntry[];
  readonly formatBreakdown: readonly CountEntry[];
  readonly domainBreakdown: readonly CountEntry[];
  readonly health: IntegrationHealthSnapshot;
}

/**
 * One topology present in this landscape, with the stage chain it uses. The
 * Integration Overview draws one clickable diagram per topology, so a company
 * running both CPI and nightly CSV sees both, accurately.
 */
export interface TopologySummary {
  readonly topology: IntegrationTopology;
  readonly label: string;
  readonly description: string;
  readonly interfaceCount: number;
  readonly stageChain: readonly TopologyStage[];
  readonly health: HealthStatus;
}

export interface TopologyStage {
  readonly id: string;
  readonly label: string;
  readonly kind: StageKind;
  readonly detail: string;
  readonly protocols: readonly Protocol[];
  readonly interfaceCount: number;
  readonly tone: Tone;
}
