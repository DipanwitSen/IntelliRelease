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
