"""Wire schemas for the Spring Boot <-> AI service contract.

Field names are camelCase on purpose: they mirror the Java records in
backend/src/main/java/.../adapters/ai exactly, because Jackson serialises those
records' component names as-is. Every nested fact is optional with a safe
default — this service must be able to narrate whatever subset of the
deterministic pipeline has actually run, not demand a fully-populated payload.

Nothing in this file accepts source code, diffs, or credentials. That is not
an oversight; the deterministic engines never send them, and this schema has
nowhere to put them if they did.
"""
from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class Lenient(BaseModel):
    model_config = ConfigDict(extra="ignore")


# ---------------------------------------------------------------------------
# SAP Commerce Context Engine output
# ---------------------------------------------------------------------------

class ContextResult(Lenient):
    files: list[dict] = Field(default_factory=list)
    capabilities: list[str] = Field(default_factory=list)
    fileCount: int = 0
    unclassifiedCount: int = 0
    testsIncluded: bool = False
    libraryVersion: str | None = None
    provenanceClass: str | None = None


# ---------------------------------------------------------------------------
# Impact Analysis Engine output
# ---------------------------------------------------------------------------

class ImpactItem(Lenient):
    capability: str
    impactType: str | None = None
    confidence: str | None = None
    evidence: str | None = None
    sourceFiles: list[str] = Field(default_factory=list)


class ImpactResult(Lenient):
    confirmedImpact: list[ImpactItem] = Field(default_factory=list)
    potentialImpact: list[ImpactItem] = Field(default_factory=list)
    provenanceClass: str | None = None


# ---------------------------------------------------------------------------
# Deployment Risk Engine output
# ---------------------------------------------------------------------------

class RiskReason(Lenient):
    rule: str | None = None
    label: str
    weight: int
    evidence: str | None = None
    sourceFiles: list[str] = Field(default_factory=list)


class RiskResult(Lenient):
    score: int
    level: str
    reasons: list[RiskReason] = Field(default_factory=list)
    policyVersion: str | None = None
    provenanceClass: str | None = None


# ---------------------------------------------------------------------------
# Regression Recommendation Engine output
# ---------------------------------------------------------------------------

class RegressionSuggestion(Lenient):
    suite: str
    capability: str | None = None
    confidence: str | None = None
    evidence: str | None = None
    sourceFiles: list[str] = Field(default_factory=list)


class RegressionResult(Lenient):
    suggestions: list[RegressionSuggestion] = Field(default_factory=list)
    suiteCount: int = 0
    disclaimer: str | None = None
    provenanceClass: str | None = None


# ---------------------------------------------------------------------------
# Configuration Drift Engine output
# ---------------------------------------------------------------------------

class DriftItem(Lenient):
    settingKey: str
    baselineValue: str | None = None
    currentValue: str | None = None
    classification: str | None = None
    potentialImpact: str | None = None
    evidence: str | None = None


class DriftResult(Lenient):
    baselineEnvironment: str | None = None
    candidateEnvironment: str | None = None
    drifts: list[DriftItem] = Field(default_factory=list)
    materialCount: int = 0
    cosmeticCount: int = 0
    baselineAvailable: bool = False
    provenanceClass: str | None = None


# ---------------------------------------------------------------------------
# Deployment Readiness Engine output
# ---------------------------------------------------------------------------

class ReadinessFactor(Lenient):
    factor: str | None = None
    label: str
    contribution: int
    maximum: int = 0
    blocker: bool = False
    evidence: str | None = None


class ReadinessResult(Lenient):
    score: int
    status: str
    factors: list[ReadinessFactor] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    engineVersion: str | None = None
    provenanceClass: str | None = None


# ---------------------------------------------------------------------------
# POST /analyze
# ---------------------------------------------------------------------------

class AnalyzeRequest(Lenient):
    prNumber: int
    title: str | None = None
    ticketKey: str | None = None
    repoName: str | None = None
    sapCommerceContext: ContextResult | None = None
    impactAnalysis: ImpactResult | None = None
    riskResult: RiskResult | None = None
    regressionSuggestions: RegressionResult | None = None
    configurationDrift: DriftResult | None = None
    deploymentReadiness: ReadinessResult | None = None


class AnalyzeResponse(BaseModel):
    technicalSummary: str
    qaSummary: str
    businessSummary: str
    clientSummary: str
    riskExplanation: str
    regressionGuidance: str
    readinessExplanation: str
    provenanceClass: str
    fallback: bool
    provider: str
    model: str
    tokensUsed: int | None = None


# ---------------------------------------------------------------------------
# POST /synthesize
# ---------------------------------------------------------------------------

class PrSummary(Lenient):
    prNumber: int
    title: str | None = None
    ticketKey: str | None = None
    riskScore: int = 0
    riskLevel: str | None = None
    capabilities: list[str] = Field(default_factory=list)


class ExcludedPr(Lenient):
    prNumber: int | None = None
    sha: str | None = None
    reason: str | None = None
    evidence: str | None = None


class SynthesizeRequest(Lenient):
    version: str
    repoName: str | None = None
    fromRef: str | None = None
    toRef: str | None = None
    includedPrCount: int = 0
    pullRequests: list[PrSummary] = Field(default_factory=list)
    excludedPrs: list[ExcludedPr] = Field(default_factory=list)
    impactAnalysis: ImpactResult | None = None
    aggregateRisk: RiskResult | None = None
    regressionSuggestions: RegressionResult | None = None
    configurationDrift: DriftResult | None = None
    deploymentReadiness: ReadinessResult | None = None


class SynthesizeResponse(BaseModel):
    developerNote: str
    qaNote: str
    businessNote: str
    clientNote: str
    releaseSummary: str
    knownRisks: str
    knownConsiderations: str
    deploymentRecommendation: str
    provenanceClass: str
    fallback: bool
    provider: str
    model: str
    tokensUsed: int | None = None
