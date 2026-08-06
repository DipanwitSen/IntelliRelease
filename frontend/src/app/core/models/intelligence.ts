import { ConfidenceLevel, CountEntry, ProvenanceClass, RiskLevel, Severity } from './common';
import { Direction, PayloadFormat, RelatedArtifact } from './integration';

/* =========================================================================
   ERROR INTELLIGENCE
   -------------------------------------------------------------------------
   The point of this module is to turn "com.sap.SomeException: no column
   found" into something a support engineer, a QA lead and a release manager
   can each act on. The explanation is assembled from a curated catalogue
   (deterministic, provenance RULE_OUTPUT); only the narrative prose is ever
   AI-written, and it is labelled as such.
   ========================================================================= */

/** A recognised failure mode in the catalogue. */
export interface ErrorPattern {
  readonly id: string;
  readonly code: string;
  readonly title: string;
  /** Plain-language, non-technical. This is what a release manager reads. */
  readonly businessExplanation: string;
  readonly technicalExplanation: string;
  readonly category: ErrorCategory;
  readonly severity: Severity;
  /** Which layers this failure can surface in — never assumes a middleware exists. */
  readonly layers: readonly ErrorLayer[];
  readonly signatures: readonly ErrorSignature[];
  readonly rootCauses: readonly RootCause[];
  readonly suggestedFixes: readonly SuggestedFix[];
  readonly recommendedTests: readonly string[];
  readonly relatedArtifacts: readonly RelatedArtifact[];
  readonly relatedPatternIds: readonly string[];
  readonly documentationLinks: readonly DocumentationLink[];
  readonly tags: readonly string[];
}

export type ErrorCategory =
  | 'AUTHENTICATION' | 'AUTHORIZATION' | 'TRANSPORT' | 'TIMEOUT'
  | 'DATA_MODEL' | 'VALIDATION' | 'MAPPING' | 'SERIALIZATION'
  | 'PERSISTENCE' | 'CONFIGURATION' | 'BUSINESS_RULE' | 'PLATFORM'
  | 'MIDDLEWARE' | 'TARGET_SYSTEM' | 'UNKNOWN'
  | string;

/**
 * Where a failure surfaced. `layer` is a free-form system name rather than an
 * enum of {Commerce, CPI, S4} so a landscape without CPI — or with MuleSoft
 * instead — is describable without lying.
 */
export interface ErrorLayer {
  readonly layer: string;
  readonly whatHappensHere: string;
  readonly typicalSymptom: string;
  readonly whereToLook: readonly string[];
}

/** How the classifier recognises this pattern in raw text. */
export interface ErrorSignature {
  readonly kind: 'EXCEPTION_CLASS' | 'MESSAGE_REGEX' | 'HTTP_STATUS' | 'SOAP_FAULT_CODE' | 'ERROR_CODE' | string;
  readonly value: string;
  readonly weight: number;
}

export interface RootCause {
  readonly id: string;
  readonly cause: string;
  readonly likelihood: ConfidenceLevel;
  readonly howToConfirm: string;
  readonly layer?: string;
}

export interface SuggestedFix {
  readonly id: string;
  readonly summary: string;
  readonly detail: string;
  readonly appliesTo: string;
  readonly effort: 'TRIVIAL' | 'SMALL' | 'MEDIUM' | 'LARGE' | string;
  readonly risk: RiskLevel;
  readonly provenance: ProvenanceClass;
}

export interface DocumentationLink {
  readonly label: string;
  readonly url?: string;
  readonly reference?: string;
  readonly kind: 'SAP_NOTE' | 'HELP_PORTAL' | 'INTERNAL' | 'RUNBOOK' | 'BLOG' | string;
}

/** Enough to link back into the Integration Center without a second lookup. */
export interface InterfaceRef {
  readonly id: string;
  readonly name: string;
}

/** The result of running a raw error through the classifier. */
export interface ErrorExplanation {
  readonly inputExcerpt: string;
  readonly matched: boolean;
  readonly pattern?: ErrorPattern;
  /** 0–100. Deterministic: derived from how many signatures matched and how strongly. */
  readonly matchConfidence: number;
  readonly matchedSignatures: readonly string[];
  readonly whatHappened: string;
  readonly whereItFailed: readonly ErrorLocation[];
  readonly affectedInterfaces: readonly InterfaceRef[];
  readonly affectedPayloadFields: readonly string[];
  readonly affectedReleases: readonly AffectedRelease[];
  readonly similarIncidents: readonly SimilarIncident[];
  /** AI-written narrative. Always rendered behind an AI_INFERENCE provenance badge. */
  readonly narrative?: string;
  readonly provenance: ProvenanceClass;
  /** Set when nothing in the catalogue matched, explaining what the user can do next. */
  readonly unmatchedGuidance?: string;
}

export interface ErrorLocation {
  readonly layer: string;
  readonly component: string;
  readonly detail: string;
  readonly evidence: string;
  readonly confidence: ConfidenceLevel;
}

export interface AffectedRelease {
  readonly releaseId: string;
  readonly version: string;
  readonly repoName: string;
  readonly deployedAt?: string;
}

export interface SimilarIncident {
  readonly id: string;
  readonly title: string;
  readonly occurredAt?: string;
  readonly resolution?: string;
  readonly similarity: number;
}

/** A recorded occurrence, so the module has a history and not just a catalogue. */
export interface ErrorOccurrence {
  readonly id: string;
  readonly patternId?: string;
  readonly title: string;
  readonly category: ErrorCategory;
  readonly severity: Severity;
  readonly layer: string;
  readonly interfaceId?: string;
  readonly correlationId?: string;
  readonly occurredAt: string;
  readonly count: number;
  readonly resolved: boolean;
  readonly excerpt: string;
}

/* =========================================================================
   KNOWLEDGE BASE
   ========================================================================= */

export interface KnowledgeArticle {
  readonly id: string;
  readonly title: string;
  readonly category: KnowledgeCategory;
  readonly summary: string;
  /** Markdown. Rendered by the KB viewer. */
  readonly body?: string;
  readonly tags: readonly string[];
  readonly appliesTo: readonly string[];
  readonly references: readonly DocumentationLink[];
  readonly updatedAt?: string;
  readonly provenance: ProvenanceClass;
}

export type KnowledgeCategory =
  | 'INTEGRATION_PATTERN' | 'COMMERCE_BEST_PRACTICE' | 'MIDDLEWARE_BEST_PRACTICE'
  | 'SAP_NOTE' | 'KNOWN_ISSUE' | 'ARCHITECTURE' | 'GLOSSARY'
  | 'SAMPLE_PAYLOAD' | 'SAMPLE_MAPPING' | 'FAQ' | 'RUNBOOK'
  | string;

export interface GlossaryTerm {
  readonly term: string;
  readonly definition: string;
  readonly aliases: readonly string[];
  readonly category: string;
}

/* =========================================================================
   AI OUTPUT
   -------------------------------------------------------------------------
   Every section is optional. The AI service emits only the sections it has
   deterministic input for; a CSV-only change with no middleware produces no
   middlewareImpact section rather than an invented one.
   ========================================================================= */

/** Mirrors backend AiAnalysisResponse exactly — every field here is prose, nothing else. */
export interface AiAnalysis {
  readonly technicalSummary?: string;
  readonly qaSummary?: string;
  readonly businessSummary?: string;
  readonly clientSummary?: string;
  readonly riskExplanation?: string;
  readonly regressionGuidance?: string;
  readonly readinessExplanation?: string;
  readonly deploymentStrategyExplanation?: string;
  readonly provenanceClass: ProvenanceClass;
  /** True when the LLM was unavailable and the deterministic narrator produced this instead. */
  readonly fallback: boolean;
  readonly provider?: string;
  readonly model?: string;
  readonly tokensUsed?: number;
}

export interface ChangedComponent {
  readonly name: string;
  readonly kind: string;
  readonly path?: string;
  readonly changeType: string;
  readonly impact: string;
}

export interface AiRisk {
  readonly title: string;
  readonly detail: string;
  readonly level: RiskLevel;
  readonly area: string;
  readonly mitigation?: string;
}

export interface RecommendedTest {
  readonly id: string;
  readonly title: string;
  readonly area: string;
  readonly priority: 'P1' | 'P2' | 'P3' | string;
  readonly rationale: string;
  readonly steps: readonly string[];
}

export interface AudienceNotes {
  readonly executive?: string;
  readonly technical?: string;
  readonly qa?: string;
  readonly business?: string;
  readonly customer?: string;
}

export interface AiModelInfo {
  readonly provider: string;
  readonly name: string;
  readonly tokensUsed?: number;
  readonly latencyMs?: number;
}

/* =========================================================================
   CONTEXT PACKAGE
   -------------------------------------------------------------------------
   What the deterministic pipeline hands to the LLM. Surfaced in the UI so a
   reviewer can audit exactly what the model was told — and confirm no raw
   GitHub payload or source file was sent.
   ========================================================================= */

export interface ContextPackage {
  readonly id: string;
  readonly prId?: string;
  readonly releaseId?: string;
  readonly generatedAt: string;
  readonly engineVersion: string;
  readonly sections: readonly ContextSection[];
  readonly stats: ContextPackageStats;
  readonly redactions: readonly string[];
}

export interface ContextSection {
  readonly key: string;
  readonly label: string;
  readonly provenance: ProvenanceClass;
  readonly itemCount: number;
  /** The exact serialised text of this section as sent to the model. */
  readonly content: string;
}

export interface ContextPackageStats {
  readonly filesExamined: number;
  readonly filesIncluded: number;
  readonly filesExcluded: number;
  readonly rawBytes: number;
  readonly packagedBytes: number;
  readonly compressionRatio: number;
  readonly estimatedTokens: number;
  readonly exclusionReasons: readonly CountEntry[];
}

/* =========================================================================
   AI ASSISTANT (chat)
   ========================================================================= */

export interface AssistantMessage {
  readonly id: string;
  readonly role: 'user' | 'assistant' | 'system';
  readonly content: string;
  readonly createdAt: string;
  readonly pending?: boolean;
  readonly error?: string;
  /** Deterministic facts the answer was grounded in. Rendered as citations. */
  readonly citations?: readonly AssistantCitation[];
  readonly provenance?: ProvenanceClass;
}

export interface AssistantCitation {
  readonly label: string;
  readonly kind: string;
  /** Mutable array: Angular's `routerLink` input does not accept `readonly`. */
  readonly routerLink?: string[];
  readonly detail?: string;
}

export interface AssistantRequest {
  readonly message: string;
  readonly conversationId?: string;
  /** Anchors the answer to something concrete the user is looking at. */
  readonly context?: AssistantContextRef;
}

export interface AssistantContextRef {
  readonly kind: 'PR' | 'RELEASE' | 'INTERFACE' | 'FLOW' | 'ERROR' | 'PAYLOAD' | 'MAPPING' | string;
  readonly id: string;
}

export interface AssistantSuggestion {
  readonly label: string;
  readonly prompt: string;
  readonly category: string;
}

/** What the AI service reports about itself — drives the "AI unavailable" banner. */
export interface AiServiceStatus {
  readonly available: boolean;
  readonly provider?: string;
  readonly model?: string;
  readonly deterministicFallback: boolean;
  readonly message?: string;
}

/* =========================================================================
   PARSED INPUT — the "understand any file you throw at it" surface
   ========================================================================= */

export interface ParsedInput {
  readonly detectedFormat: PayloadFormat;
  readonly detectedKind: string;
  readonly direction?: Direction;
  readonly confidence: ConfidenceLevel;
  readonly summary: string;
  readonly entries: readonly ParsedEntry[];
  readonly issues: readonly string[];
}

export interface ParsedEntry {
  readonly name: string;
  readonly format: PayloadFormat;
  readonly sizeBytes: number;
  readonly summary: string;
}
