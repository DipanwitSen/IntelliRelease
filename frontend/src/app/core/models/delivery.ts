import {
  ConfidenceLevel, CountEntry, HealthStatus, ProvenanceClass, ReadinessStatus,
  ReleaseStatus, RiskLevel, Severity, TrendPoint,
} from './common';
import { DeploymentStrategyResult, DeploymentStrategyType } from './deployment-strategy';
import { AiAnalysis } from './intelligence';
import { Direction, RelatedArtifact } from './integration';

/* =========================================================================
   REPOSITORIES
   ========================================================================= */

export interface RepositorySummary {
  readonly id: string;
  readonly name: string;
  readonly fullName: string;
  readonly defaultBranch: string;
  readonly description?: string;
  readonly language?: string;
  readonly openPrCount: number;
  readonly mergedPrCount30d: number;
  readonly lastActivityAt?: string;
  readonly health: RepositoryHealth;
  readonly webhookConnected: boolean;
  readonly tags: readonly string[];
}

export interface RepositoryHealth {
  readonly status: HealthStatus;
  /** 0–100, deterministic from the factors below. */
  readonly score: number;
  readonly factors: readonly HealthFactor[];
}

export interface HealthFactor {
  readonly key: string;
  readonly label: string;
  readonly value: string;
  readonly weight: number;
  readonly tone: 'success' | 'warning' | 'danger' | 'neutral';
  readonly detail?: string;
}

export interface RepositoryDetail extends RepositorySummary {
  readonly contributors: readonly Contributor[];
  readonly riskTrend: readonly TrendPoint[];
  readonly recentPullRequests: readonly PullRequestSummary[];
  readonly capabilityBreakdown: readonly CountEntry[];
  readonly interfaceIds: readonly string[];
}

export interface Contributor {
  readonly login: string;
  readonly displayName?: string;
  readonly prCount: number;
  readonly avgRiskScore?: number;
  readonly lastContributionAt?: string;
}

/* =========================================================================
   PULL REQUESTS
   ========================================================================= */

export interface PullRequestSummary {
  readonly prId: string;
  readonly repoName: string;
  readonly prNumber: number;
  readonly title: string;
  readonly author: string;
  readonly mergedAt?: string;
  readonly riskScore?: number;
  readonly riskLevel?: RiskLevel;
  readonly deploymentReadinessScore?: number;
  readonly deploymentReadinessStatus?: ReadinessStatus;
  readonly analyzed: boolean;
  /** Headline counts so the list can show impact without a second request. */
  readonly changedFileCount?: number;
  readonly capabilities?: readonly string[];
  readonly integrationTouched?: boolean;
  readonly ticketKey?: string;
  /** ROLLING or MIGRATE — see the Deployment Strategy Advisor. */
  readonly deploymentStrategyType?: DeploymentStrategyType;
}

export interface PullRequestDetail {
  readonly prId: string;
  readonly repoName: string;
  readonly prNumber: number;
  readonly title: string;
  readonly description?: string;
  readonly author: string;
  readonly branch?: string;
  readonly ticketKey?: string;
  readonly mergeSha?: string;
  readonly mergedAt?: string;
  readonly changedFiles?: readonly ChangedFileContext[];
  readonly analyzed: boolean;
  readonly sapCommerceContext?: SapCommerceContext;
  /** ROLLING or MIGRATE, and why — computed immediately after sapCommerceContext. */
  readonly deploymentStrategy?: DeploymentStrategyResult;
  readonly deploymentStrategyType?: DeploymentStrategyType;
  readonly integrationContext?: IntegrationContext;
  readonly impactAnalysis?: ImpactAnalysis;
  readonly regressionRecommendation?: RegressionRecommendation;
  readonly riskScore?: number;
  readonly riskLevel?: RiskLevel;
  readonly riskReasons?: readonly RiskReason[];
  readonly riskPolicyVersion?: string;
  readonly configurationDrift?: ConfigurationDrift;
  readonly aiSummary?: AiAnalysis;
  readonly deploymentReadinessScore?: number;
  readonly deploymentReadinessStatus?: ReadinessStatus;
  readonly provenanceClass?: ProvenanceClass;
  /** The human decision on top of deploymentStrategyType — undefined until confirmed. */
  readonly confirmedDeploymentStrategy?: DeploymentStrategyType;
  readonly confirmedBy?: string;
  readonly confirmedAt?: string;
}

export interface ChangedFileContext {
  readonly path: string;
  readonly changeType?: string;
  readonly additions?: number;
  readonly deletions?: number;
  readonly artifactType?: string;
  readonly displayName?: string;
  readonly sapCapability?: string;
  readonly businessDomain?: string;
  readonly generalRole?: string;
  readonly deploymentRisk?: string;
  readonly businessCapability?: readonly string[];
  readonly potentialImpact?: readonly string[];
  readonly regressionAreas?: readonly string[];
  readonly confidence?: ConfidenceLevel;
  readonly evidence?: string;
  readonly provenance?: ProvenanceClass;
}

export interface SapCommerceContext {
  readonly capabilities: readonly string[];
  readonly totalFiles: number;
  readonly unclassifiedFiles: number;
  readonly testsIncluded: boolean;
  readonly libraryVersion?: string;
  readonly provenanceClass?: ProvenanceClass;
  readonly files?: readonly ChangedFileContext[];
}

/**
 * What a change means for integrations. Every collection can legitimately be
 * empty — a pure storefront change touches no interface, and the UI says
 * exactly that rather than inventing impact.
 */
export interface IntegrationContext {
  readonly touched: boolean;
  readonly directions: readonly Direction[];
  readonly impactedInterfaces: readonly ImpactedInterface[];
  readonly impactedPayloads: readonly string[];
  readonly impactedMappings: readonly MappingRef[];
  readonly impactedDtos: readonly string[];
  readonly impactedCommerceModels: readonly string[];
  readonly impactedTargetObjects: readonly string[];
  readonly impactedMiddlewareFlows: readonly string[];
  readonly detectedTopologies: readonly string[];
  readonly evidence: readonly string[];
  readonly provenance: ProvenanceClass;
}

export interface ImpactedInterface {
  readonly interfaceId: string;
  readonly name: string;
  readonly direction: Direction;
  readonly reason: string;
  readonly severity: Severity;
  readonly confidence: ConfidenceLevel;
}

/** A mapping set a change reaches, with its current known issue count. */
export interface MappingRef {
  readonly id: string;
  readonly name: string;
  readonly issueCount: number;
}

export interface ImpactAnalysis {
  readonly items: readonly ImpactItem[];
  readonly highestSeverity?: Severity;
  readonly provenanceClass?: ProvenanceClass;
}

export interface ImpactItem {
  readonly area: string;
  readonly impactType: string;
  readonly description: string;
  readonly severity?: Severity;
  readonly evidence?: string;
}

export interface RegressionRecommendation {
  readonly suggestions: readonly RegressionSuggestion[];
  readonly provenanceClass?: ProvenanceClass;
}

export interface RegressionSuggestion {
  readonly suite: string;
  readonly rationale: string;
  readonly priority?: string;
  readonly area?: string;
}

export interface RiskReason {
  readonly code?: string;
  readonly description: string;
  readonly points?: number;
  readonly category?: string;
}

export interface ConfigurationDrift {
  readonly items: readonly DriftItem[];
  readonly classification?: string;
  readonly provenanceClass?: ProvenanceClass;
}

export interface DriftItem {
  readonly key: string;
  readonly classification: string;
  readonly description: string;
  readonly environment?: string;
}

/* =========================================================================
   RELEASES
   ========================================================================= */

export interface ReleaseSummary {
  readonly releaseId: string;
  readonly repoName: string;
  readonly version: string;
  readonly fromRef: string;
  readonly toRef: string;
  readonly status: ReleaseStatus;
  readonly resolvedPrCount?: number;
  readonly aggregateRiskScore?: number;
  readonly aggregateRiskLevel?: RiskLevel;
  readonly readinessScore?: number;
  readonly readinessStatus?: ReadinessStatus;
  /**
   * ROLLING or MIGRATE — the release inherits this from its riskiest included
   * pull request. Undefined until the release is built; see
   * `ApiService.getReleaseDeploymentStrategy` for the full reasoning.
   */
  readonly deploymentStrategyType?: DeploymentStrategyType;
  readonly deployed: boolean;
  readonly deployedAt?: string;
  readonly deployedBy?: string;
  readonly createdAt?: string;
  readonly builtAt?: string;
}

export interface ReleaseDetail extends ReleaseSummary {
  readonly pullRequests?: readonly ReleasePullRequest[];
  readonly excludedPrs?: readonly ExcludedChange[];
}

export interface ReleasePullRequest {
  readonly prId: string;
  readonly prNumber: number;
  readonly title: string;
  readonly ticketKey?: string;
  readonly riskScore?: number;
  readonly riskLevel?: RiskLevel;
}

export interface ExcludedChange {
  readonly prNumber?: number;
  readonly sha?: string;
  readonly reason: string;
  readonly detail?: string;
}

export interface BuildResult {
  readonly release: ReleaseDetail;
  readonly commitsExamined: number;
  readonly gitAvailable: boolean;
  readonly unmatchedPrNumbers?: readonly number[];
  readonly resolverNotes?: readonly string[];
}

export interface NoteBullet {
  readonly prNumber: number | null;
  readonly ticketKey: string | null;
  readonly text: string;
}

/** The same four audience notes NotificationService emails — previewed here first. */
export interface AudienceNotes {
  readonly developer: string;
  readonly qa: string;
  readonly business: string;
  readonly client: string;
}

export interface ReleaseNotes {
  readonly version: string;
  readonly plainText: string;
  readonly bullets: readonly NoteBullet[];
  readonly audienceNotes?: AudienceNotes;
  readonly releaseSummary?: string;
  readonly knownRisks?: string;
  readonly deploymentRecommendation?: string;
  readonly fallback: boolean;
  readonly provider: string;
}

export interface CreateReleaseRequest {
  readonly repoName: string;
  readonly version: string;
  readonly fromRef: string;
  readonly toRef: string;
}

export interface NotifyResult {
  readonly emails: readonly EmailOutcome[];
  readonly teamsConfigured: boolean;
  readonly teamsSent: boolean;
  readonly fallback: boolean;
  readonly provider?: string;
}

export interface EmailOutcome {
  readonly audience: string;
  readonly recipient: string;
  readonly sent: boolean;
  readonly relayConfigured: boolean;
  readonly relayed: boolean;
  readonly error?: string;
}

/* =========================================================================
   DEPLOYMENTS
   ========================================================================= */

export interface DeploymentEvent {
  readonly id: string;
  readonly releaseId?: string;
  readonly repoName: string;
  readonly version: string;
  readonly environment: string;
  readonly status: DeploymentStatus;
  readonly startedAt: string;
  readonly finishedAt?: string;
  readonly durationMs?: number;
  readonly triggeredBy: string;
  readonly riskLevel?: RiskLevel;
  readonly buildStatus?: BuildStatus;
  readonly failedTests?: number;
  readonly totalTests?: number;
  readonly notes?: string;
  readonly rollbackOf?: string;
}

export type DeploymentStatus =
  | 'PENDING' | 'IN_PROGRESS' | 'SUCCEEDED' | 'FAILED' | 'ROLLED_BACK' | 'CANCELLED' | string;

export type BuildStatus = 'PASSED' | 'FAILED' | 'RUNNING' | 'UNKNOWN' | 'NOT_CONFIGURED' | string;

/* =========================================================================
   AUDIT
   ========================================================================= */

export interface AuditEntry {
  readonly id: string;
  readonly occurredAt: string;
  readonly actor: string;
  readonly action: string;
  readonly entityType: string;
  readonly entityId?: string;
  readonly correlationId?: string;
  readonly outcome: 'SUCCESS' | 'FAILURE' | 'DENIED' | string;
  readonly detail?: string;
  readonly ipAddress?: string;
}

/* =========================================================================
   SETTINGS
   ========================================================================= */

export interface PlatformSettings {
  readonly tenantId: string;
  readonly tenantName: string;
  readonly integration: IntegrationSettings;
  readonly ai: AiSettings;
  readonly notifications: NotificationSettings;
  readonly governance: GovernanceSettings;
  readonly connections: readonly ConnectionStatus[];
}

/**
 * The settings that keep the platform honest about a customer's landscape.
 * A CSV-only retailer switches middleware off here and every module — flows,
 * mappings, errors, AI output — stops mentioning a middleware layer.
 */
export interface IntegrationSettings {
  readonly enabledTopologies: readonly string[];
  readonly middlewareEnabled: boolean;
  readonly middlewareName?: string;
  readonly targetSystemName: string;
  readonly enabledProtocols: readonly string[];
  readonly enabledFormats: readonly string[];
}

export interface AiSettings {
  readonly enabled: boolean;
  readonly provider?: string;
  readonly model?: string;
  readonly deterministicFallback: boolean;
  readonly maxContextTokens?: number;
  readonly redactSecrets: boolean;
}

export interface NotificationSettings {
  readonly emailEnabled: boolean;
  readonly teamsEnabled: boolean;
  readonly audiences: readonly AudienceConfig[];
}

export interface AudienceConfig {
  readonly audience: string;
  readonly recipients: readonly string[];
  readonly enabled: boolean;
}

export interface GovernanceSettings {
  readonly approvalRequired: boolean;
  readonly riskPolicyVersion?: string;
  readonly blockOnCriticalRisk: boolean;
  readonly requireTestsForHighRisk: boolean;
}

export interface ConnectionStatus {
  readonly key: string;
  readonly label: string;
  readonly status: HealthStatus;
  readonly detail?: string;
  readonly lastCheckedAt?: string;
}

/* =========================================================================
   ACTIVITY
   ========================================================================= */

export interface ActivityEvent {
  readonly id: string;
  readonly kind: ActivityKind;
  readonly title: string;
  readonly detail?: string;
  readonly actor?: string;
  readonly occurredAt: string;
  readonly entityType?: string;
  readonly entityId?: string;
  readonly severity?: Severity;
  readonly relatedArtifacts?: readonly RelatedArtifact[];
}

export type ActivityKind =
  | 'PR_MERGED' | 'PR_ANALYZED' | 'RELEASE_CREATED' | 'RELEASE_BUILT' | 'RELEASE_APPROVED'
  | 'RELEASE_NOTIFIED' | 'DEPLOYMENT' | 'ROLLBACK' | 'ERROR' | 'DRIFT_DETECTED'
  | 'INTERFACE_CHANGED' | 'SETTINGS_CHANGED'
  | string;
