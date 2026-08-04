from __future__ import annotations

import os
from dataclasses import dataclass
from typing import Any

import httpx


@dataclass
class LLMResult:
    provider: str
    text: str
    raw: dict[str, Any]


class BaseProvider:
    name: str

    def available(self) -> bool:
        return True

    def summarize(self, prompt: str) -> LLMResult:
        raise NotImplementedError


class OpenAIProvider(BaseProvider):
    name = "openai"

    def available(self) -> bool:
        return bool(os.getenv("OPENAI_API_KEY"))

    def summarize(self, prompt: str) -> LLMResult:
        response = httpx.post(
            "https://api.openai.com/v1/chat/completions",
            headers={"Authorization": f"Bearer {os.environ['OPENAI_API_KEY']}", "Content-Type": "application/json"},
            json={
                "model": os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
                "messages": [
                    {"role": "system", "content": "Summarize SAP Commerce release intelligence clearly and concisely."},
                    {"role": "user", "content": prompt},
                ],
                "temperature": 0.2,
            },
            timeout=30,
        )
        response.raise_for_status()
        payload = response.json()
        text = payload["choices"][0]["message"]["content"]
        return LLMResult(self.name, text, payload)


class GroqProvider(BaseProvider):
    name = "groq"

    def available(self) -> bool:
        return bool(os.getenv("GROQ_API_KEY"))

    def summarize(self, prompt: str) -> LLMResult:
        response = httpx.post(
            "https://api.groq.com/openai/v1/chat/completions",
            headers={"Authorization": f"Bearer {os.environ['GROQ_API_KEY']}", "Content-Type": "application/json"},
            json={
                "model": os.getenv("GROQ_MODEL", "llama-3.1-70b-versatile"),
                "messages": [
                    {"role": "system", "content": "Summarize SAP Commerce release intelligence clearly and concisely."},
                    {"role": "user", "content": prompt},
                ],
                "temperature": 0.2,
            },
            timeout=30,
        )
        response.raise_for_status()
        payload = response.json()
        text = payload["choices"][0]["message"]["content"]
        return LLMResult(self.name, text, payload)


class AnthropicProvider(BaseProvider):
    name = "anthropic"

    def available(self) -> bool:
        return bool(os.getenv("ANTHROPIC_API_KEY"))

    def summarize(self, prompt: str) -> LLMResult:
        response = httpx.post(
            "https://api.anthropic.com/v1/messages",
            headers={
                "x-api-key": os.environ["ANTHROPIC_API_KEY"],
                "anthropic-version": os.getenv("ANTHROPIC_VERSION", "2023-06-01"),
                "content-type": "application/json",
            },
            json={
                "model": os.getenv("ANTHROPIC_MODEL", "claude-3-5-sonnet-20240620"),
                "max_tokens": 1024,
                "messages": [{"role": "user", "content": prompt}],
            },
            timeout=30,
        )
        response.raise_for_status()
        payload = response.json()
        text = " ".join(block.get("text", "") for block in payload.get("content", []))
        return LLMResult(self.name, text, payload)


class GeminiProvider(BaseProvider):
    name = "gemini"

    def available(self) -> bool:
        return bool(os.getenv("GEMINI_API_KEY"))

    def summarize(self, prompt: str) -> LLMResult:
        model = os.getenv("GEMINI_MODEL", "gemini-1.5-pro")
        response = httpx.post(
            f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
            params={"key": os.environ["GEMINI_API_KEY"]},
            json={
                "contents": [{"parts": [{"text": prompt}]}],
                "generationConfig": {"temperature": 0.2, "maxOutputTokens": 1024},
            },
            timeout=30,
        )
        response.raise_for_status()
        payload = response.json()
        candidates = payload.get("candidates", [])
        text = candidates[0]["content"]["parts"][0]["text"] if candidates else ""
        return LLMResult(self.name, text, payload)


def build_provider_chain() -> list[BaseProvider]:
    priority = [item.strip().lower() for item in os.getenv("AI_PROVIDER_PRIORITY", "groq,openai,anthropic,gemini").split(",") if item.strip()]
    provider_map: dict[str, BaseProvider] = {
        "groq": GroqProvider(),
        "openai": OpenAIProvider(),
        "anthropic": AnthropicProvider(),
        "gemini": GeminiProvider(),
    }
    chain: list[BaseProvider] = []
    for name in priority:
        provider = provider_map.get(name)
        if provider is not None:
            chain.append(provider)
    return chain