"""Prompt construction.

Every prompt is built entirely from the deterministic facts Spring Boot sent —
never from source code or diffs, because the request schema has nowhere to
carry them. The model is asked to explain a verdict it did not produce and
cannot change; the score, level and status in the prompt are stated as facts,
not questions.
"""
from __future__ import annotations

import json

from .schemas import AnalyzeRequest, SynthesizeRequest

ANALYZE_KEYS = [
    "technicalSummary",
    "qaSummary",
    "businessSummary",
    "clientSummary",
    "riskExplanation",
    "regressionGuidance",
    "readinessExplanation",
]

SYNTHESIZE_KEYS = [
    "developerNote",
    "qaNote",
    "businessNote",
    "clientNote",
    "releaseSummary",
    "knownRisks",
    "knownConsiderations",
    "deploymentRecommendation",
]


def _facts(request) -> str:
    return json.dumps(request.model_dump(exclude_none=True), indent=2)


def build_analysis_prompt(request: AnalyzeRequest) -> str:
    return f"""You are writing release-note prose for a SAP Commerce (Hybris) deployment
platform used by Eli Lilly. You did not compute any of the numbers below — a
deterministic rule engine did, before you were called. Explain them in plain
English for four audiences. Never invent a number, capability, or fact that
is not present in the JSON facts. Never mention source code or file contents
you were not given — you were not given any.

Deterministic facts for pull request #{request.prNumber}:
{_facts(request)}

Respond with ONLY a single JSON object with exactly these string keys, no
other keys, no markdown fences, no commentary outside the JSON:
- technicalSummary: 1-3 sentences for a developer
- qaSummary: 1-2 sentences naming the suggested regression focus
- businessSummary: 1-2 sentences for a non-technical stakeholder
- clientSummary: 1 sentence, external-facing, no internal jargon
- riskExplanation: 1-2 sentences explaining the risk score and its drivers
- regressionGuidance: 1 sentence restating the regression disclaimer
- readinessExplanation: 1-2 sentences explaining the readiness score/status
"""


def build_synthesis_prompt(request: SynthesizeRequest) -> str:
    return f"""You are writing release notes for release {request.version} of a SAP
Commerce (Hybris) platform used by Eli Lilly. You did not compute any of the
numbers below — deterministic rule engines did. Explain them in plain English
for four audiences. Never invent a change, number, or fact that is not
present in the JSON facts. If a change was excluded (e.g. reverted), never
describe it as shipped.

Deterministic facts for this release:
{_facts(request)}

Respond with ONLY a single JSON object with exactly these string keys, no
other keys, no markdown fences, no commentary outside the JSON:
- developerNote: what shipped, referencing PR numbers
- qaNote: suggested regression focus for this release
- businessNote: 1-2 sentences for a non-technical stakeholder
- clientNote: external-facing summary, no internal jargon, no excluded changes
- releaseSummary: one sentence, the release in a nutshell
- knownRisks: 1-2 sentences explaining the aggregate risk
- knownConsiderations: 1 sentence on configuration drift, if any
- deploymentRecommendation: 1-2 sentences explaining the readiness verdict
"""


def build_repair_prompt(original_prompt: str, bad_output: str, required_keys: list[str]) -> str:
    return f"""Your previous response failed validation. It must be a single JSON
object containing exactly these keys as non-empty strings: {", ".join(required_keys)}.
No markdown fences. No text outside the JSON object.

Your previous (invalid) response was:
{bad_output}

Re-answer the original request below, this time returning only valid JSON
with all required keys present and non-empty.

{original_prompt}
"""
