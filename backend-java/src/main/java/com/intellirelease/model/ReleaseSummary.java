package com.intellirelease.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ReleaseSummary(
        String releaseId,
        String repository,
        String branch,
        String prNumber,
        String title,
        String author,
        int riskScore,
        RiskLevel riskLevel,
        int readinessScore,
        String readinessStatus,
        List<String> impactedCapabilities,
        List<String> recommendedTests,
        Map<String, String> configDrift,
        String businessSummary,
        String technicalSummary,
        Instant createdAt
) {
}