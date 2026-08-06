/**
 * Shared vocabulary between Spring Boot and Angular.
 *
 * A note on the string unions below: every one of them ends with `| string`.
 * That is deliberate. The backend's catalogues are data-driven — a customer
 * can add an integration protocol, a payload format or an error category
 * without a code change — so the union documents what ships in the box while
 * still typing values the box has never seen. Narrowing these to closed
 * unions would make the UI reject a perfectly valid customer extension.
 */

/**
 * Architecture rule 10: every stored record declares where its value came
 * from. The UI renders this on anything a human might act on, so nobody has
 * to guess whether they are looking at a measured fact or a model's opinion.
 */
export type ProvenanceClass =
  | 'FACT'
  | 'DERIVED_FACT'
  | 'RULE_OUTPUT'
  | 'AI_INFERENCE'
  | 'UNKNOWN';

/**
 * These four mirror the backend enums exactly — `RiskLevel`, `ReadinessStatus`,
 * `ReleaseStatus` and the health vocabulary. They are the one place closed
 * unions are correct, because the values come from Java enums rather than from
 * an extensible catalogue.
 *
 * The risk scale deliberately tops out at HIGH: the risk policy does not model
 * a fourth level, and inventing a CRITICAL here would mean building UI for a
 * state the backend can never produce.
 */
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export type ConfidenceLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export type ReadinessStatus = 'READY' | 'READY_WITH_WARNINGS' | 'NOT_READY';

export type HealthStatus = 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY' | 'UNKNOWN' | 'NOT_CONFIGURED';

/**
 * The release lifecycle as the backend actually models it. Note that DEPLOYED
 * is not a status: deployment is tracked by the separate `deployed` flag,
 * because RELEASED means "communications went out" and deployment is an
 * independent, human-asserted fact.
 */
export type ReleaseStatus =
  | 'DRAFT'
  | 'BUILT'
  | 'ANALYZED'
  | 'NOTES_GENERATED'
  | 'APPROVED'
  | 'RELEASED';

export type Severity = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

/** Visual tone. The single vocabulary every badge, dot, meter and callout speaks. */
export type Tone = 'success' | 'warning' | 'danger' | 'info' | 'ai' | 'neutral' | 'accent';

/** Page of results. Mirrors the backend's `PageResponse<T>`. */
export interface Page<T> {
  readonly items: readonly T[];
  readonly total: number;
  readonly page: number;
  readonly size: number;
}

/** Machine-readable error body from `ApiExceptionHandler`. */
export interface ApiError {
  readonly code: string;
  readonly message: string;
  readonly correlationId?: string;
  readonly timestamp?: string;
}

/**
 * A single point on a trend line. `label` is pre-formatted by the backend so
 * every chart in the product labels its axis the same way.
 */
export interface TrendPoint {
  readonly label: string;
  readonly value: number;
  readonly timestamp?: string;
}

/** A named count — the shape behind every "top N" list and bar chart. */
export interface CountEntry {
  readonly key: string;
  readonly label: string;
  readonly count: number;
  readonly tone?: Tone;
}

/**
 * Anything the platform asserts, with its provenance attached. Used wherever a
 * value must not be shown without its origin.
 */
export interface Attributed<T> {
  readonly value: T;
  readonly provenance: ProvenanceClass;
  readonly evidence?: string;
  readonly confidence?: ConfidenceLevel;
}
