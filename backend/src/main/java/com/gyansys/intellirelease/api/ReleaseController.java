package com.gyansys.intellirelease.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.application.NotificationService;
import com.gyansys.intellirelease.application.ReleaseService;
import com.gyansys.intellirelease.domain.release.ExcludedChange;
import com.gyansys.intellirelease.infra.JsonMapper;
import com.gyansys.intellirelease.model.PrAnalysis;
import com.gyansys.intellirelease.model.PullRequest;
import com.gyansys.intellirelease.model.Release;
import com.gyansys.intellirelease.model.enums.ReadinessStatus;
import com.gyansys.intellirelease.model.enums.ReleaseStatus;
import com.gyansys.intellirelease.model.enums.RiskLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal release lifecycle: create from Git refs, resolve real contents from
 * the commit graph, view, generate a changelog, and confirm deployment. See
 * {@link ReleaseService} for what this deliberately does not do yet (the
 * aggregate risk/impact pass, AI-synthesised audience notes, notification
 * dispatch).
 */
@RestController
@RequestMapping("/api/v1/releases")
@Tag(name = "Releases", description = "Release records, resolved contents, changelog, and deployment confirmation")
public class ReleaseController {

    private final ReleaseService releaseService;
    private final NotificationService notificationService;
    private final JsonMapper jsonMapper;

    public ReleaseController(ReleaseService releaseService, NotificationService notificationService,
                             JsonMapper jsonMapper) {
        this.releaseService = releaseService;
        this.notificationService = notificationService;
        this.jsonMapper = jsonMapper;
    }

    public record CreateRequest(
            @NotBlank String repoName,
            @NotBlank String version,
            @NotBlank String fromRef,
            @NotBlank String toRef
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PrSummary(
            UUID prId, Integer prNumber, String title, String ticketKey,
            Integer riskScore, RiskLevel riskLevel
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record View(
            UUID releaseId, String repoName, String version, String fromRef, String toRef,
            ReleaseStatus status, Integer resolvedPrCount,
            Integer aggregateRiskScore, RiskLevel aggregateRiskLevel,
            Integer readinessScore, ReadinessStatus readinessStatus,
            boolean deployed, OffsetDateTime deployedAt, String deployedBy,
            OffsetDateTime createdAt, OffsetDateTime builtAt,
            List<PrSummary> pullRequests, List<ExcludedChange> excludedPrs
    ) {
        static View summary(Release release) {
            return new View(
                    release.getReleaseId(), release.getRepoName(), release.getVersion(),
                    release.getFromRef(), release.getToRef(), release.getStatus(), release.getResolvedPrCount(),
                    release.getAggregateRiskScore(), release.getAggregateRiskLevel(),
                    release.getReadinessScore(), release.getReadinessStatus(),
                    release.isDeployed(), release.getDeployedAt(), release.getDeployedBy(),
                    release.getCreatedAt(), release.getBuiltAt(), null, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BuildResponse(
            View release, int commitsExamined, boolean gitAvailable,
            List<Integer> unmatchedPrNumbers, List<String> resolverNotes
    ) {
    }

    @PostMapping
    @Operation(summary = "Create a release from a Git ref range")
    public ResponseEntity<View> create(@RequestBody CreateRequest request) {
        Release created = releaseService.create(
                new ReleaseService.CreateReleaseRequest(
                        request.repoName(), request.version(), request.fromRef(), request.toRef()));
        return ResponseEntity.status(201).body(View.summary(created));
    }

    @GetMapping
    @Operation(summary = "Page of releases for the current tenant, most recently created first")
    public PageResponse<View> list(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        int safeSize = size <= 0 ? 50 : Math.min(size, 200);
        var result = releaseService.list(PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")));
        List<View> items = result.getContent().stream().map(View::summary).toList();
        return PageResponse.of(items, result.getTotalElements(), page, safeSize);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One release's current state, including resolved pull requests")
    public ResponseEntity<View> get(@PathVariable UUID id) {
        return releaseService.get(id)
                .map(release -> ResponseEntity.ok(detail(release)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/build")
    @Operation(
            summary = "Resolve this release's true contents from the Git commit graph",
            description = """
                    Diffs fromRef..toRef (cherry-pick aware, revert-netted) and attaches
                    every resolved pull request IntelliRelease has actually captured via
                    webhook. A resolved PR that was never captured is reported in
                    unmatchedPrNumbers rather than fabricated.
                    """)
    public ResponseEntity<BuildResponse> build(@PathVariable UUID id) {
        return releaseService.build(id)
                .map(result -> ResponseEntity.ok(new BuildResponse(
                        detail(result.release()), result.commitsExamined(), result.gitAvailable(),
                        result.unmatchedPrNumbers(), result.resolverNotes())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/notes")
    @Operation(
            summary = "Deterministic changelog for this release",
            description = "One bullet per included pull request, from the PR's own title. Not AI-generated.")
    public ResponseEntity<ReleaseService.ReleaseNotes> notes(@PathVariable UUID id) {
        return releaseService.generateNotes(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/deploy")
    @Operation(
            summary = "Confirm this release is actually running in the target environment",
            description = """
                    Human-asserted, not detected — there is no CI/CD or SAP Commerce
                    Cloud integration behind this yet. Deliberately independent of the
                    release's notification status: RELEASED means communications went
                    out, not that the code is live.
                    """)
    public ResponseEntity<View> markDeployed(@PathVariable UUID id, Principal principal) {
        String deployedBy = principal == null ? "unknown" : principal.getName();
        return releaseService.markDeployed(id, deployedBy)
                .map(release -> ResponseEntity.ok(View.summary(release)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/approve")
    @Operation(
            summary = "Approve this release for client-facing communication",
            description = """
                    The governance gate. Nothing in this system can send an email or a
                    Teams post for a release that has not passed through here — see
                    NotificationService.
                    """)
    public ResponseEntity<View> approve(@PathVariable UUID id, Principal principal) {
        String approvedBy = principal == null ? "unknown" : principal.getName();
        return releaseService.approve(id, approvedBy)
                .map(release -> ResponseEntity.ok(View.summary(release)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NotifyResponse(
            List<NotificationService.EmailOutcome> emails,
            boolean teamsConfigured, boolean teamsSent,
            boolean fallback, String provider
    ) {
    }

    @PostMapping("/{id}/notify")
    @Operation(
            summary = "Send the release notes: one email per audience distribution list, one Teams post",
            description = """
                    Returns 409 APPROVAL_REQUIRED if the release has not been approved.
                    Content is the same AI synthesis the notes preview endpoint returns —
                    never independently regenerated, so what a release manager previewed
                    is exactly what goes out.
                    """)
    public ResponseEntity<NotifyResponse> notify(@PathVariable UUID id, Principal principal) {
        String actor = principal == null ? "unknown" : principal.getName();
        return notificationService.sendReleaseNotes(id, actor)
                .map(result -> ResponseEntity.ok(new NotifyResponse(
                        result.emails(), result.teamsConfigured(), result.teamsSent(),
                        result.fallback(), result.provider())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private View detail(Release release) {
        View base = View.summary(release);
        Map<UUID, PrAnalysis> analyses = releaseService.analysesFor(release.getPullRequests());

        List<PrSummary> prSummaries = release.getPullRequests().stream()
                .map(pr -> toSummary(pr, analyses.get(pr.getPrId())))
                .sorted((left, right) -> Integer.compare(
                        left.prNumber() == null ? 0 : left.prNumber(),
                        right.prNumber() == null ? 0 : right.prNumber()))
                .toList();

        List<ExcludedChange> excluded = jsonMapper.fromJson(release.getExcludedPrs(),
                new com.fasterxml.jackson.core.type.TypeReference<List<ExcludedChange>>() {
                });

        return new View(
                base.releaseId(), base.repoName(), base.version(), base.fromRef(), base.toRef(),
                base.status(), base.resolvedPrCount(), base.aggregateRiskScore(), base.aggregateRiskLevel(),
                base.readinessScore(), base.readinessStatus(), base.deployed(), base.deployedAt(),
                base.deployedBy(), base.createdAt(), base.builtAt(),
                prSummaries.isEmpty() ? null : prSummaries,
                excluded == null || excluded.isEmpty() ? null : excluded);
    }

    private static PrSummary toSummary(PullRequest pr, PrAnalysis analysis) {
        return new PrSummary(
                pr.getPrId(), pr.getPrNumber(), pr.getTitle(), pr.getTicketKey(),
                analysis == null ? null : analysis.getRiskScore(),
                analysis == null ? null : analysis.getRiskLevel());
    }
}
