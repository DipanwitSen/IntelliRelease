import { ReadinessStatus, RiskLevel } from './pull-request';

/** Mirrors ReleaseController.PrSummary on the backend. */
export interface ReleasePrSummary {
  prId: string;
  prNumber: number;
  title: string;
  ticketKey: string | null;
  riskScore: number | null;
  riskLevel: RiskLevel | null;
}

/** Mirrors ExcludedChange on the backend. */
export interface ExcludedChange {
  prNumber: number | null;
  sha: string | null;
  title: string | null;
  reason: string;
  evidence: string;
}

/** Mirrors ReleaseController.View on the backend. */
export interface ReleaseView {
  releaseId: string;
  repoName: string;
  version: string;
  fromRef: string;
  toRef: string;
  status: string;
  resolvedPrCount: number | null;
  aggregateRiskScore: number | null;
  aggregateRiskLevel: RiskLevel | null;
  readinessScore: number | null;
  readinessStatus: ReadinessStatus | null;
  deployed: boolean;
  deployedAt: string | null;
  deployedBy: string | null;
  createdAt: string;
  builtAt: string | null;
  pullRequests: ReleasePrSummary[] | null;
  excludedPrs: ExcludedChange[] | null;
}

export interface CreateReleaseRequest {
  repoName: string;
  version: string;
  fromRef: string;
  toRef: string;
}

/** Mirrors ReleaseController.BuildResponse on the backend. */
export interface BuildResponse {
  release: ReleaseView;
  commitsExamined: number;
  gitAvailable: boolean;
  unmatchedPrNumbers: number[];
  resolverNotes: string[];
}

/** Mirrors ReleaseService.ReleaseNotes on the backend. */
export interface ReleaseNotes {
  version: string;
  plainText: string;
  bullets: { prNumber: number | null; ticketKey: string | null; text: string }[];
  fallback: boolean;
  provider: string;
}

/** Mirrors ReleaseController.NotifyResponse on the backend. */
export interface NotifyResponse {
  emails: { audience: string; recipient: string; sent: boolean; relayConfigured: boolean; relayed: boolean }[];
  teamsConfigured: boolean;
  teamsSent: boolean;
  fallback: boolean;
  provider: string;
}
