package com.gyansys.intellirelease.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.gyansys.intellirelease.application.ContextPackageBuilder;
import com.gyansys.intellirelease.application.PullRequestAnalysisService;
import com.gyansys.intellirelease.domain.integration.IntegrationContextExtractor;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.IntegrationContext;
import com.gyansys.intellirelease.infra.JsonMapper;
import com.gyansys.intellirelease.infra.TenantContext;
import com.gyansys.intellirelease.model.PrAnalysis;
import com.gyansys.intellirelease.model.PullRequest;
import com.gyansys.intellirelease.model.enums.DeploymentStrategyType;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import com.gyansys.intellirelease.model.enums.ReadinessStatus;
import com.gyansys.intellirelease.model.enums.RiskLevel;
import com.gyansys.intellirelease.repository.PrAnalysisRepository;
import com.gyansys.intellirelease.repository.PullRequestRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.PageRequest;
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
import java.util.UUID;

/**
 * Read access to captured pull requests and their deterministic + AI
 * analysis. This is the Knowledge Repository made visible: every field here
 * was written once by {@code IngestionService} or
 * {@code PullRequestAnalysisService} and is never recomputed on read.
 */
@RestController
@RequestMapping("/api/v1/pull-requests")
@Tag(name = "Pull Requests", description = "Captured changes and their deterministic + AI analysis")
public class PullRequestController {

    private final PullRequestRepository pullRequestRepository;
    private final PrAnalysisRepository prAnalysisRepository;
    private final TenantContext tenantContext;
    private final JsonMapper jsonMapper;
    private final ContextPackageBuilder contextPackageBuilder;
    private final IntegrationContextExtractor integrationExtractor;
    private final PullRequestAnalysisService analysisService;

    public PullRequestController(PullRequestRepository pullRequestRepository,
                                 PrAnalysisRepository prAnalysisRepository,
                                 TenantContext tenantContext,
                                 JsonMapper jsonMapper,
                                 ContextPackageBuilder contextPackageBuilder,
                                 IntegrationContextExtractor integrationExtractor,
                                 PullRequestAnalysisService analysisService) {
        this.pullRequestRepository = pullRequestRepository;
        this.prAnalysisRepository = prAnalysisRepository;
        this.tenantContext = tenantContext;
        this.jsonMapper = jsonMapper;
        this.contextPackageBuilder = contextPackageBuilder;
        this.integrationExtractor = integrationExtractor;
        this.analysisService = analysisService;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Summary(
            UUID prId, String repoName, Integer prNumber, String title, String author,
            OffsetDateTime mergedAt, Integer riskScore, RiskLevel riskLevel,
            Integer deploymentReadinessScore, ReadinessStatus deploymentReadinessStatus,
            boolean analyzed,
            /** Headline counts, so the list can show impact without a second request per row. */
            Integer changedFileCount, String ticketKey, boolean integrationTouched,
            /** ROLLING or MIGRATE — see the Deployment Strategy Advisor feature. */
            DeploymentStrategyType deploymentStrategyType
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Detail(
            UUID prId, String repoName, Integer prNumber, String title, String description,
            String author, String branch, String ticketKey, String mergeSha,
            OffsetDateTime mergedAt, JsonNode changedFiles,
            boolean analyzed,
            JsonNode sapCommerceContext,
            JsonNode deploymentStrategy, DeploymentStrategyType deploymentStrategyType,
            JsonNode impactAnalysis, JsonNode regressionRecommendation,
            Integer riskScore, RiskLevel riskLevel, JsonNode riskReasons, String riskPolicyVersion,
            JsonNode configurationDrift,
            /** What this pull request's ImpEx files insert/update/remove, and how those rows link. */
            JsonNode impexAnalysis,
            JsonNode aiSummary, Boolean aiFallbackUsed, String modelProvider, String modelName,
            Integer tokensUsed,
            Integer deploymentReadinessScore, ReadinessStatus deploymentReadinessStatus,
            ProvenanceClass provenanceClass,
            /**
             * Computed on read from the stored file manifest rather than
             * persisted, so an interface added to the catalogue today is
             * reflected against a change captured last week without a backfill.
             */
            IntegrationContext integrationContext,
            /** The human decision on top of {@code deploymentStrategyType} — null until confirmed. */
            DeploymentStrategyType confirmedDeploymentStrategy, String confirmedBy, OffsetDateTime confirmedAt
    ) {
    }

    public record ConfirmStrategyRequest(@NotNull DeploymentStrategyType strategy) {
    }

    @GetMapping
    @Operation(
            summary = "List captured pull requests, most recently merged first",
            description = """
                    Filtering is applied server-side because this is the list that grows without
                    bound — a busy programme merges thousands of pull requests a quarter, and
                    pulling them all into the browser to filter locally stops working long before
                    that.
                    """)
    public PageResponse<Summary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String repo,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) Boolean analyzed,
            @RequestParam(required = false) Boolean integrationOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        String tenantId = tenantContext.currentTenantId();

        // Read a generous window, then filter in memory. The alternative is a
        // dynamic Specification per filter combination, which is a lot of
        // machinery for result sets this size.
        List<PullRequest> pullRequests = pullRequestRepository
                .findByTenantIdOrderByMergedAtDesc(tenantId, PageRequest.of(0, 1000));

        List<UUID> ids = pullRequests.stream().map(PullRequest::getPrId).toList();
        var analyses = prAnalysisRepository.findByPrIdIn(ids).stream()
                .collect(java.util.stream.Collectors.toMap(PrAnalysis::getPrId, a -> a, (a, b) -> a));

        String term = q == null ? "" : q.trim().toLowerCase();

        List<Summary> matches = pullRequests.stream()
                .filter(pr -> term.isEmpty() || matchesTerm(pr, term))
                .filter(pr -> repo == null || repo.equalsIgnoreCase(pr.getRepoName()))
                .filter(pr -> author == null || author.equalsIgnoreCase(pr.getAuthor()))
                .filter(pr -> analyzed == null || analyses.containsKey(pr.getPrId()) == analyzed)
                .filter(pr -> riskLevel == null
                        || (analyses.get(pr.getPrId()) != null
                            && analyses.get(pr.getPrId()).getRiskLevel() == riskLevel))
                .filter(pr -> !Boolean.TRUE.equals(integrationOnly) || touchesIntegration(pr))
                .map(pr -> toSummary(pr, analyses.get(pr.getPrId())))
                .toList();

        return PageResponse.slice(matches, page, size);
    }

    private Summary toSummary(PullRequest pr, PrAnalysis analysis) {
        return new Summary(
                pr.getPrId(), pr.getRepoName(), pr.getPrNumber(), pr.getTitle(), pr.getAuthor(),
                pr.getMergedAt(),
                analysis == null ? null : analysis.getRiskScore(),
                analysis == null ? null : analysis.getRiskLevel(),
                analysis == null ? null : analysis.getDeploymentReadinessScore(),
                analysis == null ? null : analysis.getDeploymentReadinessStatus(),
                analysis != null,
                countChangedFiles(pr),
                pr.getTicketKey(),
                touchesIntegration(pr),
                analysis == null ? null : analysis.getDeploymentStrategyType());
    }

    private static boolean matchesTerm(PullRequest pr, String term) {
        return contains(pr.getTitle(), term)
                || contains(pr.getAuthor(), term)
                || contains(pr.getTicketKey(), term)
                || contains(pr.getRepoName(), term)
                || String.valueOf(pr.getPrNumber()).contains(term);
    }

    private static boolean contains(String value, String term) {
        return value != null && value.toLowerCase().contains(term);
    }

    /**
     * Whether the change reaches an integration surface.
     *
     * <p>A path-level heuristic over the stored file manifest, kept in step with
     * the integration catalogue's discovery rules. It is used only for the list
     * filter — the authoritative per-change answer is the stored integration
     * context on the detail endpoint.
     */
    private boolean touchesIntegration(PullRequest pr) {
        String files = pr.getChangedFiles();
        if (files == null) {
            return false;
        }
        String lower = files.toLowerCase();
        return lower.contains("integration") || lower.contains(".impex") || lower.contains(".wsdl")
                || lower.contains(".xsd") || lower.contains(".edmx") || lower.contains("converter")
                || lower.contains("populator") || lower.contains("contributor") || lower.contains("occ");
    }

    private Integer countChangedFiles(PullRequest pr) {
        JsonNode files = jsonMapper.readTree(pr.getChangedFiles());
        return files != null && files.isArray() ? files.size() : null;
    }

    /**
     * Extracts changed-file paths from the stored manifest.
     *
     * <p>Handles both shapes the manifest can take: a bare array of strings, or
     * an array of objects with a {@code path} field, depending on which Git
     * provider captured it.
     */
    private List<String> pathsOf(PullRequest pr) {
        JsonNode files = jsonMapper.readTree(pr.getChangedFiles());
        if (files == null || !files.isArray()) {
            return List.of();
        }
        List<String> paths = new java.util.ArrayList<>(files.size());
        files.forEach(entry -> {
            JsonNode pathNode = entry.isTextual() ? entry : entry.get("path");
            if (pathNode != null && pathNode.isTextual()) {
                paths.add(pathNode.asText());
            }
        });
        return paths;
    }

    @GetMapping("/{id}/context-package")
    @Operation(
            summary = "Exactly what was handed to the language model",
            description = """
                    The accountability endpoint. Returns the deterministic package verbatim so a
                    reviewer can confirm that no file contents, no diff and no raw GitHub payload
                    ever reached the model — a claim that should be verifiable rather than
                    believed. Credential-shaped values are redacted before packaging.
                    """)
    public ResponseEntity<ContextPackageBuilder.ContextPackage> contextPackage(@PathVariable UUID id) {
        return pullRequestRepository.findById(id)
                .map(pr -> ResponseEntity.ok(
                        contextPackageBuilder.build(pr, prAnalysisRepository.findById(id).orElse(null))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "One pull request's full captured facts plus its deterministic and AI analysis")
    public ResponseEntity<Detail> get(@PathVariable UUID id) {
        return pullRequestRepository.findById(id)
                .map(pr -> ResponseEntity.ok(toDetail(pr, prAnalysisRepository.findById(id).orElse(null))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reanalyze")
    @Operation(
            summary = "Re-run the deterministic pipeline (and AI narration) for one pull request",
            description = """
                    Same engines the ingestion pipeline runs asynchronously after a webhook,
                    triggered synchronously on demand — for a change captured before a rule
                    update, or to retry after the AI service was unavailable the first time.
                    Overwrites the previous pr_analysis row rather than creating a second one.
                    """)
    public ResponseEntity<Detail> reanalyze(@PathVariable UUID id) {
        if (pullRequestRepository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PrAnalysis analysis = analysisService.analyze(id);
        PullRequest pr = pullRequestRepository.findById(id).orElseThrow();
        return ResponseEntity.ok(toDetail(pr, analysis));
    }

    private Detail toDetail(PullRequest pr, PrAnalysis analysis) {
        return new Detail(
                pr.getPrId(), pr.getRepoName(), pr.getPrNumber(), pr.getTitle(), pr.getDescription(),
                pr.getAuthor(), pr.getBranch(), pr.getTicketKey(), pr.getMergeSha(),
                pr.getMergedAt(), jsonMapper.readTree(pr.getChangedFiles()),
                analysis != null,
                analysis == null ? null : jsonMapper.readTree(analysis.getSapCommerceContext()),
                analysis == null ? null : jsonMapper.readTree(analysis.getDeploymentStrategy()),
                analysis == null ? null : analysis.getDeploymentStrategyType(),
                analysis == null ? null : jsonMapper.readTree(analysis.getImpactAnalysis()),
                analysis == null ? null : jsonMapper.readTree(analysis.getRegressionRecommendation()),
                analysis == null ? null : analysis.getRiskScore(),
                analysis == null ? null : analysis.getRiskLevel(),
                analysis == null ? null : jsonMapper.readTree(analysis.getRiskReasons()),
                analysis == null ? null : analysis.getRiskPolicyVersion(),
                analysis == null ? null : jsonMapper.readTree(analysis.getConfigurationDrift()),
                analysis == null ? null : jsonMapper.readTree(analysis.getImpexAnalysis()),
                analysis == null ? null : jsonMapper.readTree(analysis.getAiSummary()),
                analysis == null ? null : analysis.isAiFallbackUsed(),
                analysis == null ? null : analysis.getModelProvider(),
                analysis == null ? null : analysis.getModelName(),
                analysis == null ? null : analysis.getTokensUsed(),
                analysis == null ? null : analysis.getDeploymentReadinessScore(),
                analysis == null ? null : analysis.getDeploymentReadinessStatus(),
                analysis == null ? null : analysis.getProvenanceClass(),
                integrationExtractor.extract(pathsOf(pr)),
                analysis == null ? null : analysis.getConfirmedDeploymentStrategy(),
                analysis == null ? null : analysis.getConfirmedBy(),
                analysis == null ? null : analysis.getConfirmedAt());
    }

    @PostMapping("/{id}/deployment-strategy/confirm")
    @Operation(
            summary = "Confirm a deployment strategy for this pull request",
            description = """
                    The governance gate for the Deployment Strategy Advisor. A human confirms
                    ROLLING or MIGRATE — matching or overriding the engine's recommendation —
                    which refreshes the analysis (regenerating the AI narrative fresh) and
                    stamps who confirmed what and when.
                    """)
    public ResponseEntity<Detail> confirmDeploymentStrategy(
            @PathVariable UUID id, @Valid @RequestBody ConfirmStrategyRequest request, Principal principal) {
        if (pullRequestRepository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String confirmedBy = principal == null ? "unknown" : principal.getName();
        analysisService.confirmDeploymentStrategy(id, request.strategy(), confirmedBy);
        return get(id);
    }
}
