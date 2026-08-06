"""Deterministic template narration.

Used whenever the model is unavailable or its output fails schema validation
twice. This is a Python port of the Java-side DeterministicNarrator so that a
reader gets the same quality of prose regardless of which side of the
Spring Boot <-> AI service boundary decided to fall back.

Nothing here is inference: every sentence is built directly from fields the
deterministic engines already produced. Output is always tagged
provenanceClass="RULE_OUTPUT" and fallback=True.
"""
from __future__ import annotations

from .schemas import AnalyzeRequest, AnalyzeResponse, ChangelogBullet, SynthesizeRequest, SynthesizeResponse

# Mirrors SapCapability's displayName() in
# backend/.../model/enums/SapCapability.java. Jackson serialises the enum by
# name (e.g. "CHECKOUT_CAPABILITY"), so this service re-applies the same
# human-readable labels instead of showing raw enum constants in prose.
_CAPABILITY_DISPLAY_NAMES: dict[str, str] = {
    "TYPE_SYSTEM": "Type System Change",
    "SEARCH_CONFIGURATION": "Search Configuration",
    "DATA_IMPORT": "Data Import",
    "SPRING_CONFIGURATION": "Bean Wiring",
    "CONFIGURATION": "Configuration",
    "BUILD_CONFIGURATION": "Build Configuration",
    "BACKGROUND_PROCESSING": "Background Processing",
    "BUSINESS_LOGIC": "Business Logic",
    "API_LAYER": "API Layer",
    "DATA_ACCESS": "Data Access",
    "DATA_TRANSFORMATION": "Data Transformation",
    "CHECKOUT_CAPABILITY": "Checkout Capability",
    "CART_CAPABILITY": "Cart Capability",
    "ORDER_CAPABILITY": "Order Capability",
    "PRODUCT_CAPABILITY": "Product Capability",
    "CUSTOMER_CAPABILITY": "Customer Capability",
    "PRICING_CAPABILITY": "Pricing Capability",
    "PROMOTION_CAPABILITY": "Promotion Capability",
    "INVENTORY_CAPABILITY": "Inventory Capability",
    "DELIVERY_CAPABILITY": "Delivery Capability",
    "PAYMENT": "Payment",
    "SEARCH": "Search",
    "CMS_CAPABILITY": "CMS Capability",
    "OCC_API": "Storefront API (OCC)",
    "SECURITY": "Security",
    "INTEGRATION": "Integration",
    "TEST": "Test",
    "UNCLASSIFIED": "Unclassified",
}


def _display(capability: str) -> str:
    return _CAPABILITY_DISPLAY_NAMES.get(capability, capability)


def _safe(value: str | None, default: str = "(untitled)") -> str:
    return value if value else default


def _risk_sentence(risk) -> str:
    if risk is None:
        return "No risk analysis is available."
    return f"Risk scored {risk.score} ({risk.level})."


def _explain_risk(risk) -> str:
    if risk is None or not risk.reasons:
        return "No risk rules fired for this change."
    breakdown = "; ".join(
        f"{reason.label} +{reason.weight} ({reason.evidence})" for reason in risk.reasons
    )
    return (
        f"Risk {risk.score} {risk.level} under policy {risk.policyVersion}. "
        f"Contributing rules: {breakdown}."
    )


def _explain_readiness(readiness) -> str:
    if readiness is None:
        return "Deployment readiness has not been evaluated for this release."
    factors = ", ".join(
        f"{factor.label} {factor.contribution}" + (f"/{factor.maximum}" if factor.maximum > 0 else "")
        for factor in readiness.factors
    )
    warnings = f" Attention: {' '.join(readiness.warnings)}" if readiness.warnings else ""
    return f"Deployment readiness {readiness.score}/100 — {readiness.status}. Composition: {factors}.{warnings}"


def _describe_drift(drift) -> str:
    if drift is None or not drift.baselineAvailable:
        return "Configuration drift could not be verified: no production baseline was available."
    material = [item for item in drift.drifts if item.classification == "MATERIAL"]
    if not material:
        return "No material configuration drift was detected against the production baseline."
    items = ", ".join(f"{item.settingKey} {item.baselineValue} -> {item.currentValue}" for item in material)
    return f"{len(material)} material configuration drift(s) detected: {items}."


def narrate_analysis(request: AnalyzeRequest) -> AnalyzeResponse:
    risk = request.riskResult
    impact = request.impactAnalysis
    regression = request.regressionSuggestions
    readiness = request.deploymentReadiness

    capabilities = (
        "none identified"
        if impact is None or not impact.confirmedImpact
        else ", ".join(_display(item.capability) for item in impact.confirmedImpact)
    )

    context_note = ""
    if request.sapCommerceContext is not None:
        ctx = request.sapCommerceContext
        context_note = f"{ctx.fileCount} file(s) analysed, {ctx.unclassifiedCount} unclassified. "

    technical = (
        f"PR #{request.prNumber} — {_safe(request.title)}. "
        f"SAP Commerce capabilities directly changed: {capabilities}. "
        f"{context_note}{_risk_sentence(risk)}"
    )

    suites = (
        "no suites suggested"
        if regression is None or not regression.suggestions
        else ", ".join(s.suite for s in regression.suggestions)
    )
    qa = f"Suggested regression focus: {suites}. {regression.disclaimer if regression else ''}"

    potential_note = ""
    if impact is not None and impact.potentialImpact:
        potential = ", ".join(_display(item.capability) for item in impact.potentialImpact)
        potential_note = f"Areas that may also be affected and should be verified: {potential}."
    business = f"This change affects {capabilities}. {_risk_sentence(risk)} {potential_note}"

    client = (
        f"An update was made to {capabilities.lower()}. "
        f"This change has been analysed and risk-assessed before release."
    )

    return AnalyzeResponse(
        technicalSummary=technical,
        qaSummary=qa,
        businessSummary=business,
        clientSummary=client,
        riskExplanation=_explain_risk(risk),
        regressionGuidance=regression.disclaimer if regression else "",
        readinessExplanation=_explain_readiness(readiness),
        provenanceClass="RULE_OUTPUT",
        fallback=True,
        provider="deterministic-template",
        model="none",
        tokensUsed=0,
    )


def narrate_release(request: SynthesizeRequest) -> SynthesizeResponse:
    pr_list = (
        "no changes resolved"
        if not request.pullRequests
        else "; ".join(
            f"#{pr.prNumber} {_safe(pr.title)} (risk {pr.riskScore} {pr.riskLevel})"
            for pr in request.pullRequests
        )
    )

    if not request.excludedPrs:
        excluded = "No changes were excluded from this release."
    else:
        items = "; ".join(
            f"{'#' + str(pr.prNumber) if pr.prNumber is not None else pr.sha} ({pr.reason})"
            for pr in request.excludedPrs
        )
        excluded = f"{len(request.excludedPrs)} change(s) were excluded: {items}."

    drift_line = _describe_drift(request.configurationDrift)

    developer = (
        f"Release {request.version} ({request.fromRef} -> {request.toRef}) contains "
        f"{request.includedPrCount} change(s): {pr_list}. {excluded} {drift_line}"
    )

    suite_names = (
        "none suggested"
        if request.regressionSuggestions is None or not request.regressionSuggestions.suggestions
        else ", ".join(dict.fromkeys(s.suite for s in request.regressionSuggestions.suggestions))
    )
    qa = (
        f"Suggested regression focus for release {request.version}: {suite_names}. "
        f"{request.regressionSuggestions.disclaimer if request.regressionSuggestions else ''}"
    )

    business = (
        f"Release {request.version} delivers {request.includedPrCount} change(s). "
        f"{_risk_sentence(request.aggregateRisk)} {excluded}"
    )

    client = (
        f"Release {request.version} includes {request.includedPrCount} update(s) to the platform. "
        f"{excluded} This summary is generated from the changes that actually shipped."
    )

    changelog = [
        ChangelogBullet(prNumber=pr.prNumber, ticketKey=pr.ticketKey, text=_changelog_text(pr))
        for pr in request.pullRequests
    ]

    return SynthesizeResponse(
        developerNote=developer,
        qaNote=qa,
        businessNote=business,
        clientNote=client,
        releaseSummary=f"Release {request.version}: {request.includedPrCount} change(s) shipped.",
        knownRisks=_explain_risk(request.aggregateRisk),
        knownConsiderations=drift_line,
        deploymentRecommendation=_explain_readiness(request.deploymentReadiness),
        changelogBullets=changelog,
        provenanceClass="RULE_OUTPUT",
        fallback=True,
        provider="deterministic-template",
        model="none",
        tokensUsed=0,
    )


def _changelog_text(pr) -> str:
    """Title first, then the description's first sentence when it adds
    something the title didn't already say — mirrors DeterministicNarrator's
    Java-side fallback so both sides of the boundary produce the same quality
    of prose when the model is unavailable."""
    title = _safe(pr.title)
    description = (pr.description or "").strip()
    if not description or description.lower() == title.lower():
        return title
    first_sentence = description.split(". ")[0].strip()
    if len(first_sentence) > 200:
        first_sentence = first_sentence[:200] + "…"
    return f"{title} — {first_sentence}"
