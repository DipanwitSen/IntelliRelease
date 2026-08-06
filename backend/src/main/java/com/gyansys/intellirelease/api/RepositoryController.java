package com.gyansys.intellirelease.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.domain.context.ContextResult;
import com.gyansys.intellirelease.domain.context.FileContext;
import com.gyansys.intellirelease.infra.JsonMapper;
import com.gyansys.intellirelease.infra.TenantContext;
import com.gyansys.intellirelease.model.PrAnalysis;
import com.gyansys.intellirelease.model.PullRequest;
import com.gyansys.intellirelease.model.enums.RiskLevel;
import com.gyansys.intellirelease.repository.PrAnalysisRepository;
import com.gyansys.intellirelease.repository.PullRequestRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Repositories, derived entirely from the pull requests IntelliRelease has
 * actually captured via webhook — there is no separate GitHub repository
 * sync. A repository IntelliRelease has never received a webhook for simply
 * does not appear here, rather than showing as an empty shell.
 */
@RestController
@RequestMapping("/api/v1/repositories")
@Tag(name = "Repositories", description = "Repository health and activity, derived from captured pull requests")
public class RepositoryController {

    private final PullRequestRepository pullRequestRepository;
    private final PrAnalysisRepository prAnalysisRepository;
    private final TenantContext tenantContext;
    private final JsonMapper jsonMapper;

    public RepositoryController(PullRequestRepository pullRequestRepository,
                                PrAnalysisRepository prAnalysisRepository,
                                TenantContext tenantContext,
                                JsonMapper jsonMapper) {
        this.pullRequestRepository = pullRequestRepository;
        this.prAnalysisRepository = prAnalysisRepository;
        this.tenantContext = tenantContext;
        this.jsonMapper = jsonMapper;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HealthFactor(String key, String label, String value, double weight, String tone, String detail) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Health(String status, int score, List<HealthFactor> factors) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Summary(
            String id, String name, String fullName, String defaultBranch, String description, String language,
            int openPrCount, int mergedPrCount30d, OffsetDateTime lastActivityAt,
            Health health, boolean webhookConnected, List<String> tags
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContributorView(String login, String displayName, int prCount,
                                  Double avgRiskScore, OffsetDateTime lastContributionAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TrendPoint(String label, double value, OffsetDateTime timestamp) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CountEntry(String key, String label, int count) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecentPullRequest(
            UUID prId, String repoName, Integer prNumber, String title, String author,
            OffsetDateTime mergedAt, Integer riskScore, RiskLevel riskLevel, boolean analyzed
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Detail(
            String id, String name, String fullName, String defaultBranch, String description, String language,
            int openPrCount, int mergedPrCount30d, OffsetDateTime lastActivityAt,
            Health health, boolean webhookConnected, List<String> tags,
            List<ContributorView> contributors, List<TrendPoint> riskTrend,
            List<RecentPullRequest> recentPullRequests, List<CountEntry> capabilityBreakdown,
            List<String> interfaceIds
    ) {
    }

    @GetMapping
    @Operation(summary = "Repositories IntelliRelease has captured pull requests for, most recently active first")
    public List<Summary> list() {
        String tenantId = tenantContext.currentTenantId();
        return pullRequestRepository.findDistinctRepoNameByTenantId(tenantId).stream()
                .map(repoName -> toSummary(repoName, pullRequestRepository.findByTenantIdAndRepoName(tenantId, repoName)))
                .sorted(Comparator.comparing(
                        (Summary summary) -> summary.lastActivityAt() == null ? OffsetDateTime.MIN : summary.lastActivityAt())
                        .reversed())
                .toList();
    }

    @GetMapping("/{name}")
    @Operation(summary = "One repository's health, contributors, risk trend and recent activity")
    public ResponseEntity<Detail> get(@PathVariable String name) {
        String tenantId = tenantContext.currentTenantId();
        List<PullRequest> prs = pullRequestRepository.findByTenantIdAndRepoName(tenantId, name);
        if (prs.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<UUID, PrAnalysis> analyses = prAnalysisRepository
                .findByPrIdIn(prs.stream().map(PullRequest::getPrId).toList()).stream()
                .collect(Collectors.toMap(PrAnalysis::getPrId, a -> a));

        Summary summary = toSummary(name, prs);

        List<PullRequest> recent = prs.stream()
                .sorted(Comparator.comparing(PullRequest::getMergedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .toList();

        List<RecentPullRequest> recentPullRequests = recent.stream()
                .map(pr -> {
                    PrAnalysis analysis = analyses.get(pr.getPrId());
                    return new RecentPullRequest(pr.getPrId(), pr.getRepoName(), pr.getPrNumber(), pr.getTitle(),
                            pr.getAuthor(), pr.getMergedAt(),
                            analysis == null ? null : analysis.getRiskScore(),
                            analysis == null ? null : analysis.getRiskLevel(),
                            analysis != null);
                })
                .toList();

        List<TrendPoint> riskTrend = recent.stream()
                .filter(pr -> analyses.get(pr.getPrId()) != null && pr.getMergedAt() != null)
                .sorted(Comparator.comparing(PullRequest::getMergedAt))
                .map(pr -> new TrendPoint("#" + pr.getPrNumber(),
                        analyses.get(pr.getPrId()).getRiskScore(), pr.getMergedAt()))
                .toList();

        Map<String, Integer> contributorCounts = new LinkedHashMap<>();
        Map<String, List<Integer>> contributorRisks = new LinkedHashMap<>();
        Map<String, OffsetDateTime> contributorLast = new LinkedHashMap<>();
        for (PullRequest pr : prs) {
            String author = pr.getAuthor() == null ? "unknown" : pr.getAuthor();
            contributorCounts.merge(author, 1, Integer::sum);
            PrAnalysis analysis = analyses.get(pr.getPrId());
            if (analysis != null) {
                contributorRisks.computeIfAbsent(author, k -> new ArrayList<>()).add(analysis.getRiskScore());
            }
            if (pr.getMergedAt() != null
                    && (contributorLast.get(author) == null || pr.getMergedAt().isAfter(contributorLast.get(author)))) {
                contributorLast.put(author, pr.getMergedAt());
            }
        }
        List<ContributorView> contributors = contributorCounts.entrySet().stream()
                .map(entry -> {
                    List<Integer> risks = contributorRisks.get(entry.getKey());
                    Double avgRisk = risks == null || risks.isEmpty() ? null
                            : risks.stream().mapToInt(Integer::intValue).average().orElse(0);
                    return new ContributorView(entry.getKey(), entry.getKey(), entry.getValue(), avgRisk,
                            contributorLast.get(entry.getKey()));
                })
                .sorted(Comparator.comparingInt(ContributorView::prCount).reversed())
                .toList();

        Map<String, Integer> capabilityCounts = new LinkedHashMap<>();
        for (PrAnalysis analysis : analyses.values()) {
            ContextResult context = jsonMapper.fromJson(analysis.getSapCommerceContext(), ContextResult.class);
            if (context == null) {
                continue;
            }
            for (FileContext file : context.files()) {
                if (file.artifactDisplayName() != null) {
                    capabilityCounts.merge(file.artifactDisplayName(), 1, Integer::sum);
                } else if (file.sapCapability() != null) {
                    capabilityCounts.merge(file.sapCapability().getDisplayName(), 1, Integer::sum);
                }
            }
        }
        List<CountEntry> capabilityBreakdown = capabilityCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(entry -> new CountEntry(entry.getKey(), entry.getKey(), entry.getValue()))
                .toList();

        Detail detail = new Detail(
                summary.id(), summary.name(), summary.fullName(), summary.defaultBranch(), summary.description(),
                summary.language(), summary.openPrCount(), summary.mergedPrCount30d(), summary.lastActivityAt(),
                summary.health(), summary.webhookConnected(), summary.tags(),
                contributors, riskTrend, recentPullRequests, capabilityBreakdown,
                List.of() /* interfaceIds: no integration catalogue populated yet */);

        return ResponseEntity.ok(detail);
    }

    private Summary toSummary(String repoName, List<PullRequest> prs) {
        OffsetDateTime cutoff30d = OffsetDateTime.now().minusDays(30);
        long merged30d = prs.stream()
                .filter(pr -> pr.getMergedAt() != null && pr.getMergedAt().isAfter(cutoff30d))
                .count();
        OffsetDateTime lastActivity = prs.stream()
                .map(PullRequest::getMergedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        List<UUID> ids = prs.stream().map(PullRequest::getPrId).toList();
        List<PrAnalysis> analyses = prAnalysisRepository.findByPrIdIn(ids);
        List<Integer> riskScores = analyses.stream().map(PrAnalysis::getRiskScore).toList();

        Health health = computeHealth(riskScores, analyses.size(), prs.size());

        List<String> tags = analyses.stream()
                .map(a -> jsonMapper.fromJson(a.getSapCommerceContext(), ContextResult.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(context -> context.capabilities().stream())
                .filter(cap -> cap.isBusinessCapability())
                .map(cap -> cap.getDisplayName())
                .distinct()
                .limit(6)
                .toList();

        return new Summary(
                repoName, repoName, repoName, "unknown", null, null,
                0 /* open PRs are not tracked — only merges are captured via webhook */,
                (int) merged30d, lastActivity, health, !prs.isEmpty(), tags);
    }

    private Health computeHealth(List<Integer> riskScores, int analyzedCount, int totalCount) {
        if (totalCount == 0) {
            return new Health("UNKNOWN", 0, List.of());
        }
        if (analyzedCount == 0) {
            return new Health("UNKNOWN", 0, List.of(
                    new HealthFactor("analysis_pending", "Analysis pending", "0 of " + totalCount + " analyzed",
                            1.0, "neutral", "No captured pull request has completed analysis yet")));
        }
        double avgRisk = riskScores.stream().mapToInt(Integer::intValue).average().orElse(0);
        int score = (int) Math.round(Math.max(0, 100 - avgRisk));
        String status = score >= 70 ? "HEALTHY" : score >= 40 ? "DEGRADED" : "UNHEALTHY";
        String tone = score >= 70 ? "success" : score >= 40 ? "warning" : "danger";
        List<HealthFactor> factors = List.of(
                new HealthFactor("avg_risk", "Average risk score", String.format("%.0f", avgRisk), 1.0, tone,
                        "Mean deterministic risk score across " + analyzedCount + " analyzed pull requests"),
                new HealthFactor("analysis_coverage", "Analysis coverage",
                        analyzedCount + " of " + totalCount, 0.5,
                        analyzedCount == totalCount ? "success" : "warning",
                        "Captured pull requests that have completed the analysis pipeline"));
        return new Health(status, score, factors);
    }
}
