package com.gyansys.intellirelease.api;

import com.gyansys.intellirelease.infra.TenantContext;
import com.gyansys.intellirelease.model.AuditEvent;
import com.gyansys.intellirelease.model.PrAnalysis;
import com.gyansys.intellirelease.model.PullRequest;
import com.gyansys.intellirelease.repository.AuditEventRepository;
import com.gyansys.intellirelease.repository.PrAnalysisRepository;
import com.gyansys.intellirelease.repository.PullRequestRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Repositories, deployments and the audit trail.
 *
 * <p>Repositories are <em>derived</em> rather than stored: this platform learns
 * about a repository by receiving a webhook from it, so the repository list is
 * exactly the set that has delivered a captured change. That is why there is no
 * "add repository" endpoint — there is nothing to add, only something to
 * observe.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Delivery", description = "Repositories, deployment history and the audit trail")
public class DeliveryController {

    private static final String NO_DEPLOY_FEED =
            "Deployment is human-asserted in this build. Confirm a deployment from a release and it "
                    + "will appear here; there is no CI/CD or platform integration to poll.";

    private final PullRequestRepository pullRequestRepository;
    private final PrAnalysisRepository prAnalysisRepository;
    private final AuditEventRepository auditEventRepository;
    private final TenantContext tenantContext;

    public DeliveryController(PullRequestRepository pullRequestRepository,
                              PrAnalysisRepository prAnalysisRepository,
                              AuditEventRepository auditEventRepository,
                              TenantContext tenantContext) {
        this.pullRequestRepository = pullRequestRepository;
        this.prAnalysisRepository = prAnalysisRepository;
        this.auditEventRepository = auditEventRepository;
        this.tenantContext = tenantContext;
    }

    /* -------------------------------------------------------- repositories */

    @GetMapping("/repositories")
    @Operation(summary = "Repositories that have delivered captured changes")
    public List<RepositorySummary> repositories() {
        return buildSummaries().values().stream()
                .sorted(Comparator.comparingInt((RepositorySummary repo) -> repo.health().score()))
                .toList();
    }

    @GetMapping("/repositories/{name}")
    @Operation(summary = "One repository, with contributors, risk trend and recent changes")
    public ResponseEntity<RepositoryDetail> repository(@PathVariable String name) {
        RepositorySummary summary = buildSummaries().get(name);
        if (summary == null) {
            return ResponseEntity.notFound().build();
        }

        List<PullRequest> prs = pullRequestsFor(name);
        Map<UUID, PrAnalysis> analyses = analysesFor(prs);

        return ResponseEntity.ok(new RepositoryDetail(
                summary.id(), summary.name(), summary.fullName(), summary.defaultBranch(),
                summary.description(), summary.language(), summary.openPrCount(),
                summary.mergedPrCount30d(), summary.lastActivityAt(), summary.health(),
                summary.webhookConnected(), summary.tags(),
                contributors(prs, analyses),
                riskTrend(prs, analyses),
                prs.stream().limit(10).map(pr -> toRecent(pr, analyses.get(pr.getPrId()))).toList(),
                List.of(),
                List.of()));
    }

    /* ---------------------------------------------------------- deployments */

    @GetMapping("/deployments")
    @Operation(
            summary = "Deployment history",
            description = """
                    Empty until a deployment is confirmed. Reporting an empty page is the honest
                    answer for a platform with no CI/CD integration; inventing deployment events
                    would make the timeline look convincing and be wrong.
                    """)
    public PageResponse<Object> deployments(
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return PageResponse.slice(List.of(), page, size);
    }

    /* ---------------------------------------------------------------- audit */

    @GetMapping("/audit")
    @Operation(summary = "The append-only record of governed actions")
    public PageResponse<AuditEntry> audit(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String outcome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {

        String term = q == null ? "" : q.trim().toLowerCase();

        List<AuditEntry> matches = auditEventRepository
                .findByTenantIdOrderByTimestampDesc(tenantContext.currentTenantId(), PageRequest.of(0, 1000))
                .stream()
                .filter(event -> term.isEmpty() || searchable(event).contains(term))
                .filter(event -> actor == null || actor.equalsIgnoreCase(event.getActor()))
                .filter(event -> action == null || action.equalsIgnoreCase(event.getAction()))
                .filter(event -> entityType == null || entityType.equalsIgnoreCase(event.getEntityType()))
                .map(DeliveryController::toAuditEntry)
                .filter(entry -> outcome == null || outcome.equalsIgnoreCase(entry.outcome()))
                .toList();

        return PageResponse.slice(matches, page, size);
    }

    /* ------------------------------------------------------------ internals */

    private Map<String, RepositorySummary> buildSummaries() {
        List<PullRequest> allPrs = pullRequestRepository
                .findByTenantIdOrderByMergedAtDesc(tenantContext.currentTenantId(), PageRequest.of(0, 1000));
        Map<UUID, PrAnalysis> analyses = analysesFor(allPrs);

        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);

        Map<String, List<PullRequest>> byRepo = allPrs.stream()
                .filter(pr -> pr.getRepoName() != null)
                .collect(Collectors.groupingBy(PullRequest::getRepoName, LinkedHashMap::new, Collectors.toList()));

        Map<String, RepositorySummary> summaries = new LinkedHashMap<>();
        byRepo.forEach((name, prs) -> {
            List<PrAnalysis> repoAnalyses = prs.stream()
                    .map(pr -> analyses.get(pr.getPrId()))
                    .filter(Objects::nonNull)
                    .toList();

            int analysedPercent = prs.isEmpty() ? 100
                    : (int) Math.round((repoAnalyses.size() * 100.0) / prs.size());
            int avgRisk = repoAnalyses.isEmpty() ? 0
                    : (int) Math.round(repoAnalyses.stream()
                            .mapToInt(PrAnalysis::getRiskScore).average().orElse(0));

            // Half the score is "have we actually looked at this repository's
            // change?", half is "how risky was it?". Both are things the
            // platform genuinely measures — this is not a code-quality guess.
            int score = Math.max(0, Math.min(100,
                    (int) Math.round(analysedPercent * 0.5 + (100 - avgRisk) * 0.5)));

            OffsetDateTime lastActivity = prs.stream()
                    .map(PullRequest::getMergedAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);

            long merged30d = prs.stream()
                    .filter(pr -> pr.getMergedAt() != null && pr.getMergedAt().isAfter(thirtyDaysAgo))
                    .count();

            summaries.put(name, new RepositorySummary(
                    name, name, name, "main", null, null,
                    0, (int) merged30d,
                    lastActivity == null ? null : lastActivity.toString(),
                    new RepositoryHealth(statusFor(score), score, List.of(
                            new HealthFactor("analysed", "Change analysed", analysedPercent + "%", 50,
                                    analysedPercent >= 90 ? "success"
                                            : analysedPercent >= 60 ? "warning" : "danger",
                                    repoAnalyses.size() + " of " + prs.size()
                                            + " captured changes have a deterministic verdict"),
                            new HealthFactor("risk", "Average risk", String.valueOf(avgRisk), 50,
                                    avgRisk <= 39 ? "success" : avgRisk <= 69 ? "warning" : "danger",
                                    "Mean deterministic risk score across analysed changes"))),
                    // A repository is only in this list because a webhook
                    // delivered a change from it, so delivery is a fact.
                    true,
                    List.of()));
        });

        return summaries;
    }

    private List<PullRequest> pullRequestsFor(String repoName) {
        return pullRequestRepository
                .findByTenantIdOrderByMergedAtDesc(tenantContext.currentTenantId(), PageRequest.of(0, 1000))
                .stream()
                .filter(pr -> repoName.equals(pr.getRepoName()))
                .toList();
    }

    private Map<UUID, PrAnalysis> analysesFor(List<PullRequest> prs) {
        return prAnalysisRepository
                .findByPrIdIn(prs.stream().map(PullRequest::getPrId).toList())
                .stream()
                .collect(Collectors.toMap(PrAnalysis::getPrId, analysis -> analysis, (a, b) -> a));
    }

    private static List<Contributor> contributors(List<PullRequest> prs, Map<UUID, PrAnalysis> analyses) {
        Map<String, List<PullRequest>> byAuthor = prs.stream()
                .collect(Collectors.groupingBy(
                        pr -> pr.getAuthor() == null ? "unknown" : pr.getAuthor(),
                        LinkedHashMap::new, Collectors.toList()));

        return byAuthor.entrySet().stream()
                .map(entry -> {
                    List<PrAnalysis> theirs = entry.getValue().stream()
                            .map(pr -> analyses.get(pr.getPrId()))
                            .filter(Objects::nonNull)
                            .toList();
                    Integer avgRisk = theirs.isEmpty() ? null
                            : (int) Math.round(theirs.stream()
                                    .mapToInt(PrAnalysis::getRiskScore).average().orElse(0));
                    OffsetDateTime last = entry.getValue().stream()
                            .map(PullRequest::getMergedAt)
                            .filter(Objects::nonNull)
                            .max(Comparator.naturalOrder())
                            .orElse(null);
                    return new Contributor(entry.getKey(), null, entry.getValue().size(), avgRisk,
                            last == null ? null : last.toString());
                })
                .sorted(Comparator.comparingInt(Contributor::prCount).reversed())
                .limit(10)
                .toList();
    }

    /** Risk per merged change, oldest first, so the chart reads left to right. */
    private static List<TrendPoint> riskTrend(List<PullRequest> prs, Map<UUID, PrAnalysis> analyses) {
        return prs.stream()
                .filter(pr -> analyses.containsKey(pr.getPrId()))
                .sorted(Comparator.comparing(PullRequest::getMergedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(pr -> new TrendPoint(
                        "#" + pr.getPrNumber(),
                        analyses.get(pr.getPrId()).getRiskScore(),
                        pr.getMergedAt() == null ? null : pr.getMergedAt().toString()))
                .toList();
    }

    private static RecentPullRequest toRecent(PullRequest pr, PrAnalysis analysis) {
        return new RecentPullRequest(
                pr.getPrId().toString(), pr.getRepoName(), pr.getPrNumber(), pr.getTitle(),
                pr.getAuthor(), pr.getMergedAt() == null ? null : pr.getMergedAt().toString(),
                analysis == null ? null : analysis.getRiskScore(),
                analysis == null ? null : analysis.getRiskLevel().name(),
                analysis != null);
    }

    private static String searchable(AuditEvent event) {
        return String.join(" ",
                        nullToEmpty(event.getActor()), nullToEmpty(event.getAction()),
                        nullToEmpty(event.getEntityType()), nullToEmpty(event.getEntityId()),
                        nullToEmpty(event.getDetail()))
                .toLowerCase();
    }

    /**
     * Derives an outcome from the action name.
     *
     * <p>The audit table has no outcome column: the writer records what
     * happened, and a blocked action is recorded as its own action rather than
     * as a failed one. This surfaces that distinction without pretending the
     * column exists.
     */
    private static AuditEntry toAuditEntry(AuditEvent event) {
        String action = nullToEmpty(event.getAction()).toUpperCase();
        String outcome = action.contains("DENIED") || action.contains("BLOCKED") || action.contains("REJECT")
                ? "DENIED"
                : action.contains("FAIL") || action.contains("ERROR") ? "FAILURE" : "SUCCESS";

        return new AuditEntry(
                event.getAuditId().toString(),
                event.getTimestamp() == null ? null : event.getTimestamp().toString(),
                event.getActor(), event.getAction(), event.getEntityType(),
                event.getEntityId(), event.getCorrelationId(), outcome, event.getDetail(), null);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String statusFor(int score) {
        if (score >= 80) return "HEALTHY";
        if (score >= 55) return "DEGRADED";
        return "UNHEALTHY";
    }

    /* ------------------------------------------------------------- wire */

    public record RepositorySummary(String id, String name, String fullName, String defaultBranch,
                                    String description, String language, int openPrCount,
                                    int mergedPrCount30d, String lastActivityAt,
                                    RepositoryHealth health, boolean webhookConnected,
                                    List<String> tags) {
    }

    public record RepositoryDetail(String id, String name, String fullName, String defaultBranch,
                                   String description, String language, int openPrCount,
                                   int mergedPrCount30d, String lastActivityAt,
                                   RepositoryHealth health, boolean webhookConnected,
                                   List<String> tags, List<Contributor> contributors,
                                   List<TrendPoint> riskTrend,
                                   List<RecentPullRequest> recentPullRequests,
                                   List<Object> capabilityBreakdown, List<String> interfaceIds) {
    }

    public record RepositoryHealth(String status, int score, List<HealthFactor> factors) {
    }

    public record HealthFactor(String key, String label, String value, int weight,
                               String tone, String detail) {
    }

    public record Contributor(String login, String displayName, int prCount,
                              Integer avgRiskScore, String lastContributionAt) {
    }

    public record TrendPoint(String label, int value, String timestamp) {
    }

    public record RecentPullRequest(String prId, String repoName, Integer prNumber, String title,
                                    String author, String mergedAt, Integer riskScore,
                                    String riskLevel, boolean analyzed) {
    }

    public record AuditEntry(String id, String occurredAt, String actor, String action,
                             String entityType, String entityId, String correlationId,
                             String outcome, String detail, String ipAddress) {
    }
}
