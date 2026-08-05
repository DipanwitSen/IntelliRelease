"""LLM provider access. Ollama only, per the fixed POC stack.

This is the one place the AI service reaches outside its own process. It has
no database handle, no GitHub token, no SMTP credentials — an HTTP call to a
local model server is the entire blast radius of a compromised model here.
"""
from __future__ import annotations

import os

import httpx

OLLAMA_URL = os.environ.get("OLLAMA_URL", "http://localhost:11434")
OLLAMA_MODEL = os.environ.get("OLLAMA_MODEL", "llama3.1:8b")
OLLAMA_TIMEOUT_SECONDS = float(os.environ.get("OLLAMA_TIMEOUT_SECONDS", "20"))


class ProviderUnavailable(Exception):
    """Ollama could not be reached or did not answer in time."""


def generate(prompt: str) -> str:
    try:
        response = httpx.post(
            f"{OLLAMA_URL}/api/generate",
            json={
                "model": OLLAMA_MODEL,
                "prompt": prompt,
                "stream": False,
                "format": "json",
                "options": {"temperature": 0.2},
            },
            timeout=OLLAMA_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
    except httpx.HTTPError as exc:
        raise ProviderUnavailable(str(exc)) from exc

    body = response.json()
    text = body.get("response")
    if not text:
        raise ProviderUnavailable("Ollama returned an empty response body")
    return text


def is_healthy() -> bool:
    try:
        response = httpx.get(f"{OLLAMA_URL}/api/tags", timeout=3.0)
        return response.status_code == 200
    except httpx.HTTPError:
        return False
