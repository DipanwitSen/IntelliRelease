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
from .validation import InvalidModelOutput, parse_json_object, require_string_keys

log = logging.getLogger("intellirelease.ai")


def _attempt(prompt: str, required_keys: list[str]) -> dict[str, str] | None:
    """One model call plus one repair retry. Returns None on any failure."""
    try:
        raw = providers.generate(prompt)
    except providers.ProviderUnavailable as exc:
        log.warning("Ollama unavailable: %s", exc)
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


def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    fields = _attempt(build_analysis_prompt(request), ANALYZE_KEYS)
    if fields is None:
        return narrator.narrate_analysis(request)

    return AnalyzeResponse(
        **fields,
        provenanceClass="AI_INFERENCE",
        fallback=False,
        provider="ollama",
        model=providers.OLLAMA_MODEL,
        tokensUsed=None,
    )


def synthesize(request: SynthesizeRequest) -> SynthesizeResponse:
    fields = _attempt(build_synthesis_prompt(request), SYNTHESIZE_KEYS)
    if fields is None:
        return narrator.narrate_release(request)

    return SynthesizeResponse(
        **fields,
        provenanceClass="AI_INFERENCE",
        fallback=False,
        provider="ollama",
        model=providers.OLLAMA_MODEL,
        tokensUsed=None,
    )
