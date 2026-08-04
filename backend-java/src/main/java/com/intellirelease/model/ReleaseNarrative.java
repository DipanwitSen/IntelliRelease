package com.intellirelease.model;

import java.util.Map;

public record ReleaseNarrative(
        String provider,
        String businessSummary,
        String technicalSummary,
        String executiveSummary,
        String riskExplanation,
        String qaGuidance,
        String readinessExplanation,
        Map<String, Object> raw
) {
}