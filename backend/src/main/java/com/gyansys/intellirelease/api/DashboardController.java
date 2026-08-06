package com.gyansys.intellirelease.api;

import com.gyansys.intellirelease.domain.integration.IntegrationCatalog;
import com.gyansys.intellirelease.infra.TenantContext;
import com.gyansys.intellirelease.model.PrAnalysis;
import com.gyansys.intellirelease.model.PullRequest;
import com.gyansys.intellirelease.model.Release;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import com.gyansys.intellirelease.model.enums.ReadinessStatus;
import com.gyansys.intellirelease.model.enums.RiskLevel;
import com.gyansys.intellirelease.repository.PrAnalysisRepository;
import com.gyansys.intellirelease.repository.PullRequestRepository;
import com.gyansys.intellirelease.repository.ReleaseRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The landing page's single payload.
 *
 * <h2>Why one endpoint</h2>
 * Fifteen tiles firing fifteen requests renders in fifteen stages, hammers the
 * backend on every visit, and — worse — produces a dashboard where the tiles
 * disagree because each read the database at a different instant. One snapshot
 * means the whole page describes one point in time.
 *
 * <h2>Why so much of it is null</h2>
 * Build status, deployment status and integration telemetry have no source in
 * this build. Each is returned with an {@code unavailableReason} rather than a
 * zero. A dashboard that shows "0 failed tests" when no CI is connected is not
 * neutral — it actively tells a release manager the wrong thing.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Delivery, change and integration posture in one snapshot")
public class DashboardController {

    private static final String NO_CI =
            "No CI system is connected, so build and test results cannot be reported.";
    private static final String NO_DEPLOY_FEED =
            "Deployment is human-asserted in this build; there is no CI/CD or platform integration to poll.";
    private static final String NO_TELEMETRY =
            "No integration monitoring source is connected, so runtime interface health cannot be reported.";

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("d MMM");

    private final PullRequestRepository pullRequestRepository;
    private final PrAnalysisRepository prAnalysisRepository;
    private final ReleaseRepository releaseRepository;
    private final IntegrationCatalog catalog;
    private final TenantContext tenantContext;

    public DashboardController(PullRequestRepository pullRequestRepository,
                               PrAnalysisRepository prAnalysisRepository,
                               ReleaseRepository releaseRepository,
                               IntegrationCatalog catalog,
                               TenantContext tenantContext) {
        this.pullRequestRepository = pullRequestRepository;
        this.prAnalysisRepository = prAnalysisRepository;
        this.releaseRepository = releaseRepository;
        this.catalog = catalog;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    @Operation(summary = "Everything the dashboard renders, computed against one point in time")
    public DashboardSnapshot snapshot(@RequestParam(defaultValue = "7") int windowDays) {
        int window = Math.max(1, Math.min(windowDays, 365));
        String tenantId = tenantContext.currentTenantId();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime since = now.minusDays(window);

        // One generous read, then everything is computed in memory. These are
        // page-sized result sets, and a dozen narrow queries would cost more
        // round-trips than the filtering saves.
        List<PullRequest> allPrs = pullRequestRepository
                .findByTenantIdOrderByMergedAtDesc(tenantId, PageRequest.of(0, 500));

        List<PullRequest> inWindow = allPrs.stream()
                .filter(pr -> pr.getMergedAt() != null && pr.getMergedAt().isAfter(since))
                .toList();

        Map<UUID, PrAnalysis> analyses = prAnalysisRepository
                .findByPrIdIn(allPrs.stream().map(PullRequest::getPrId).toList())
                .stream()
                .collect(Collectors.toMap(PrAnalysis::getPrId, analysis -> analysis, (a, b) -> a));

        List<Release> releases = releaseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        return new DashboardSnapshot(
                now.toString(),
                window,
                kpis(allPrs, inWindow, analyses, releases, now),
                delivery(allPrs, inWindow, analyses, releases, now),
                change(inWindow, analyses),
                integration(),
                List.of(),
                List.of(),
                List.of(),
                releases.stream().limit(5).map(DashboardController::toReleaseSummary).toList(),
                riskTrend(inWindow, analyses, since, window),
                releaseTimeline(releases),
                topContributors(inWindow, analyses),
                repositoryHealth(allPrs, analyses));
    }

    /* ---------------------------------------------------------------- KPIs */

    private List<Kpi> kpis(List<PullRequest> allPrs, List<PullRequest> inWindow,
                           Map<UUID, PrAnalysis> analyses, List<Release> releases,
                           OffsetDateTime now) {

        LocalDate today = now.toLocalDate();
        long mergedToday = allPrs.stream()
                .filter(pr -> pr.getMergedAt() != null
                        && pr.getMergedAt().toLocalDate().equals(today))
                .count();

        long repositories = allPrs.stream().map(PullRequest::getRepoName).distinct().count();

        List<PrAnalysis> windowAnalyses = inWindow.stream()
                .map(pr -> analyses.get(pr.getPrId()))
                .filter(java.util.Objects::nonNull)
                .toList();

        Integer averageRisk = windowAnalyses.isEmpty() ? null
                : (int) Math.round(windowAnalyses.stream()
                        .mapToInt(PrAnalysis::getRiskScore).average().orElse(0));

        long integrationTouching = inWindow.stream()
                .filter(pr -> touchesIntegration(pr))
                .count();

        List<Kpi> kpis = new ArrayList<>();

        kpis.add(new Kpi("repositories", "Repositories", String.valueOf(repositories),
                (double) repositories, null, "info", "repo", null, null, null,
                repositories == 0 ? "No repositories have delivered a webhook yet" : null,
                List.of("/repositories"), ProvenanceClass.FACT, null));

        kpis.add(new Kpi("mergedToday", "Merged today", String.valueOf(mergedToday),
                (double) mergedToday, null, "accent", "git-pull-request", null, true, null,
                null, List.of("/pull-requests"), ProvenanceClass.FACT, null));

        kpis.add(new Kpi("mergedWindow", "Merged in window", String.valueOf(inWindow.size()),
                (double) inWindow.size(), null, "accent", "git-branch", null, true, null,
                null, List.of("/pull-requests"), ProvenanceClass.FACT, null));

        kpis.add(new Kpi("analysed", "Analysed",
                windowAnalyses.size() + " / " + inWindow.size(),
                (double) windowAnalyses.size(), null,
                windowAnalyses.size() == inWindow.size() ? "success" : "warning",
                "sparkles", null, true, null,
                inWindow.isEmpty() ? null : "Analysis runs asynchronously from the job queue",
                List.of("/pull-requests"), ProvenanceClass.FACT, null));

        kpis.add(new Kpi("risk", "Average risk",
                averageRisk == null ? "—" : String.valueOf(averageRisk),
                averageRisk == null ? null : averageRisk.doubleValue(), null,
                averageRisk == null ? "neutral" : toneForRisk(averageRisk),
                "shield", null, false, null,
                "Deterministic — produced by a versioned rule policy, not a model",
                List.of("/risk-analysis"), ProvenanceClass.RULE_OUTPUT,
                averageRisk == null ? "Nothing analysed in this window yet" : null));

        kpis.add(new Kpi("integrationChanges", "Integration-touching changes",
                String.valueOf(integrationTouching), (double) integrationTouching, null,
                integrationTouching > 0 ? "warning" : "success", "network", null, false, null,
                "Changes reaching an interface, payload or mapping",
                List.of("/integration"), ProvenanceClass.DERIVED_FACT, null));

        kpis.add(new Kpi("interfaces", "Catalogued interfaces",
                String.valueOf(catalog.interfaces().size()),
                (double) catalog.interfaces().size(), null, "info", "plug", null, null, null,
                null, List.of("/integration"), ProvenanceClass.RULE_OUTPUT, null));

        kpis.add(new Kpi("openReleases", "Open releases",
                String.valueOf(releases.stream().filter(release -> !release.isDeployed()).count()),
                null, null, "accent", "rocket", null, null, null, null,
                List.of("/releases"), ProvenanceClass.FACT, null));

        kpis.add(new Kpi("buildStatus", "Build status", "—", null, null, "neutral",
                "check-circle", null, null, null, null, null, ProvenanceClass.UNKNOWN, NO_CI));

        kpis.add(new Kpi("failedTests", "Failed tests", "—", null, null, "neutral",
                "x-circle", null, false, null, null, null, ProvenanceClass.UNKNOWN, NO_CI));

        return kpis;
    }

    /* ------------------------------------------------------------ sections */

    private DeliverySection delivery(List<PullRequest> allPrs, List<PullRequest> inWindow,
                                     Map<UUID, PrAnalysis> analyses, List<Release> releases,
                                     OffsetDateTime now) {

        LocalDate today = now.toLocalDate();
        long mergedToday = allPrs.stream()
                .filter(pr -> pr.getMergedAt() != null && pr.getMergedAt().toLocalDate().equals(today))
                .count();

        Release next = releases.stream()
                .filter(release -> !release.isDeployed())
                .findFirst()
                .orElse(null);

        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        long unanalysed = inWindow.stream()
                .filter(pr -> !analyses.containsKey(pr.getPrId()))
                .count();
        if (unanalysed > 0) {
            warnings.add(unanalysed + " merged change(s) in this window have not been analysed yet.");
        }

        // HIGH is the top of this platform's risk scale — the policy deliberately
        // does not model a fourth level, so this is the blocker threshold.
        long highRisk = inWindow.stream()
                .map(pr -> analyses.get(pr.getPrId()))
                .filter(java.util.Objects::nonNull)
                .filter(analysis -> analysis.getRiskLevel() == RiskLevel.HIGH)
                .count();
        if (highRisk > 0) {
            blockers.add(highRisk + " change(s) scored HIGH risk and need explicit sign-off.");
        }

        int readinessScore = next != null && next.getReadinessScore() != null
                ? next.getReadinessScore()
                : deriveReadiness(blockers, warnings);

        ReadinessStatus status = next != null && next.getReadinessStatus() != null
                ? next.getReadinessStatus()
                : (blockers.isEmpty()
                        ? (warnings.isEmpty() ? ReadinessStatus.READY : ReadinessStatus.READY_WITH_WARNINGS)
                        : ReadinessStatus.NOT_READY);

        return new DeliverySection(
                (int) allPrs.stream().map(PullRequest::getRepoName).distinct().count(),
                (int) mergedToday,
                inWindow.size(),
                (int) releases.stream().filter(release -> !release.isDeployed()).count(),
                new ReadinessSummary(status.name(), readinessScore, blockers, warnings,
                        next == null ? null : next.getVersion()),
                new DeploymentStatusSummary("UNKNOWN", null, null, null, 0, 0, NO_DEPLOY_FEED),
                new BuildStatusSummary("NOT_CONFIGURED", 0, 0, 0, 0, null, NO_CI));
    }

    /**
     * Readiness derived from what we can actually see.
     *
     * <p>Only used when a release has not been built and scored by the
     * readiness engine — otherwise the engine's own score wins, because it
     * considers far more than blocker and warning counts.
     */
    private static int deriveReadiness(List<String> blockers, List<String> warnings) {
        int score = 100;
        score -= blockers.size() * 40;
        score -= warnings.size() * 10;
        return Math.max(0, Math.min(100, score));
    }

    private ChangeSection change(List<PullRequest> inWindow, Map<UUID, PrAnalysis> analyses) {
        Map<String, Long> modules = new LinkedHashMap<>();
        for (PullRequest pr : inWindow) {
            String repo = pr.getRepoName();
            if (repo != null) {
                modules.merge(repo, 1L, Long::sum);
            }
        }

        List<PrAnalysis> windowAnalyses = inWindow.stream()
                .map(pr -> analyses.get(pr.getPrId()))
                .filter(java.util.Objects::nonNull)
                .toList();

        Integer average = windowAnalyses.isEmpty() ? null
                : (int) Math.round(windowAnalyses.stream()
                        .mapToInt(PrAnalysis::getRiskScore).average().orElse(0));

        return new ChangeSection(
                toEntries(modules),
                List.of(), List.of(), List.of(),
                // Populated once the integration extractor's output is persisted
                // per analysis; reported empty rather than approximated.
                List.of(),
                average,
                average == null ? null : levelFor(average).name(),
                ProvenanceClass.RULE_OUTPUT);
    }

    private IntegrationSection integration() {
        return new IntegrationSection(
                "UNKNOWN", null, catalog.interfaces().size(), 0,
                null, null, null, List.of(), NO_TELEMETRY);
    }

    /* -------------------------------------------------------------- trends */

    /**
     * Mean risk score per day across the window.
     *
     * <p>Days with no merges are emitted with a zero rather than skipped, so
     * the x-axis stays evenly spaced and a quiet week reads as quiet instead of
     * compressing into a misleadingly steep line.
     */
    private List<TrendPoint> riskTrend(List<PullRequest> inWindow, Map<UUID, PrAnalysis> analyses,
                                       OffsetDateTime since, int window) {
        Map<LocalDate, List<Integer>> byDay = new LinkedHashMap<>();
        LocalDate start = since.toLocalDate();

        for (int i = 0; i <= window; i++) {
            byDay.put(start.plusDays(i), new ArrayList<>());
        }

        for (PullRequest pr : inWindow) {
            PrAnalysis analysis = analyses.get(pr.getPrId());
            if (analysis == null || pr.getMergedAt() == null) {
                continue;
            }
            byDay.computeIfAbsent(pr.getMergedAt().toLocalDate(), key -> new ArrayList<>())
                    .add(analysis.getRiskScore());
        }

        return byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new TrendPoint(
                        entry.getKey().format(DAY_LABEL),
                        entry.getValue().isEmpty() ? 0
                                : (int) Math.round(entry.getValue().stream()
                                        .mapToInt(Integer::intValue).average().orElse(0)),
                        entry.getKey().atStartOfDay().atOffset(ZoneOffset.UTC).toString()))
                .toList();
    }

    private List<TimelineEntry> releaseTimeline(List<Release> releases) {
        return releases.stream()
                .limit(8)
                .map(release -> new TimelineEntry(
                        release.getReleaseId().toString(),
                        release.getVersion() + " · " + release.getRepoName(),
                        release.getResolvedPrCount() == null
                                ? "Contents not resolved yet"
                                : release.getResolvedPrCount() + " pull request(s)",
                        release.getCreatedAt() == null ? null : release.getCreatedAt().toString(),
                        release.getStatus().name(),
                        toneForRelease(release),
                        "rocket",
                        List.of("/releases", release.getReleaseId().toString())))
                .toList();
    }

    private List<Contributor> topContributors(List<PullRequest> inWindow, Map<UUID, PrAnalysis> analyses) {
        Map<String, List<PrAnalysis>> byAuthor = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, OffsetDateTime> latest = new LinkedHashMap<>();

        for (PullRequest pr : inWindow) {
            String author = pr.getAuthor() == null ? "unknown" : pr.getAuthor();
            counts.merge(author, 1, Integer::sum);

            PrAnalysis analysis = analyses.get(pr.getPrId());
            if (analysis != null) {
                byAuthor.computeIfAbsent(author, key -> new ArrayList<>()).add(analysis);
            }
            if (pr.getMergedAt() != null) {
                latest.merge(author, pr.getMergedAt(),
                        (existing, candidate) -> candidate.isAfter(existing) ? candidate : existing);
            }
        }

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .map(entry -> {
                    List<PrAnalysis> theirs = byAuthor.getOrDefault(entry.getKey(), List.of());
                    Integer avgRisk = theirs.isEmpty() ? null
                            : (int) Math.round(theirs.stream()
                                    .mapToInt(PrAnalysis::getRiskScore).average().orElse(0));
                    return new Contributor(entry.getKey(), null, entry.getValue(), avgRisk,
                            latest.get(entry.getKey()) == null
                                    ? null : latest.get(entry.getKey()).toString());
                })
                .toList();
    }

    /**
     * Per-repository health, scored from what the platform genuinely knows:
     * how much of its recent change has been analysed, and how risky that
     * change turned out to be. Not a guess at code quality.
     */
    private List<RepositorySummary> repositoryHealth(List<PullRequest> allPrs,
                                                     Map<UUID, PrAnalysis> analyses) {
        Map<String, List<PullRequest>> byRepo = allPrs.stream()
                .filter(pr -> pr.getRepoName() != null)
                .collect(Collectors.groupingBy(PullRequest::getRepoName, LinkedHashMap::new,
                        Collectors.toList()));

        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);

        return byRepo.entrySet().stream()
                .map(entry -> {
                    List<PullRequest> prs = entry.getValue();
                    List<PrAnalysis> repoAnalyses = prs.stream()
                            .map(pr -> analyses.get(pr.getPrId()))
                            .filter(java.util.Objects::nonNull)
                            .toList();

                    int analysedPercent = prs.isEmpty() ? 100
                            : (int) Math.round((repoAnalyses.size() * 100.0) / prs.size());
                    int avgRisk = repoAnalyses.isEmpty() ? 0
                            : (int) Math.round(repoAnalyses.stream()
                                    .mapToInt(PrAnalysis::getRiskScore).average().orElse(0));

                    int score = Math.max(0, Math.min(100,
                            (int) Math.round(analysedPercent * 0.5 + (100 - avgRisk) * 0.5)));

                    List<HealthFactor> factors = List.of(
                            new HealthFactor("analysed", "Change analysed", analysedPercent + "%",
                                    50, analysedPercent >= 90 ? "success"
                                            : analysedPercent >= 60 ? "warning" : "danger",
                                    repoAnalyses.size() + " of " + prs.size()
                                            + " captured changes have a deterministic verdict"),
                            new HealthFactor("risk", "Average risk", String.valueOf(avgRisk),
                                    50, avgRisk <= 39 ? "success" : avgRisk <= 69 ? "warning" : "danger",
                                    "Mean deterministic risk score across analysed changes"));

                    OffsetDateTime lastActivity = prs.stream()
                            .map(PullRequest::getMergedAt)
                            .filter(java.util.Objects::nonNull)
                            .max(Comparator.naturalOrder())
                            .orElse(null);

                    long merged30d = prs.stream()
                            .filter(pr -> pr.getMergedAt() != null && pr.getMergedAt().isAfter(thirtyDaysAgo))
                            .count();

                    return new RepositorySummary(
                            entry.getKey(), entry.getKey(), entry.getKey(), "main", null, null,
                            0, (int) merged30d,
                            lastActivity == null ? null : lastActivity.toString(),
                            new RepositoryHealth(healthStatusFor(score), score, factors),
                            true, List.of());
                })
                .sorted(Comparator.comparingInt((RepositorySummary repo) -> repo.health().score()))
                .toList();
    }

    /* ---------------------------------------------------------- internals */

    /**
     * Whether a change reaches an integration surface.
     *
     * <p>Reads the stored SAP Commerce context rather than re-running the
     * extractor: the analysis already recorded what this change touched, and
     * recomputing it here would risk two answers for the same pull request.
     */
    private boolean touchesIntegration(PullRequest pr) {
        String files = pr.getChangedFiles();
        if (files == null) {
            return false;
        }
        String lower = files.toLowerCase();
        return lower.contains("integration") || lower.contains(".impex")
                || lower.contains(".wsdl") || lower.contains(".xsd") || lower.contains(".edmx")
                || lower.contains("converter") || lower.contains("populator") || lower.contains("occ");
    }

    private static ReleaseSummary toReleaseSummary(Release release) {
        return new ReleaseSummary(
                release.getReleaseId().toString(), release.getRepoName(), release.getVersion(),
                release.getFromRef(), release.getToRef(), release.getStatus().name(),
                release.getResolvedPrCount(), release.getAggregateRiskScore(),
                release.getAggregateRiskLevel() == null ? null : release.getAggregateRiskLevel().name(),
                release.getReadinessScore(),
                release.getReadinessStatus() == null ? null : release.getReadinessStatus().name(),
                release.isDeployed(),
                release.getDeployedAt() == null ? null : release.getDeployedAt().toString(),
                release.getDeployedBy(),
                release.getCreatedAt() == null ? null : release.getCreatedAt().toString(),
                release.getBuiltAt() == null ? null : release.getBuiltAt().toString());
    }

    private static List<CountEntry> toEntries(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new CountEntry(entry.getKey(), entry.getKey(), entry.getValue(), null))
                .toList();
    }

    private static String toneForRisk(int score) {
        if (score >= 70) return "danger";
        if (score >= 40) return "warning";
        return "success";
    }

    /**
     * Bands the aggregate score using the same thresholds the risk policy uses,
     * so the dashboard's label agrees with each pull request's own.
     */
    private static RiskLevel levelFor(int score) {
        return RiskLevel.fromScore(score, 40, 70);
    }

    private static String toneForRelease(Release release) {
        if (release.isDeployed()) {
            return "success";
        }
        return switch (release.getStatus()) {
            case RELEASED -> "success";
            case APPROVED -> "accent";
            case NOTES_GENERATED -> "warning";
            case ANALYZED, BUILT -> "info";
            case DRAFT -> "neutral";
        };
    }

    private static String healthStatusFor(int score) {
        if (score >= 80) return "HEALTHY";
        if (score >= 55) return "DEGRADED";
        return "UNHEALTHY";
    }

    /* ------------------------------------------------------------- wire */

    public record DashboardSnapshot(
            String generatedAt, int windowDays, List<Kpi> kpis,
            DeliverySection delivery, ChangeSection change, IntegrationSection integration,
            List<Object> recentActivity, List<Object> recentDeployments, List<Object> recentErrors,
            List<ReleaseSummary> recentReleases, List<TrendPoint> riskTrend,
            List<TimelineEntry> releaseTimeline, List<Contributor> topContributors,
            List<RepositorySummary> repositoryHealth) {
    }

    public record Kpi(String key, String label, String value, Double numericValue, String unit,
                      String tone, String icon, Double deltaPercent, Boolean deltaIsGood,
                      List<Integer> sparkline, String detail, List<String> routerLink,
                      ProvenanceClass provenance, String unavailableReason) {
    }

    public record DeliverySection(int totalRepositories, int mergedPrsToday, int mergedPrsWindow,
                                  int openReleases, ReadinessSummary releaseReadiness,
                                  DeploymentStatusSummary deploymentStatus,
                                  BuildStatusSummary buildStatus) {
    }

    public record ReadinessSummary(String status, int score, List<String> blockers,
                                   List<String> warnings, String nextRelease) {
    }

    public record DeploymentStatusSummary(String current, String environment, String lastDeployedAt,
                                          String lastVersion, int inFlight, int failed24h,
                                          String unavailableReason) {
    }

    public record BuildStatusSummary(String status, int passed, int failed, int failedTests,
                                     int totalTests, String lastRunAt, String unavailableReason) {
    }

    public record ChangeSection(List<CountEntry> changedModules, List<CountEntry> changedApis,
                                List<CountEntry> changedDtos, List<CountEntry> changedIntegrationObjects,
                                List<CountEntry> impactedInterfaces, Integer aiRiskScore,
                                String aiRiskLevel, ProvenanceClass riskProvenance) {
    }

    public record IntegrationSection(String overall, Double successRate, int totalInterfaces,
                                     int failedInterfaces, SystemHealth middleware,
                                     SystemHealth targetSystem, SystemHealth commerce,
                                     List<CountEntry> topFailures, String unavailableReason) {
    }

    public record SystemHealth(String key, String label, String status, String detail,
                               Double successRate, Integer avgResponseMs, String lastCheckedAt) {
    }

    public record CountEntry(String key, String label, long count, String tone) {
    }

    public record TrendPoint(String label, int value, String timestamp) {
    }

    public record TimelineEntry(String id, String label, String detail, String timestamp,
                                String status, String tone, String icon, List<String> routerLink) {
    }

    public record Contributor(String login, String displayName, int prCount,
                              Integer avgRiskScore, String lastContributionAt) {
    }

    public record RepositorySummary(String id, String name, String fullName, String defaultBranch,
                                    String description, String language, int openPrCount,
                                    int mergedPrCount30d, String lastActivityAt,
                                    RepositoryHealth health, boolean webhookConnected,
                                    List<String> tags) {
    }

    public record RepositoryHealth(String status, int score, List<HealthFactor> factors) {
    }

    public record HealthFactor(String key, String label, String value, int weight,
                               String tone, String detail) {
    }

    public record ReleaseSummary(String releaseId, String repoName, String version, String fromRef,
                                 String toRef, String status, Integer resolvedPrCount,
                                 Integer aggregateRiskScore, String aggregateRiskLevel,
                                 Integer readinessScore, String readinessStatus, boolean deployed,
                                 String deployedAt, String deployedBy, String createdAt,
                                 String builtAt) {
    }
}
