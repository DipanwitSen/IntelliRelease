export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';
export type ReadinessStatus = 'READY' | 'READY_WITH_WARNINGS' | 'NOT_READY';

/** One changed file's classification. Mirrors FileContext on the backend. */
export interface FileContext {
  filePath: string;
  sapCapability: string;
  classified: boolean;
  confidence: string | null;
  evidence: string;
  artifactType: string | null;
  artifactDisplayName: string | null;
  generalRole: string | null;
  businessCapabilityTags: string[];
  deploymentRiskBand: string | null;
  knowledgeBaseImpact: string[];
  regressionAreas: string[];
}

/** Mirrors ContextResult on the backend. */
export interface SapCommerceContext {
  files: FileContext[];
  capabilities: string[];
  fileCount: number;
  unclassifiedCount: number;
  testsIncluded: boolean;
  libraryVersion: string;
}

/** Mirrors PullRequestController.Summary on the backend. */
export interface PullRequestSummary {
  prId: string;
  repoName: string;
  prNumber: number;
  title: string;
  author: string;
  mergedAt: string;
  riskScore: number | null;
  riskLevel: RiskLevel | null;
  deploymentReadinessScore: number | null;
  deploymentReadinessStatus: ReadinessStatus | null;
  analyzed: boolean;
}

/** Mirrors PullRequestController.Detail on the backend. */
export interface PullRequestDetail extends PullRequestSummary {
  description: string | null;
  branch: string | null;
  ticketKey: string | null;
  mergeSha: string;
  changedFiles: unknown;
  sapCommerceContext: SapCommerceContext | null;
  impactAnalysis: unknown;
  regressionRecommendation: unknown;
  riskReasons: unknown;
  riskPolicyVersion: string | null;
  configurationDrift: unknown;
  aiSummary: {
    technicalSummary: string;
    qaSummary: string;
    businessSummary: string;
    clientSummary: string;
    riskExplanation: string;
    regressionGuidance: string;
    readinessExplanation: string;
    fallback: boolean;
    provider: string;
    model: string;
  } | null;
  aiFallbackUsed: boolean | null;
  modelProvider: string | null;
  modelName: string | null;
  provenanceClass: string | null;
}
