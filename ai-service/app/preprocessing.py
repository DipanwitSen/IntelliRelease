"""Deterministic preprocessing applied before anything reaches a model.

This module is the last line of defence for the architectural rule that source
code, diffs and credentials never reach the LLM. Spring already builds a
structured context package containing only paths and classifications — but
"the caller promised" is not a control. This runs on the receiving side, and
what it strips it strips regardless of what the caller intended.

Everything here is pure and deterministic: same input, same output, no model
involved. That matters because these functions decide what a third party gets
to see, and a probabilistic redactor is not a redactor.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field

# ---------------------------------------------------------------------------
# Secret redaction
# ---------------------------------------------------------------------------

# Deliberately broad. A false positive costs a redacted word in a PR title; a
# false negative costs a live credential in a third party's request logs. The
# asymmetry is not close, so these err heavily toward over-matching.
_SECRET_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("github-token", re.compile(r"\bgh[pousr]_[A-Za-z0-9]{16,}\b")),
    ("slack-token", re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{10,}\b")),
    ("openai-key", re.compile(r"\bsk-[A-Za-z0-9]{20,}\b")),
    ("aws-access-key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("bearer-token", re.compile(r"\bBearer\s+[A-Za-z0-9._~+/-]{20,}=*")),
    ("basic-auth-url", re.compile(r"\b[a-z][a-z0-9+.-]*://[^\s/@]+:[^\s/@]+@")),
    (
        "assigned-secret",
        re.compile(
            r"(?i)\b(password|passwd|pwd|secret|token|api[_-]?key|client[_-]?secret|private[_-]?key)"
            r"\s*[=:]\s*\S+"
        ),
    ),
    (
        "private-key-block",
        re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", re.DOTALL),
    ),
    # Long unbroken base64-ish runs are almost never prose and frequently are
    # encoded credentials or embedded binaries.
    ("opaque-blob", re.compile(r"\b[A-Za-z0-9+/]{60,}={0,2}\b")),
]

REDACTED = "[REDACTED]"


def redact(text: str) -> tuple[str, list[str]]:
    """Strip credential-shaped values.

    Returns the cleaned text and the names of the rules that fired, so the
    caller can report *that* something was redacted without echoing the secret
    into a log line — which would defeat the point entirely.
    """
    if not text:
        return "", []

    fired: list[str] = []
    cleaned = text
    for name, pattern in _SECRET_PATTERNS:
        cleaned, count = pattern.subn(REDACTED, cleaned)
        if count:
            fired.append(name)
    return cleaned, fired


# ---------------------------------------------------------------------------
# Source-code detection
# ---------------------------------------------------------------------------

# Signals that a string is source code rather than a path or a classification.
# Any single one is weak; several together are conclusive.
_CODE_SIGNALS: list[re.Pattern[str]] = [
    re.compile(r"^\s*(public|private|protected)\s+(static\s+)?[\w<>\[\], ]+\s+\w+\s*\(", re.MULTILINE),
    re.compile(r"^\s*(import|package)\s+[\w.]+\s*;", re.MULTILINE),
    re.compile(r"^\s*(def|class)\s+\w+\s*[(:]", re.MULTILINE),
    re.compile(r"^\s*(function|const|let|var)\s+\w+\s*[=(]", re.MULTILINE),
    re.compile(r"^[+-]{1,3}\s", re.MULTILINE),          # unified diff body
    re.compile(r"^@@\s-\d+.*\+\d+.*@@", re.MULTILINE),  # diff hunk header
    re.compile(r"\bif\s*\(.+\)\s*\{"),
    re.compile(r"^\s*<\?xml", re.MULTILINE),
]

# Two independent signals, so a PR title mentioning "class" or a path
# containing "import" does not trip the guard on its own.
_CODE_SIGNAL_THRESHOLD = 2


def looks_like_source_code(text: str) -> bool:
    """True when a field appears to contain source code or a diff.

    Used to *reject*, not to clean. If code has reached this service, the
    correct response is to fail loudly rather than quietly strip it and carry
    on — a silent strip hides a pipeline defect that will recur.
    """
    if not text or len(text) < 40:
        return False
    return sum(1 for pattern in _CODE_SIGNALS if pattern.search(text)) >= _CODE_SIGNAL_THRESHOLD


# ---------------------------------------------------------------------------
# Noise removal
# ---------------------------------------------------------------------------

_NOISE_PATH_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("vendor-or-build-output", re.compile(r"(^|/)(node_modules|dist|build|target|out|coverage|__pycache__|\.venv)/")),
    ("dependency-lock", re.compile(r"(package-lock\.json|yarn\.lock|pnpm-lock\.yaml|Gemfile\.lock|poetry\.lock)$")),
    ("generated-code", re.compile(r"(^|/)generated/|\.generated\.")),
    (
        "binary-or-minified-asset",
        re.compile(
            r"\.(min\.js|min\.css|map|png|jpe?g|gif|svg|ico|webp|woff2?|ttf|eot|zip|gz|tar|jar|war|class|pdf|xlsx?)$",
            re.IGNORECASE,
        ),
    ),
]


def classify_noise(path: str) -> str | None:
    """Return why a path carries no analytical signal, or None to keep it."""
    for reason, pattern in _NOISE_PATH_PATTERNS:
        if pattern.search(path):
            return reason
    return None


def filter_paths(paths: list[str]) -> tuple[list[str], dict[str, int]]:
    """Split paths into those worth reasoning about and a tally of exclusions."""
    kept: list[str] = []
    excluded: dict[str, int] = {}

    for path in paths:
        reason = classify_noise(path)
        if reason is None:
            kept.append(path)
        else:
            excluded[reason] = excluded.get(reason, 0) + 1

    return kept, excluded


# ---------------------------------------------------------------------------
# Compression
# ---------------------------------------------------------------------------

def summarise_repeated(paths: list[str], threshold: int = 6) -> list[str]:
    """Collapse large sibling groups into one summary line.

    Forty files under the same directory tell a model exactly what four files
    plus "and 36 more" tell it, at a tenth of the tokens. Below the threshold
    nothing is collapsed, because the individual names still carry signal.
    """
    if len(paths) <= threshold:
        return list(paths)

    grouped: dict[str, list[str]] = {}
    for path in paths:
        directory = path.rsplit("/", 1)[0] if "/" in path else "."
        grouped.setdefault(directory, []).append(path)

    out: list[str] = []
    for directory, members in grouped.items():
        if len(members) <= threshold:
            out.extend(members)
            continue
        shown = members[:threshold]
        out.extend(shown)
        out.append(f"{directory}/… and {len(members) - threshold} more file(s) in this directory")
    return out


def truncate(text: str, limit: int) -> str:
    """Cut to a byte-ish budget, marking the cut so nothing looks complete when it is not."""
    if len(text) <= limit:
        return text
    return text[:limit] + f"\n… truncated, {len(text) - limit} more character(s) omitted"


def estimate_tokens(text: str) -> int:
    """Rough count at four characters per token.

    Deliberately approximate: an exact count needs the model's own tokenizer,
    which this service does not have and should not depend on. Callers use it
    for budgeting, never for billing.
    """
    return max(1, len(text) // 4)


# ---------------------------------------------------------------------------
# The pipeline
# ---------------------------------------------------------------------------

@dataclass
class PreprocessResult:
    """What survived preprocessing, and what it cost."""

    text: str
    redactions: list[str] = field(default_factory=list)
    excluded_paths: dict[str, int] = field(default_factory=dict)
    original_chars: int = 0
    final_chars: int = 0
    estimated_tokens: int = 0
    rejected_reason: str | None = None

    @property
    def accepted(self) -> bool:
        return self.rejected_reason is None


def preprocess_text(text: str, *, max_chars: int = 20_000, allow_code: bool = False) -> PreprocessResult:
    """Redact, check and bound a free-text field before it reaches a model."""
    original = text or ""

    if not allow_code and looks_like_source_code(original):
        return PreprocessResult(
            text="",
            original_chars=len(original),
            rejected_reason=(
                "Field appears to contain source code or a diff. The deterministic pipeline "
                "sends paths and classifications only, so this indicates an upstream defect "
                "rather than an unusual input — failing loudly here keeps the guarantee real."
            ),
        )

    cleaned, fired = redact(original)
    bounded = truncate(cleaned, max_chars)

    return PreprocessResult(
        text=bounded,
        redactions=fired,
        original_chars=len(original),
        final_chars=len(bounded),
        estimated_tokens=estimate_tokens(bounded),
    )


def preprocess_paths(paths: list[str], *, max_paths: int = 400) -> PreprocessResult:
    """Drop noise, collapse repetition and bound the list."""
    kept, excluded = filter_paths(paths or [])
    compressed = summarise_repeated(kept)

    if len(compressed) > max_paths:
        omitted = len(compressed) - max_paths
        compressed = compressed[:max_paths]
        compressed.append(f"… and {omitted} further path(s) omitted to stay within the context budget")

    text = "\n".join(compressed)
    cleaned, fired = redact(text)

    return PreprocessResult(
        text=cleaned,
        redactions=fired,
        excluded_paths=excluded,
        original_chars=len("\n".join(paths or [])),
        final_chars=len(cleaned),
        estimated_tokens=estimate_tokens(cleaned),
    )
