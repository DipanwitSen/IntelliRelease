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
    return f"""You are writing IN-DEPTH release notes for release {request.version} of a
SAP Commerce (Hybris) platform used by Eli Lilly. You did not compute any of
the numbers below — deterministic rule engines did. Explain them in plain
English, with real depth, not a one-line summary. Never invent a change,
number, or fact that is not present in the JSON facts. If a change was
excluded (e.g. reverted), never describe it as shipped.

Each pull request below already carries its title, description, the paths of
files it changed (never their content — you were not given any), which SAP
Commerce artifact types those paths represent (e.g. "Type System
Definition", "Facade"), and which business capabilities those artifact types
touch (e.g. "product-and-pricing-model", "customer-organisation-hierarchy").
Use ALL of that, not just the title, to explain each change properly:
- What actually changed, technically.
- What it implies downstream, given the artifact type (e.g. a Type System
  Definition change implies a database schema update and model
  regeneration; a Facade change implies an orchestration-layer behaviour
  change). Reason from the artifact type's known role in SAP Commerce — you
  are allowed general SAP Commerce/Hybris platform knowledge for this, you
  are just never allowed to invent facts about THIS specific change beyond
  what the JSON gives you.
- Which business capability it touches and why that matters to the
  audience reading that particular note.

Depth requirement: write MULTIPLE SENTENCES per note and per changelog
bullet — a short paragraph, not a one-liner. A reader should come away
understanding not just WHAT shipped but WHY it matters and what to watch
for. If the underlying facts are genuinely thin (e.g. a generic title, no
description, no recognised artifact type), say so plainly rather than
padding with filler — do not invent detail just to sound longer.

Deterministic facts for this release:
{_facts(request)}

Respond with ONLY a single JSON object with exactly these keys, no other
keys, no markdown fences, no commentary outside the JSON:
- developerNote: string, 3-5 sentences — what shipped, referencing PR
  numbers, the artifact types touched, and the technical implications
- qaNote: string, 3-4 sentences — suggested regression focus for this
  release AND why each area is at risk given what changed
- businessNote: string, 3-4 sentences for a non-technical stakeholder —
  what changed in terms of business capability and why it matters
- clientNote: string, 2-3 sentences, external-facing, no internal jargon,
  no excluded changes, still substantive rather than a bare summary
- releaseSummary: string, one sentence, the release in a nutshell
- knownRisks: string, 2-3 sentences explaining the aggregate risk and its
  actual drivers
- knownConsiderations: string, 1-2 sentences on configuration drift, if any
- deploymentRecommendation: string, 2-3 sentences explaining the readiness
  verdict and what would need to be true to change it
- changelogBullets: array with EXACTLY one object per pull request listed
  above — every PR number in the facts must appear exactly once, none
  omitted, none invented. Each object:
  - prNumber: the PR number (integer)
  - ticketKey: the ticket key if one was given, else null
  - text: 2-4 sentences — what changed, the artifact type / capability it
    touches, and why it matters. Not a restated title. Not a single
    generic sentence.

Even a small or seemingly minor PR gets its own bullet, with the same
explanatory depth. Do not skip one because it looks trivial — a release
note that omits a shipped change is worse than one with a plain-looking
entry.
"""


def build_repair_prompt(original_prompt: str, bad_output: str, required_keys: list[str]) -> str:
    return f"""Your previous response failed validation. It must be a single JSON
object containing exactly these keys, matching the shape requested below:
{", ".join(required_keys)}. No markdown fences. No text outside the JSON object.

Your previous (invalid) response was:
{bad_output}

Re-answer the original request below, this time returning only valid JSON
with all required keys present and non-empty.

{original_prompt}
"""
