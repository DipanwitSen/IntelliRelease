package com.intellirelease.service;

import com.intellirelease.model.ChangeEvent;
import com.intellirelease.model.ChangeFile;
import com.intellirelease.model.ReleaseSummary;
import com.intellirelease.model.RiskLevel;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class DefaultReleaseService implements ReleaseService {

    private final Map<String, ReleaseSummary> releases = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(1);

    @Override
    public ReleaseSummary ingestGithubWebhook(ChangeEvent event) {
        String releaseId = "release-" + sequence.getAndIncrement();
        List<String> capabilities = detectCapabilities(event.changedFiles());
        int riskScore = calculateRiskScore(event.changedFiles(), capabilities);
        List<String> tests = recommendTests(capabilities);
        Map<String, String> drift = detectDrift(event.changedFiles());
        int readinessScore = calculateReadinessScore(riskScore, drift, tests);
        String readinessStatus = readinessScore >= 80 ? "Ready" : readinessScore >= 60 ? "Ready with Warnings" : "Needs Review";

        ReleaseSummary summary = new ReleaseSummary(
                releaseId,
                event.repository(),
                event.branch(),
                event.prNumber(),
                event.title(),
                event.author(),
                riskScore,
                toRiskLevel(riskScore),
                readinessScore,
                readinessStatus,
                capabilities,
                tests,
                drift,
                buildBusinessSummary(capabilities, event.changedFiles()),
                buildTechnicalSummary(event.changedFiles()),
                Instant.now()
        );
        releases.put(releaseId, summary);
        return summary;
    }

    @Override
    public ReleaseSummary getRelease(String releaseId) {
        ReleaseSummary summary = releases.get(releaseId);
        if (summary == null) {
            throw new ReleaseNotFoundException(releaseId);
        }
        return summary;
    }

    @Override
    public List<ReleaseSummary> listReleases() {
        return releases.values().stream()
                .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                .toList();
    }

    private List<String> detectCapabilities(List<ChangeFile> files) {
        List<String> capabilities = new ArrayList<>();
        for (ChangeFile file : files) {
            String path = file.path().toLowerCase(Locale.ROOT);
            if (path.contains("checkout") || path.contains("cart") || path.contains("payment")) {
                capabilities.add("Checkout");
            }
            if (path.contains("cms") || path.contains("content")) {
                capabilities.add("CMS");
            }
            if (path.contains("solr") || path.contains("search")) {
                capabilities.add("Search");
            }
            if (path.endsWith("items.xml")) {
                capabilities.add("Type System");
            }
            if (path.endsWith(".impex")) {
                capabilities.add("Impex");
            }
            if (path.contains("cronjob")) {
                capabilities.add("CronJob");
            }
            if (path.endsWith(".xml") || path.contains("spring")) {
                capabilities.add("Configuration");
            }
            if (path.contains("occ")) {
                capabilities.add("OCC API");
            }
        }
        return capabilities.stream().distinct().toList();
    }

    private int calculateRiskScore(List<ChangeFile> files, List<String> capabilities) {
        int score = Math.min(25, files.size() * 3);
        if (capabilities.contains("Checkout")) {
            score += 15;
        }
        if (capabilities.contains("Type System")) {
            score += 20;
        }
        if (capabilities.contains("OCC API")) {
            score += 15;
        }
        if (capabilities.contains("Configuration")) {
            score += 8;
        }
        if (files.size() > 10) {
            score += 10;
        }
        return Math.min(100, score);
    }

    private RiskLevel toRiskLevel(int score) {
        if (score >= 80) {
            return RiskLevel.CRITICAL;
        }
        if (score >= 60) {
            return RiskLevel.HIGH;
        }
        if (score >= 30) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private List<String> recommendTests(List<String> capabilities) {
        List<String> tests = new ArrayList<>();
        if (capabilities.contains("Checkout")) {
            tests.add("Guest Checkout");
            tests.add("Registered Checkout");
            tests.add("Payment");
        }
        if (capabilities.contains("CMS")) {
            tests.add("Homepage Rendering");
            tests.add("CMS Preview");
        }
        if (capabilities.contains("Search")) {
            tests.add("Search Index Refresh");
            tests.add("Search Result Ranking");
        }
        if (capabilities.contains("Type System")) {
            tests.add("System Update Validation");
        }
        if (tests.isEmpty()) {
            tests.add("Smoke Test");
        }
        return tests.stream().distinct().toList();
    }

    private Map<String, String> detectDrift(List<ChangeFile> files) {
        Map<String, String> drift = new LinkedHashMap<>();
        for (ChangeFile file : files) {
            String path = file.path().toLowerCase(Locale.ROOT);
            if (path.contains("local.properties") || path.contains("env") || path.endsWith(".properties")) {
                drift.put(file.path(), "Potential environment-specific configuration drift");
            }
        }
        return drift;
    }

    private int calculateReadinessScore(int riskScore, Map<String, String> drift, List<String> tests) {
        int score = 100 - riskScore;
        score -= drift.size() * 5;
        score += Math.min(10, tests.size());
        return Math.max(0, Math.min(100, score));
    }

    private String buildBusinessSummary(List<String> capabilities, List<ChangeFile> files) {
        if (capabilities.isEmpty()) {
            return "Code-only change with no detected SAP Commerce business capability impact.";
        }
        return "Changed " + String.join(", ", capabilities) + " affecting " + files.size() + " files.";
    }

    private String buildTechnicalSummary(List<ChangeFile> files) {
        return files.stream()
                .map(changeFile -> changeFile.path())
                .collect(Collectors.joining(", "));
    }
}