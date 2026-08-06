import { ConfidenceLevel, ProvenanceClass } from './common';

/**
 * Deployment Strategy Advisor.
 *
 * Mirrors the backend's `DeploymentStrategyResult` / `DeploymentStrategyMatch`
 * exactly (see `domain/deployment/DeploymentStrategyEngine.java`). This is a
 * closed, deterministic decision — ROLLING or MIGRATE, nothing else — decided
 * entirely by a rule engine reading SAP Commerce artifact types. The AI
 * service is never in this picture except to narrate the reasons already
 * listed here; it cannot change `strategy`.
 */
export type DeploymentStrategyType = 'ROLLING' | 'MIGRATE';

/** One changed file's contribution to the decision — evidence, not opinion. */
export interface DeploymentStrategyMatch {
  readonly filePath: string;
  readonly artifactType?: string;
  readonly artifactDisplayName?: string;
  readonly strategy: DeploymentStrategyType;
  readonly priority: number;
  readonly reason?: string;
  readonly recommendedActions: readonly string[];
}

export interface DeploymentStrategyResult {
  readonly strategy: DeploymentStrategyType;
  readonly confidence: ConfidenceLevel;
  readonly reasons: readonly string[];
  readonly recommendedActions: readonly string[];
  readonly matches: readonly DeploymentStrategyMatch[];
  readonly classifiedFileCount: number;
  readonly unclassifiedFileCount: number;
  readonly knowledgeBaseVersion?: string;
  readonly provenanceClass: ProvenanceClass;
}
