import { CountEntry, HealthStatus, ProvenanceClass, ReadinessStatus, RiskLevel, Tone, TrendPoint } from './common';
import { ActivityEvent, BuildStatus, Contributor, DeploymentEvent, ReleaseSummary, RepositorySummary } from './delivery';
import { ErrorOccurrence } from './intelligence';

/**
 * The single payload behind the landing page. One request, because a
 * dashboard that fires fifteen is a dashboard that renders in fifteen stages.
 */
export interface DashboardSnapshot {
  readonly generatedAt: string;
  readonly windowDays: number;
  readonly kpis: readonly Kpi[];
  readonly delivery: DeliverySection;
  readonly change: ChangeSection;
  readonly integration: IntegrationSection;
  readonly recentActivity: readonly ActivityEvent[];
  readonly recentDeployments: readonly DeploymentEvent[];
  readonly recentErrors: readonly ErrorOccurrence[];
  readonly recentReleases: readonly ReleaseSummary[];
  readonly riskTrend: readonly TrendPoint[];
  readonly releaseTimeline: readonly TimelineEntry[];
  readonly topContributors: readonly Contributor[];
  readonly repositoryHealth: readonly RepositorySummary[];
}

/**
 * A headline metric. `value` is a pre-formatted string so the backend owns
 * unit and precision decisions — the tile just renders what it is handed,
 * and every tile in the product formats numbers the same way.
 */
export interface Kpi {
  readonly key: string;
  readonly label: string;
  readonly value: string;
  readonly numericValue?: number;
  readonly unit?: string;
  readonly tone: Tone;
  readonly icon: string;
  /** Percent change against the previous window. Omitted when there is no baseline. */
  readonly deltaPercent?: number;
  /** Whether a rise is good. Cost and error counts invert this. */
  readonly deltaIsGood?: boolean;
  readonly sparkline?: readonly number[];
  readonly detail?: string;
  /** Mutable array: Angular's `routerLink` input does not accept `readonly`. */
  readonly routerLink?: string[];
  readonly provenance: ProvenanceClass;
  /** Set when the metric cannot be computed, e.g. no CI connected. */
  readonly unavailableReason?: string;
}

export interface DeliverySection {
  readonly totalRepositories: number;
  readonly mergedPrsToday: number;
  readonly mergedPrsWindow: number;
  readonly openReleases: number;
  readonly releaseReadiness: ReadinessSummary;
  readonly deploymentStatus: DeploymentStatusSummary;
  readonly buildStatus: BuildStatusSummary;
}

export interface ReadinessSummary {
  readonly status: ReadinessStatus;
  readonly score: number;
  readonly blockers: readonly string[];
  readonly warnings: readonly string[];
  readonly nextRelease?: string;
}

export interface DeploymentStatusSummary {
  readonly current: string;
  readonly environment?: string;
  readonly lastDeployedAt?: string;
  readonly lastVersion?: string;
  readonly inFlight: number;
  readonly failed24h: number;
  readonly unavailableReason?: string;
}

export interface BuildStatusSummary {
  readonly status: BuildStatus;
  readonly passed: number;
  readonly failed: number;
  readonly failedTests: number;
  readonly totalTests: number;
  readonly lastRunAt?: string;
  readonly unavailableReason?: string;
}

/** What actually changed in the window, aggregated across repositories. */
export interface ChangeSection {
  readonly changedModules: readonly CountEntry[];
  readonly changedApis: readonly CountEntry[];
  readonly changedDtos: readonly CountEntry[];
  readonly changedIntegrationObjects: readonly CountEntry[];
  readonly impactedInterfaces: readonly CountEntry[];
  readonly aiRiskScore?: number;
  readonly aiRiskLevel?: RiskLevel;
  readonly riskProvenance: ProvenanceClass;
}

/**
 * Landscape health. `middleware` and `targetSystem` are nullable on purpose:
 * a customer with no middleware gets no middleware card rather than a card
 * reading "unknown" forever.
 */
export interface IntegrationSection {
  readonly overall: HealthStatus;
  readonly successRate?: number;
  readonly totalInterfaces: number;
  readonly failedInterfaces: number;
  readonly middleware?: SystemHealth;
  readonly targetSystem?: SystemHealth;
  readonly commerce?: SystemHealth;
  readonly topFailures: readonly CountEntry[];
  readonly unavailableReason?: string;
}

export interface SystemHealth {
  readonly key: string;
  readonly label: string;
  readonly status: HealthStatus;
  readonly detail?: string;
  readonly successRate?: number;
  readonly avgResponseMs?: number;
  readonly lastCheckedAt?: string;
}

export interface TimelineEntry {
  readonly id: string;
  readonly label: string;
  readonly detail?: string;
  readonly timestamp: string;
  readonly status: string;
  readonly tone: Tone;
  readonly icon?: string;
  readonly routerLink?: string[];
}
