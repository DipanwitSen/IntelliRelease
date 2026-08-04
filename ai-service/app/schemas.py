from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class ExplainRequest(BaseModel):
    release_id: str = Field(min_length=1)
    repository: str = Field(min_length=1)
    branch: str = Field(min_length=1)
    pr_number: str = Field(min_length=1)
    title: str = Field(min_length=1)
    author: str = Field(min_length=1)
    risk_score: int = Field(ge=0, le=100)
    readiness_score: int = Field(ge=0, le=100)
    risk_level: str = Field(min_length=1)
    impacted_capabilities: list[str] = Field(default_factory=list)
    recommended_tests: list[str] = Field(default_factory=list)
    config_drift: dict[str, str] = Field(default_factory=dict)
    business_summary: str = Field(default="")
    technical_summary: str = Field(default="")


class ExplainResponse(BaseModel):
    provider: str
    business_summary: str
    technical_summary: str
    executive_summary: str
    risk_explanation: str
    qa_guidance: str
    readiness_explanation: str
    raw: dict[str, Any] = Field(default_factory=dict)