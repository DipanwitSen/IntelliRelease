"""Schema validation for raw model output.

The model is asked for strict JSON but is not trusted to produce it. This
module is the only gate between a model's raw text and a response Spring Boot
will store — it fails closed: anything that doesn't parse, or is missing a
required non-empty string key, is rejected rather than partially accepted.
"""
from __future__ import annotations

import json
import re


class InvalidModelOutput(Exception):
    pass


def _strip_code_fences(text: str) -> str:
    match = re.search(r"```(?:json)?\s*(.*?)\s*```", text, re.DOTALL)
    return match.group(1) if match else text


def parse_json_object(raw_text: str) -> dict:
    candidate = _strip_code_fences(raw_text).strip()
    try:
        parsed = json.loads(candidate)
    except json.JSONDecodeError as exc:
        raise InvalidModelOutput(f"not valid JSON: {exc}") from exc
    if not isinstance(parsed, dict):
        raise InvalidModelOutput("top-level JSON value is not an object")
    return parsed


def require_string_keys(parsed: dict, keys: list[str]) -> dict[str, str]:
    result: dict[str, str] = {}
    for key in keys:
        value = parsed.get(key)
        if not isinstance(value, str) or not value.strip():
            raise InvalidModelOutput(f"missing or empty required key: {key}")
        result[key] = value.strip()
    return result


def require_changelog_bullets(
    parsed: dict, expected_pr_numbers: list[int], key: str = "changelogBullets"
) -> list[dict]:
    """One bullet per PR is a list of objects, not a flat string — validated
    separately from require_string_keys rather than bending that function's
    contract to cover both shapes.

    Coverage is enforced, not just shape: a model that silently drops a
    shipped PR from the changelog produces release notes that under-report
    what went out, the mirror-image failure of announcing something that
    never shipped. Both are treated as invalid output rather than accepted
    and shipped to a reader."""
    value = parsed.get(key)
    if not isinstance(value, list) or not value:
        raise InvalidModelOutput(f"missing or empty required key: {key}")

    bullets: list[dict] = []
    seen_pr_numbers: set[int] = set()
    for item in value:
        if not isinstance(item, dict):
            raise InvalidModelOutput(f"{key} entries must be objects")
        text = item.get("text")
        if not isinstance(text, str) or not text.strip():
            raise InvalidModelOutput(f"{key} entry missing non-empty 'text'")
        pr_number = item.get("prNumber")
        if isinstance(pr_number, int):
            seen_pr_numbers.add(pr_number)
        bullets.append({
            "prNumber": pr_number,
            "ticketKey": item.get("ticketKey"),
            "text": text.strip(),
        })

    missing = [pr for pr in expected_pr_numbers if pr not in seen_pr_numbers]
    if missing:
        raise InvalidModelOutput(
            f"{key} is missing bullet(s) for PR(s) {missing} out of {expected_pr_numbers}")

    return bullets
