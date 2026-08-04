export interface ReleaseSummary {
  releaseId: string;
  repository: string;
  branch: string;
  prNumber: string;
  title: string;
  author: string;
  riskScore: number;
  riskLevel: string;
  readinessScore: number;
  readinessStatus: string;
  impactedCapabilities: string[];
  recommendedTests: string[];
  configDrift: Record<string, string>;
  businessSummary: string;
  technicalSummary: string;
  createdAt: string;
}

export interface ReleaseNarrative {
  provider: string;
  businessSummary: string;
  technicalSummary: string;
  executiveSummary: string;
  riskExplanation: string;
  qaGuidance: string;
  readinessExplanation: string;
  raw: Record<string, unknown>;
}

export interface GithubWebhookRequest {
  repository: string;
  branch: string;
  mergeSha: string;
  prNumber: string;
  author: string;
  title: string;
  changedFiles: Array<{ path: string; changeType: string }>;
}