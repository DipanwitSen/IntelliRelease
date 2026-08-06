import { HealthStatus, ProvenanceClass, ReadinessStatus, ReleaseStatus, RiskLevel, Severity, Tone } from '../core/models/common';
import { DeploymentStatus } from '../core/models/delivery';
import { MappingStatus } from '../core/models/integration';

/**
 * Domain value -> visual tone, in one place.
 *
 * Colour is a claim about severity, and the same claim has to be made the same
 * way everywhere: HIGH risk on the dashboard, on a PR row and inside an AI
 * summary must all read as the same amber. Scattering these `switch`
 * statements across components is exactly how a product ends up with three
 * different shades of "bad".
 *
 * Every function falls through to `neutral` for values it does not recognise —
 * the backend catalogues are extensible, and an unknown status should look
 * uncommitted rather than accidentally reassuring.
 */

export function riskTone(level: RiskLevel | string | null | undefined): Tone {
  switch (level) {
    case 'LOW': return 'success';
    case 'MEDIUM': return 'warning';
    case 'HIGH': return 'danger';
    case 'CRITICAL': return 'danger';
    default: return 'neutral';
  }
}

/** CRITICAL needs to outrank HIGH visually; the badge adds a border for it. */
export function isCritical(level: RiskLevel | Severity | string | null | undefined): boolean {
  return level === 'CRITICAL';
}

export function readinessTone(status: ReadinessStatus | string | null | undefined): Tone {
  switch (status) {
    case 'READY': return 'success';
    case 'READY_WITH_WARNINGS': return 'warning';
    case 'NOT_READY': return 'danger';
    case 'BLOCKED': return 'danger';
    default: return 'neutral';
  }
}

export function healthTone(status: HealthStatus | string | null | undefined): Tone {
  switch (status) {
    case 'HEALTHY': return 'success';
    case 'DEGRADED': return 'warning';
    case 'UNHEALTHY': return 'danger';
    case 'NOT_CONFIGURED': return 'neutral';
    default: return 'neutral';
  }
}

export function severityTone(severity: Severity | string | null | undefined): Tone {
  switch (severity) {
    case 'CRITICAL':
    case 'HIGH': return 'danger';
    case 'MEDIUM': return 'warning';
    case 'LOW': return 'info';
    case 'INFO': return 'neutral';
    default: return 'neutral';
  }
}

/**
 * AI_INFERENCE is `ai` (violet) rather than a status colour on purpose. It is
 * not "worse" than a fact — it is a different *kind* of claim, and the palette
 * says so.
 */
export function provenanceTone(provenance: ProvenanceClass | string | null | undefined): Tone {
  switch (provenance) {
    case 'FACT': return 'success';
    case 'DERIVED_FACT': return 'info';
    case 'RULE_OUTPUT': return 'accent';
    case 'AI_INFERENCE': return 'ai';
    default: return 'neutral';
  }
}

export function deploymentTone(status: DeploymentStatus | string | null | undefined): Tone {
  switch (status) {
    case 'SUCCEEDED': return 'success';
    case 'IN_PROGRESS':
    case 'PENDING': return 'info';
    case 'FAILED': return 'danger';
    case 'ROLLED_BACK': return 'warning';
    case 'CANCELLED': return 'neutral';
    default: return 'neutral';
  }
}

export function releaseStatusTone(status: ReleaseStatus | string | null | undefined): Tone {
  switch (status) {
    case 'RELEASED': return 'success';
    case 'APPROVED': return 'accent';
    case 'NOTES_GENERATED': return 'warning';
    case 'ANALYZED':
    case 'BUILT': return 'info';
    case 'DRAFT': return 'neutral';
    default: return 'neutral';
  }
}

export function mappingStatusTone(status: MappingStatus | string | null | undefined): Tone {
  switch (status) {
    case 'MAPPED': return 'success';
    case 'ADDED': return 'info';
    case 'RENAMED':
    case 'TYPE_CHANGED': return 'warning';
    case 'MISSING':
    case 'DELETED': return 'danger';
    case 'UNMAPPED_SOURCE':
    case 'UNMAPPED_TARGET': return 'warning';
    default: return 'neutral';
  }
}

export function buildStatusTone(status: string | null | undefined): Tone {
  switch (status) {
    case 'PASSED': return 'success';
    case 'FAILED': return 'danger';
    case 'RUNNING': return 'info';
    case 'NOT_CONFIGURED': return 'neutral';
    default: return 'neutral';
  }
}

/**
 * Tone for a 0–100 score where higher is better (readiness, success rate,
 * repository health). Risk scores invert this — use {@link riskScoreTone}.
 */
export function scoreTone(score: number | null | undefined): Tone {
  if (score === null || score === undefined) return 'neutral';
  if (score >= 80) return 'success';
  if (score >= 60) return 'warning';
  return 'danger';
}

/** Tone for a 0–100 score where higher is worse. */
export function riskScoreTone(score: number | null | undefined): Tone {
  if (score === null || score === undefined) return 'neutral';
  if (score >= 70) return 'danger';
  if (score >= 40) return 'warning';
  return 'success';
}

/** Icon that pairs with a tone in callouts, toasts and inline status. */
export function toneIcon(tone: Tone): string {
  switch (tone) {
    case 'success': return 'check-circle';
    case 'warning': return 'alert-triangle';
    case 'danger': return 'x-circle';
    case 'ai': return 'sparkles';
    case 'info':
    case 'accent': return 'info';
    default: return 'info';
  }
}

/**
 * Turns SCREAMING_SNAKE_CASE into Title Case for display.
 *
 * Backend enums reach the UI verbatim so the wire format stays greppable;
 * this is the single place they become human-readable, which also means an
 * enum value added tomorrow renders correctly with no UI change.
 */
export function humanise(value: string | null | undefined): string {
  if (!value) return '—';
  return value
    .replace(/[_-]+/g, ' ')
    .toLowerCase()
    .replace(/\b\w/g, (character) => character.toUpperCase());
}
