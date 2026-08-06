"""LLM provider access.

Groq is used when GROQ_API_KEY is set (a free-tier hosted API — fast,
OpenAI-compatible, no local model server to run). Ollama is the fallback for
fully local/offline use. Whichever is active, this remains the one place the
AI service reaches outside its own process. It has no database handle, no
GitHub token, no SMTP credentials — an HTTP call to a model provider is the
entire blast radius of a compromised model here.
"""
from __future__ import annotations

import os

import httpx

GROQ_API_KEY = os.environ.get("GROQ_API_KEY", "")
GROQ_MODEL = os.environ.get("GROQ_MODEL", "llama-3.1-8b-instant")
GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
GROQ_TIMEOUT_SECONDS = float(os.environ.get("GROQ_TIMEOUT_SECONDS", "20"))

OLLAMA_URL = os.environ.get("OLLAMA_URL", "http://localhost:11434")
OLLAMA_MODEL = os.environ.get("OLLAMA_MODEL", "llama3.1:8b")
OLLAMA_TIMEOUT_SECONDS = float(os.environ.get("OLLAMA_TIMEOUT_SECONDS", "20"))


class ProviderUnavailable(Exception):
    """The active provider could not be reached or did not answer usably."""


def active_provider_name() -> str:
    return "groq" if GROQ_API_KEY else "ollama"


def active_model_name() -> str:
    return GROQ_MODEL if GROQ_API_KEY else OLLAMA_MODEL


def generate(prompt: str) -> str:
    return _generate_groq(prompt) if GROQ_API_KEY else _generate_ollama(prompt)


def is_healthy() -> bool:
    if GROQ_API_KEY:
        try:
            response = httpx.get(
                "https://api.groq.com/openai/v1/models",
                headers={"Authorization": f"Bearer {GROQ_API_KEY}"},
                timeout=5.0,
            )
            return response.status_code == 200
        except httpx.HTTPError:
            return False
    try:
        response = httpx.get(f"{OLLAMA_URL}/api/tags", timeout=3.0)
        return response.status_code == 200
    except httpx.HTTPError:
        return False


def _generate_groq(prompt: str) -> str:
    try:
        response = httpx.post(
            GROQ_URL,
            headers={"Authorization": f"Bearer {GROQ_API_KEY}"},
            json={
                "model": GROQ_MODEL,
                "messages": [{"role": "user", "content": prompt}],
                "temperature": 0.2,
                "response_format": {"type": "json_object"},
            },
            timeout=GROQ_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
    except httpx.HTTPError as exc:
        raise ProviderUnavailable(f"Groq: {exc}") from exc

    body = response.json()
    try:
        text = body["choices"][0]["message"]["content"]
    except (KeyError, IndexError) as exc:
        raise ProviderUnavailable("Groq returned an unexpected response shape") from exc
    if not text:
        raise ProviderUnavailable("Groq returned an empty response")
    return text


def _generate_ollama(prompt: str) -> str:
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
        raise ProviderUnavailable(f"Ollama: {exc}") from exc

    body = response.json()
    text = body.get("response")
    if not text:
        raise ProviderUnavailable("Ollama returned an empty response body")
    return text
