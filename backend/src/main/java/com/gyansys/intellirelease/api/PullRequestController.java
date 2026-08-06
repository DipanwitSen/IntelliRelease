package com.gyansys.intellirelease.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.gyansys.intellirelease.application.PullRequestAnalysisService;
import com.gyansys.intellirelease.infra.JsonMapper;
import com.gyansys.intellirelease.infra.TenantContext;
import com.gyansys.intellirelease.model.PrAnalysis;
import com.gyansys.intellirelease.model.PullRequest;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import com.gyansys.intellirelease.model.enums.ReadinessStatus;
import com.gyansys.intellirelease.model.enums.RiskLevel;
import com.gyansys.intellirelease.repository.PrAnalysisRepository;
import com.gyansys.intellirelease.repository.PullRequestRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access to captured pull requests and their deterministic + AI
 * analysis. This is the Knowledge Repository made visible: every field here
 * was written once by {@code IngestionService} or
 * {@code PullRequestAnalysisService} and is never recomputed on read, except
 * {@link #reanalyze} which deliberately re-runs the whole pipeline.
 */
@RestController
@RequestMapping("/api/v1/pull-requests")
@Tag(name = "Pull Requests", description = "Captured changes and their deterministic + AI analysis")
public class PullRequestController {

    private final PullRequestRepository pullRequestRepository;
    private final PrAnalysisRepository prAnalysisRepository;
    private final PullRequestAnalysisService analysisService;
    private final TenantContext tenantContext;
    private final JsonMapper jsonMapper;

    public PullRequestController(PullRequestRepository pullRequestRepository,
                                 PrAnalysisRepository prAnalysisRepository,
                                 PullRequestAnalysisService analysisService,
                                 TenantContext tenantContext,
                                 JsonMapper jsonMapper) {
        this.pullRequestRepository = pullRequestRepository;
        this.prAnalysisRepository = prAnalysisRepository;
        this.analysisService = analysisService;
        this.tenantContext = tenantContext;
        this.jsonMapper = jsonMapper;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Summary(
            UUID prId, String repoName, Integer prNumber, String title, String author,
            OffsetDateTime mergedAt, Integer riskScore, RiskLevel riskLevel,
            Integer deploymentReadinessScore, ReadinessStatus deploymentReadinessStatus,
            boolean analyzed, String ticketKey
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Detail(
            UUID prId, String repoName, Integer prNumber, String title, String description,
            String author, String branch, String ticketKey, String mergeSha,
            OffsetDateTime mergedAt, JsonNode changedFiles,
            boolean analyzed,
            JsonNode sapCommerceContext, JsonNode impactAnalysis, JsonNode regressionRecommendation,
            Integer riskScore, RiskLevel riskLevel, JsonNode riskReasons, String riskPolicyVersion,
            JsonNode configurationDrift,
            JsonNode aiSummary, Boolean aiFallbackUsed, String modelProvider, String modelName,
            Integer tokensUsed,
            Integer deploymentReadinessScore, ReadinessStatus deploymentReadinessStatus,
            ProvenanceClass provenanceClass
    ) {
    }

    @GetMapping
    @Operation(summary = "Page of captured pull requests for the current tenant, most recently merged first")
    public PageResponse<Summary> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        String tenantId = tenantContext.currentTenantId();
        int safeSize = size <= 0 ? 50 : Math.min(size, 200);
        Page<PullRequest> result = pullRequestRepository.findByTenantId(tenantId,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "mergedAt")));

        List<UUID> ids = result.getContent().stream().map(PullRequest::getPrId).toList();
        var analyses = prAnalysisRepository.findByPrIdIn(ids).stream()
                .collect(java.util.stream.Collectors.toMap(PrAnalysis::getPrId, a -> a));

        List<Summary> items = result.getContent().stream()
                .map(pr -> toSummary(pr, analyses.get(pr.getPrId())))
                .toList();

        return PageResponse.of(items, result.getTotalElements(), page, safeSize);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One pull request's full captured facts plus its deterministic and AI analysis")
    public ResponseEntity<Detail> get(@PathVariable UUID id) {
        Optional<PullRequest> pullRequest = pullRequestRepository.findById(id);
        if (pullRequest.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PrAnalysis analysis = prAnalysisRepository.findById(id).orElse(null);
        return ResponseEntity.ok(toDetail(pullRequest.get(), analysis));
    }

    @PostMapping("/{id}/reanalyze")
    @Operation(
            summary = "Re-run the deterministic + AI analysis pipeline for this pull request",
            description = "Overwrites the existing pr_analysis row rather than creating a second one.")
    public ResponseEntity<Detail> reanalyze(@PathVariable UUID id) {
        if (pullRequestRepository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PrAnalysis analysis = analysisService.analyze(id);
        PullRequest pullRequest = pullRequestRepository.findById(id).orElseThrow();
        return ResponseEntity.ok(toDetail(pullRequest, analysis));
    }

    @GetMapping("/{id}/context-package")
    @Operation(
            summary = "The exact deterministic package handed to the AI service for this pull request",
            description = """
                    Reconstructed from the persisted analysis, not re-derived — this is
                    provably what the model saw: title, ticket key, repo name, and the
                    six deterministic engine outputs. Never source code, never a diff.
                    """)
    public ResponseEntity<Map<String, Object>> contextPackage(@PathVariable UUID id) {
        Optional<PullRequest> pullRequest = pullRequestRepository.findById(id);
        if (pullRequest.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Optional<PrAnalysis> analysis = prAnalysisRepository.findById(id);
        if (analysis.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PullRequest pr = pullRequest.get();
        PrAnalysis a = analysis.get();

        Map<String, Object> package_ = new java.util.LinkedHashMap<>();
        package_.put("prNumber", pr.getPrNumber());
        package_.put("title", pr.getTitle());
        package_.put("ticketKey", pr.getTicketKey());
        package_.put("repoName", pr.getRepoName());
        package_.put("sapCommerceContext", jsonMapper.readTree(a.getSapCommerceContext()));
        package_.put("impactAnalysis", jsonMapper.readTree(a.getImpactAnalysis()));
        package_.put("riskResult", Map.of("score", a.getRiskScore(), "level", a.getRiskLevel(),
                "reasons", jsonMapper.readTree(a.getRiskReasons()), "policyVersion", a.getRiskPolicyVersion()));
        package_.put("regressionSuggestions", jsonMapper.readTree(a.getRegressionRecommendation()));
        package_.put("configurationDrift", jsonMapper.readTree(a.getConfigurationDrift()));
        package_.put("deploymentReadiness", Map.of("score", a.getDeploymentReadinessScore(),
                "status", a.getDeploymentReadinessStatus()));

        return ResponseEntity.ok(package_);
    }

    private Detail toDetail(PullRequest pr, PrAnalysis analysis) {
        return new Detail(
                pr.getPrId(), pr.getRepoName(), pr.getPrNumber(), pr.getTitle(), pr.getDescription(),
                pr.getAuthor(), pr.getBranch(), pr.getTicketKey(), pr.getMergeSha(),
                pr.getMergedAt(), jsonMapper.readTree(pr.getChangedFiles()),
                analysis != null,
                analysis == null ? null : jsonMapper.readTree(analysis.getSapCommerceContext()),
                analysis == null ? null : jsonMapper.readTree(analysis.getImpactAnalysis()),
                analysis == null ? null : jsonMapper.readTree(analysis.getRegressionRecommendation()),
                analysis == null ? null : analysis.getRiskScore(),
                analysis == null ? null : analysis.getRiskLevel(),
                analysis == null ? null : jsonMapper.readTree(analysis.getRiskReasons()),
                analysis == null ? null : analysis.getRiskPolicyVersion(),
                analysis == null ? null : jsonMapper.readTree(analysis.getConfigurationDrift()),
                analysis == null ? null : jsonMapper.readTree(analysis.getAiSummary()),
                analysis == null ? null : analysis.isAiFallbackUsed(),
                analysis == null ? null : analysis.getModelProvider(),
                analysis == null ? null : analysis.getModelName(),
                analysis == null ? null : analysis.getTokensUsed(),
                analysis == null ? null : analysis.getDeploymentReadinessScore(),
                analysis == null ? null : analysis.getDeploymentReadinessStatus(),
                analysis == null ? null : analysis.getProvenanceClass());
    }

    private static Summary toSummary(PullRequest pr, PrAnalysis analysis) {
        return new Summary(
                pr.getPrId(), pr.getRepoName(), pr.getPrNumber(), pr.getTitle(), pr.getAuthor(),
                pr.getMergedAt(),
                analysis == null ? null : analysis.getRiskScore(),
                analysis == null ? null : analysis.getRiskLevel(),
                analysis == null ? null : analysis.getDeploymentReadinessScore(),
                analysis == null ? null : analysis.getDeploymentReadinessStatus(),
                analysis != null, pr.getTicketKey());
    }
}
