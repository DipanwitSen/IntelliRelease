package com.gyansys.intellirelease.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.domain.context.ContextResult;
import com.gyansys.intellirelease.domain.context.FileContext;
import com.gyansys.intellirelease.infra.JsonMapper;
import com.gyansys.intellirelease.infra.TenantContext;
import com.gyansys.intellirelease.model.AuditEvent;
import com.gyansys.intellirelease.model.PrAnalysis;
import com.gyansys.intellirelease.model.PullRequest;
import com.gyansys.intellirelease.model.Release;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import com.gyansys.intellirelease.model.enums.ReleaseStatus;
import com.gyansys.intellirelease.model.enums.RiskLevel;
import com.gyansys.intellirelease.model.enums.SapCapability;
import com.gyansys.intellirelease.repository.AuditEventRepository;
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

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The single payload behind the landing page. Every KPI, count and trend here
 * is aggregated from facts and rule-engine output already stored elsewhere —
 * nothing on this endpoint is computed fresh by an engine, and nothing is
 * AI-authored. Sections IntelliRelease has no real data source for yet
 * (build status, integration health, error intelligence) say so explicitly
 * via `unavailableReason` rather than showing fabricated numbers.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "The landing page snapshot, aggregated from stored facts and rule output")
public class DashboardController {

    private static final int RECENT_PR_SCAN_LIMIT = 500;
    private static final int LOW_RISK_THRESHOLD = 30;
    private static final int HIGH_RISK_THRESHOLD = 60;

    private final PullRequestRepository pullRequestRepository;
    private final PrAnalysisRepository prAnalysisRepository;
    private final ReleaseRepository releaseRepository;
    private final AuditEventRepository auditEventRepository;
    private final RepositoryController repositoryController;
    private final TenantContext tenantContext;
    private final JsonMapper jsonMapper;

    public DashboardController(PullRequestRepository pullRequestRepository,
                               PrAnalysisRepository prAnalysisRepository,
                               ReleaseRepository releaseRepository,
                               AuditEventRepository auditEventRepository,
                               RepositoryController repositoryController,
                               TenantContext tenantContext,
                               JsonMapper jsonMapper) {
        this.pullRequestRepository = pullRequestRepository;
        this.prAnalysisRepository = prAnalysisRepository;
        this.releaseRepository = releaseRepository;
        this.auditEventRepository = auditEventRepository;
        this.repositoryController = repositoryController;
        this.tenantContext = tenantContext;
        this.jsonMapper = jsonMapper;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Kpi(String key, String label, String value, Double numericValue, String unit, String tone,
                      String icon, Double deltaPercent, Boolean deltaIsGood, List<Integer> sparkline,
                      String detail, List<String> routerLink, ProvenanceClass provenance, String unavailableReason) {
        static Kpi of(String key, String label, String value, String tone, String icon, ProvenanceClass provenance) {
            return new Kpi(key, label, value, null, null, tone, icon, null, null, null, null, null, provenance, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReadinessSummary(String status, int score, List<String> blockers, List<String> warnings,
                                   String nextRelease) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeploymentStatusSummary(String current, String environment, OffsetDateTime lastDeployedAt,
                                          String lastVersion, int inFlight, int failed24h, String unavailableReason) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BuildStatusSummary(String status, int passed, int failed, int failedTests, int totalTests,
                                     OffsetDateTime lastRunAt, String unavailableReason) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeliverySection(int totalRepositories, long mergedPrsToday, long mergedPrsWindow,
                                  long openReleases, ReadinessSummary releaseReadiness,
                                  DeploymentStatusSummary deploymentStatus, BuildStatusSummary buildStatus) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CountEntry(String key, String label, int count, String tone) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChangeSection(List<CountEntry> changedModules, List<CountEntry> changedApis,
                                List<CountEntry> changedDtos, List<CountEntry> changedIntegrationObjects,
                                List<CountEntry> impactedInterfaces, Double aiRiskScore, RiskLevel aiRiskLevel,
                                ProvenanceClass riskProvenance) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IntegrationSection(String overall, Double successRate, int totalInterfaces, int failedInterfaces,
                                     Object middleware, Object targetSystem, Object commerce,
                                     List<CountEntry> topFailures, String unavailableReason) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TimelineEntry(String id, String label, String detail, OffsetDateTime timestamp, String status,
                                String tone, String icon, List<String> routerLink) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TrendPoint(String label, double value, OffsetDateTime timestamp) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ActivityEvent(String id, String kind, String title, String detail, String actor,
                                OffsetDateTime occurredAt, String entityType, String entityId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Snapshot(OffsetDateTime generatedAt, int windowDays, List<Kpi> kpis, DeliverySection delivery,
                           ChangeSection change, IntegrationSection integration,
                           List<ActivityEvent> recentActivity, List<DeploymentController.Event> recentDeployments,
                           List<Object> recentErrors, List<ReleaseController.View> recentReleases,
                           List<TrendPoint> riskTrend, List<TimelineEntry> releaseTimeline,
                           List<RepositoryController.ContributorView> topContributors,
                           List<RepositoryController.Summary> repositoryHealth) {
    }

    @GetMapping
    @Operation(summary = "The landing page snapshot: KPIs, delivery/change/integration sections, recent activity")
    public Snapshot get(@RequestParam(defaultValue = "7") int windowDays) {
        String tenantId = tenantContext.currentTenantId();
        int safeWindow = windowDays <= 0 ? 7 : windowDays;
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime windowCutoff = now.minusDays(safeWindow);
        OffsetDateTime startOfToday = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());

        List<PullRequest> recentPrs = pullRequestRepository.findByTenantIdOrderByMergedAtDesc(
                tenantId, PageRequest.of(0, RECENT_PR_SCAN_LIMIT));
        Map<UUID, PrAnalysis> analyses = prAnalysisRepository
                .findByPrIdIn(recentPrs.stream().map(PullRequest::getPrId).toList()).stream()
                .collect(Collectors.toMap(PrAnalysis::getPrId, a -> a));

        List<PullRequest> windowPrs = recentPrs.stream()
                .filter(pr -> pr.getMergedAt() != null && pr.getMergedAt().isAfter(windowCutoff))
                .toList();
        long mergedToday = recentPrs.stream()
                .filter(pr -> pr.getMergedAt() != null && pr.getMergedAt().isAfter(startOfToday))
                .count();

        List<Release> releases = releaseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<Release> deployed = releaseRepository
                .findByTenantIdAndDeployedAtIsNotNullOrderByDeployedAtDesc(tenantId);
        long openReleases = releases.stream().filter(r -> r.getStatus() != ReleaseStatus.RELEASED).count();

        int totalRepositories = pullRequestRepository.findDistinctRepoNameByTenantId(tenantId).size();

        DeliverySection delivery = buildDelivery(totalRepositories, mergedToday, windowPrs.size(), openReleases,
                releases, deployed);
        ChangeSection change = buildChange(windowPrs, analyses);
        IntegrationSection integration = new IntegrationSection("NOT_CONFIGURED", null, 0, 0, null, null, null,
                List.of(), "No integration catalogue has been imported for this tenant yet");

        List<ActivityEvent> recentActivity = auditEventRepository
                .findByTenantIdOrderByTimestampDesc(tenantId, PageRequest.of(0, 20)).stream()
                .map(this::toActivityEvent)
                .toList();

        List<DeploymentController.Event> recentDeployments = deployed.stream()
                .limit(10)
                .map(release -> new DeploymentController.Event(
                        release.getReleaseId(), release.getReleaseId(), release.getRepoName(), release.getVersion(),
                        "production", "SUCCEEDED", release.getDeployedAt(), release.getDeployedAt(), 0,
                        release.getDeployedBy(), release.getAggregateRiskLevel(), null, null, null, null, null))
                .toList();

        List<ReleaseController.View> recentReleases = releases.stream()
                .limit(10)
                .map(ReleaseController.View::summary)
                .toList();

        List<TrendPoint> riskTrend = windowPrs.stream()
                .filter(pr -> analyses.get(pr.getPrId()) != null && pr.getMergedAt() != null)
                .sorted(Comparator.comparing(PullRequest::getMergedAt))
                .map(pr -> new TrendPoint("#" + pr.getPrNumber(), analyses.get(pr.getPrId()).getRiskScore(),
                        pr.getMergedAt()))
                .toList();

        List<TimelineEntry> releaseTimeline = releases.stream()
                .limit(10)
                .map(this::toTimelineEntry)
                .toList();

        List<RepositoryController.Summary> repositoryHealth = repositoryController.list();

        List<RepositoryController.ContributorView> topContributors = topContributors(recentPrs, analyses);

        List<Kpi> kpis = buildKpis(totalRepositories, windowPrs.size(), openReleases, change);

        return new Snapshot(now, safeWindow, kpis, delivery, change, integration, recentActivity, recentDeployments,
                List.of(), recentReleases, riskTrend, releaseTimeline, topContributors, repositoryHealth);
    }

    private DeliverySection buildDelivery(int totalRepositories, long mergedToday, long mergedWindow,
                                          long openReleases, List<Release> releases, List<Release> deployed) {
        Release latestBuilt = releases.stream()
                .filter(r -> r.getReadinessScore() != null)
                .findFirst()
                .orElse(null);
        ReadinessSummary readiness = latestBuilt == null
                ? new ReadinessSummary("NOT_READY", 0, List.of("No release has been built yet"), List.of(), null)
                : new ReadinessSummary(
                        latestBuilt.getReadinessStatus() == null ? "NOT_READY" : latestBuilt.getReadinessStatus().name(),
                        latestBuilt.getReadinessScore(), List.of(), List.of(), latestBuilt.getVersion());

        Release lastDeployed = deployed.isEmpty() ? null : deployed.get(0);
        DeploymentStatusSummary deploymentStatus = lastDeployed == null
                ? new DeploymentStatusSummary("UNKNOWN", null, null, null, 0, 0,
                        "No deployment has been confirmed yet")
                : new DeploymentStatusSummary("DEPLOYED", "production", lastDeployed.getDeployedAt(),
                        lastDeployed.getVersion(), 0, 0, null);

        BuildStatusSummary buildStatus = new BuildStatusSummary("NOT_CONFIGURED", 0, 0, 0, 0, null,
                "No CI/build system is integrated yet");

        return new DeliverySection(totalRepositories, mergedToday, mergedWindow, openReleases, readiness,
                deploymentStatus, buildStatus);
    }

    private ChangeSection buildChange(List<PullRequest> windowPrs, Map<UUID, PrAnalysis> analyses) {
        Map<String, Integer> modules = new LinkedHashMap<>();
        Map<String, Integer> apis = new LinkedHashMap<>();
        Map<String, Integer> dtos = new LinkedHashMap<>();
        Map<String, Integer> integrationObjects = new LinkedHashMap<>();
        List<Integer> riskScores = new java.util.ArrayList<>();

        for (PullRequest pr : windowPrs) {
            PrAnalysis analysis = analyses.get(pr.getPrId());
            if (analysis == null) {
                continue;
            }
            riskScores.add(analysis.getRiskScore());
            ContextResult context = jsonMapper.fromJson(analysis.getSapCommerceContext(), ContextResult.class);
            if (context == null) {
                continue;
            }
            for (FileContext file : context.files()) {
                if (file.sapCapability() == null) {
                    continue;
                }
                if (file.sapCapability().isBusinessCapability()) {
                    modules.merge(file.sapCapability().getDisplayName(), 1, Integer::sum);
                }
                if (file.sapCapability() == SapCapability.API_LAYER || file.sapCapability() == SapCapability.OCC_API) {
                    apis.merge(file.sapCapability().getDisplayName(), 1, Integer::sum);
                }
                if (file.sapCapability() == SapCapability.DATA_TRANSFORMATION) {
                    dtos.merge("Converters & populators", 1, Integer::sum);
                }
                if (file.sapCapability() == SapCapability.INTEGRATION) {
                    integrationObjects.merge("Integration interfaces (SAP CPI / ERP)", 1, Integer::sum);
                }
            }
        }

        Double avgRisk = riskScores.isEmpty() ? null
                : riskScores.stream().mapToInt(Integer::intValue).average().orElse(0);
        RiskLevel level = avgRisk == null ? null
                : RiskLevel.fromScore((int) Math.round(avgRisk), LOW_RISK_THRESHOLD, HIGH_RISK_THRESHOLD);

        return new ChangeSection(
                toCountEntries(modules), toCountEntries(apis), toCountEntries(dtos), toCountEntries(integrationObjects),
                List.of(), avgRisk, level, ProvenanceClass.RULE_OUTPUT);
    }

    private List<CountEntry> toCountEntries(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(entry -> new CountEntry(entry.getKey(), entry.getKey(), entry.getValue(), null))
                .toList();
    }

    private List<Kpi> buildKpis(int totalRepositories, long mergedWindow, long openReleases, ChangeSection change) {
        Kpi repoKpi = Kpi.of("total_repositories", "Repositories", String.valueOf(totalRepositories), "neutral",
                "folder-git-2", ProvenanceClass.FACT);
        Kpi mergedKpi = Kpi.of("merged_prs_window", "Merged pull requests", String.valueOf(mergedWindow), "info",
                "git-merge", ProvenanceClass.FACT);
        Kpi openReleaseKpi = Kpi.of("open_releases", "Open releases", String.valueOf(openReleases), "accent",
                "rocket", ProvenanceClass.FACT);
        String riskValue = change.aiRiskScore() == null ? "—" : String.format("%.0f", change.aiRiskScore());
        String riskTone = change.aiRiskLevel() == null ? "neutral"
                : switch (change.aiRiskLevel()) {
                    case HIGH -> "danger";
                    case MEDIUM -> "warning";
                    case LOW -> "success";
                };
        Kpi riskKpi = Kpi.of("avg_risk_score", "Average risk score", riskValue, riskTone, "shield-alert",
                ProvenanceClass.RULE_OUTPUT);
        return List.of(repoKpi, mergedKpi, openReleaseKpi, riskKpi);
    }

    private List<RepositoryController.ContributorView> topContributors(List<PullRequest> prs,
                                                                        Map<UUID, PrAnalysis> analyses) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, List<Integer>> risks = new LinkedHashMap<>();
        Map<String, OffsetDateTime> last = new LinkedHashMap<>();
        for (PullRequest pr : prs) {
            String author = pr.getAuthor() == null ? "unknown" : pr.getAuthor();
            counts.merge(author, 1, Integer::sum);
            PrAnalysis analysis = analyses.get(pr.getPrId());
            if (analysis != null) {
                risks.computeIfAbsent(author, k -> new java.util.ArrayList<>()).add(analysis.getRiskScore());
            }
            if (pr.getMergedAt() != null && (last.get(author) == null || pr.getMergedAt().isAfter(last.get(author)))) {
                last.put(author, pr.getMergedAt());
            }
        }
        return counts.entrySet().stream()
                .map(entry -> {
                    List<Integer> r = risks.get(entry.getKey());
                    Double avg = r == null || r.isEmpty() ? null : r.stream().mapToInt(Integer::intValue).average().orElse(0);
                    return new RepositoryController.ContributorView(entry.getKey(), entry.getKey(), entry.getValue(),
                            avg, last.get(entry.getKey()));
                })
                .sorted(Comparator.comparingInt(RepositoryController.ContributorView::prCount).reversed())
                .limit(10)
                .toList();
    }

    private ActivityEvent toActivityEvent(AuditEvent event) {
        return new ActivityEvent(event.getAuditId().toString(), mapActivityKind(event.getAction()),
                humanizeAction(event.getAction()), event.getDetail(), event.getActor(), event.getTimestamp(),
                event.getEntityType(), event.getEntityId());
    }

    private String mapActivityKind(String action) {
        return switch (action) {
            case "PR_CAPTURED" -> "PR_MERGED";
            case "PR_ANALYZED" -> "PR_ANALYZED";
            case "RELEASE_CREATED" -> "RELEASE_CREATED";
            case "RELEASE_BUILT" -> "RELEASE_BUILT";
            case "RELEASE_APPROVED" -> "RELEASE_APPROVED";
            case "RELEASE_DEPLOYED" -> "DEPLOYMENT";
            case "RELEASE_NOTES_SENT" -> "RELEASE_NOTIFIED";
            default -> action;
        };
    }

    private String humanizeAction(String action) {
        String[] words = action.toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private TimelineEntry toTimelineEntry(Release release) {
        String status = release.getStatus().name();
        String tone = switch (release.getStatus()) {
            case RELEASED -> "success";
            case APPROVED -> "accent";
            case NOTES_GENERATED -> "warning";
            case ANALYZED, BUILT -> "info";
            case DRAFT -> "neutral";
        };
        return new TimelineEntry(release.getReleaseId().toString(),
                release.getRepoName() + " " + release.getVersion(), status,
                release.getBuiltAt() != null ? release.getBuiltAt() : release.getCreatedAt(), status, tone, "rocket",
                List.of("/releases", release.getReleaseId().toString()));
    }
}
