from __future__ import annotations

from .providers import LLMResult, build_provider_chain
from .schemas import ExplainRequest, ExplainResponse


def explain_release(request: ExplainRequest) -> ExplainResponse:
    prompt = build_prompt(request)
    provider_chain = build_provider_chain()

    chosen: LLMResult | None = None
    for provider in provider_chain:
        if not provider.available():
            continue
        try:
            chosen = provider.summarize(prompt)
            break
        except Exception:
            continue

    if chosen is None:
        chosen = LLMResult(
            provider="deterministic",
            text=deterministic_summary(request),
            raw={"fallback": True},
        )

    return ExplainResponse(
        provider=chosen.provider,
        business_summary=build_business_summary(request),
        technical_summary=build_technical_summary(request),
        executive_summary=build_executive_summary(request),
        risk_explanation=build_risk_explanation(request),
        qa_guidance=build_qa_guidance(request),
        readiness_explanation=build_readiness_explanation(request),
        raw={"llm_text": chosen.text, "provider": chosen.provider, **chosen.raw},
    )


def build_prompt(request: ExplainRequest) -> str:
    return (
        f"Release {request.release_id} for {request.repository} on {request.branch}. "
        f"PR {request.pr_number} titled '{request.title}' by {request.author}. "
        f"Risk {request.risk_score}/100, readiness {request.readiness_score}/100, level {request.risk_level}. "
        f"Capabilities: {', '.join(request.impacted_capabilities) or 'none'}. "
        f"Recommended tests: {', '.join(request.recommended_tests) or 'none'}. "
        f"Configuration drift: {', '.join(request.config_drift.keys()) or 'none'}."
    )


def deterministic_summary(request: ExplainRequest) -> str:
    return (
        f"Release {request.release_id} is {request.readiness_score}/100 ready with {request.risk_level} risk. "
        f"Focus QA on {', '.join(request.recommended_tests) or 'smoke testing'} and review {', '.join(request.config_drift.keys()) or 'no configuration drift'}.")


def build_business_summary(request: ExplainRequest) -> str:
    if request.impacted_capabilities:
        return f"Business impact spans {', '.join(request.impacted_capabilities)}."
    return "No high-confidence customer-facing capability impact was detected."


def build_technical_summary(request: ExplainRequest) -> str:
    return request.technical_summary or "No technical summary provided by upstream services."


def build_executive_summary(request: ExplainRequest) -> str:
    return (
        f"Release {request.release_id} is {request.readiness_score}/100 ready. "
        f"Risk is {request.risk_level} at {request.risk_score}/100."
    )


def build_risk_explanation(request: ExplainRequest) -> str:
    details = []
    if request.impacted_capabilities:
        details.append(f"Impacted areas: {', '.join(request.impacted_capabilities)}")
    if request.config_drift:
        details.append(f"Config drift detected in {len(request.config_drift)} item(s)")
    if not details:
        details.append("No major risk amplifiers were detected")
    return "; ".join(details)


def build_qa_guidance(request: ExplainRequest) -> str:
    return "Test: " + ", ".join(request.recommended_tests or ["smoke test"])


def build_readiness_explanation(request: ExplainRequest) -> str:
    if request.readiness_score >= 80:
        return "Release is ready with minimal blockers."
    if request.readiness_score >= 60:
        return "Release is usable but should be reviewed for warnings before approval."
    return "Release needs remediation before approval."