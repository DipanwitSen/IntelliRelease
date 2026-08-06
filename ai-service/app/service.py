"""Orchestration: model attempt -> validate -> one repair retry -> deterministic fallback.

This is the only place that decides fallback=True/False. Every path through it
ends in a fully-populated, schema-valid response — the caller in Spring Boot
never has to guard against a malformed body, only against this service being
unreachable entirely.
"""
from __future__ import annotations

import logging

from . import narrator, providers
from .prompts import (
    ANALYZE_KEYS,
    SYNTHESIZE_KEYS,
    build_analysis_prompt,
    build_repair_prompt,
    build_synthesis_prompt,
)
from .schemas import AnalyzeRequest, AnalyzeResponse, SynthesizeRequest, SynthesizeResponse
from .validation import InvalidModelOutput, parse_json_object, require_changelog_bullets, require_string_keys

log = logging.getLogger("intellirelease.ai")


def _attempt(prompt: str, required_keys: list[str]) -> dict[str, str] | None:
    """One model call plus one repair retry. Returns None on any failure."""
    try:
        raw = providers.generate(prompt)
    except providers.ProviderUnavailable as exc:
        log.warning("%s unavailable: %s", providers.active_provider_name(), exc)
        return None

    try:
        return require_string_keys(parse_json_object(raw), required_keys)
    except InvalidModelOutput as exc:
        log.info("Model output failed validation, retrying once: %s", exc)

    try:
        repaired = providers.generate(build_repair_prompt(prompt, raw, required_keys))
        return require_string_keys(parse_json_object(repaired), required_keys)
    except (providers.ProviderUnavailable, InvalidModelOutput) as exc:
        log.warning("Repair retry failed, falling back to deterministic template: %s", exc)
        return None


def _attempt_synthesis(
    prompt: str, expected_pr_numbers: list[int]
) -> tuple[dict[str, str], list[dict]] | None:
    """Like _attempt, but the release synthesis response also carries a
    changelogBullets list (one object per PR) alongside the flat string
    fields, so both halves — including full PR coverage — have to validate
    before the model's answer is trusted."""

    def parse(text: str) -> tuple[dict[str, str], list[dict]]:
        parsed = parse_json_object(text)
        fields = require_string_keys(parsed, SYNTHESIZE_KEYS)
        bullets = require_changelog_bullets(parsed, expected_pr_numbers)
        return fields, bullets

    try:
        raw = providers.generate(prompt)
    except providers.ProviderUnavailable as exc:
        log.warning("%s unavailable: %s", providers.active_provider_name(), exc)
        return None

    try:
        return parse(raw)
    except InvalidModelOutput as exc:
        log.info("Model output failed validation, retrying once: %s", exc)

    try:
        repaired = providers.generate(
            build_repair_prompt(prompt, raw, SYNTHESIZE_KEYS + ["changelogBullets"]))
        return parse(repaired)
    except (providers.ProviderUnavailable, InvalidModelOutput) as exc:
        log.warning("Repair retry failed, falling back to deterministic template: %s", exc)
        return None


def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    fields = _attempt(build_analysis_prompt(request), ANALYZE_KEYS)
    if fields is None:
        return narrator.narrate_analysis(request)

    return AnalyzeResponse(
        **fields,
        provenanceClass="AI_INFERENCE",
        fallback=False,
        provider=providers.active_provider_name(),
        model=providers.active_model_name(),
        tokensUsed=None,
    )


def synthesize(request: SynthesizeRequest) -> SynthesizeResponse:
    expected_pr_numbers = [pr.prNumber for pr in request.pullRequests]
    result = _attempt_synthesis(build_synthesis_prompt(request), expected_pr_numbers)
    if result is None:
        return narrator.narrate_release(request)
    fields, bullets = result

    return SynthesizeResponse(
        **fields,
        changelogBullets=bullets,
        provenanceClass="AI_INFERENCE",
        fallback=False,
        provider=providers.active_provider_name(),
        model=providers.active_model_name(),
        tokensUsed=None,
    )
