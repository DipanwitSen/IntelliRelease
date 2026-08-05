package com.gyansys.intellirelease.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

    public PullRequestController(PullRequestRepository pullRequestRepository,
                                 PrAnalysisRepository prAnalysisRepository,
                                 TenantContext tenantContext,
                                 JsonMapper jsonMapper) {
        this.pullRequestRepository = pullRequestRepository;
        this.prAnalysisRepository = prAnalysisRepository;
        this.tenantContext = tenantContext;
        this.jsonMapper = jsonMapper;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Summary(
            UUID prId, String repoName, Integer prNumber, String title, String author,
            OffsetDateTime mergedAt, Integer riskScore, RiskLevel riskLevel,
            Integer deploymentReadinessScore, ReadinessStatus deploymentReadinessStatus,
            boolean analyzed
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
    @Operation(summary = "List captured pull requests for the current tenant, most recently merged first")
    public List<Summary> list(@RequestParam(defaultValue = "50") int limit) {
        String tenantId = tenantContext.currentTenantId();
        List<PullRequest> pullRequests = pullRequestRepository
                .findByTenantIdOrderByMergedAtDesc(tenantId, PageRequest.of(0, Math.min(limit, 200)));

        List<UUID> ids = pullRequests.stream().map(PullRequest::getPrId).toList();
        var analyses = prAnalysisRepository.findByPrIdIn(ids).stream()
                .collect(java.util.stream.Collectors.toMap(PrAnalysis::getPrId, a -> a));

        return pullRequests.stream()
                .map(pr -> {
                    PrAnalysis analysis = analyses.get(pr.getPrId());
                    return new Summary(
                            pr.getPrId(), pr.getRepoName(), pr.getPrNumber(), pr.getTitle(), pr.getAuthor(),
                            pr.getMergedAt(),
                            analysis == null ? null : analysis.getRiskScore(),
                            analysis == null ? null : analysis.getRiskLevel(),
                            analysis == null ? null : analysis.getDeploymentReadinessScore(),
                            analysis == null ? null : analysis.getDeploymentReadinessStatus(),
                            analysis != null);
                })
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One pull request's full captured facts plus its deterministic and AI analysis")
    public ResponseEntity<Detail> get(@PathVariable UUID id) {
        Optional<PullRequest> pullRequest = pullRequestRepository.findById(id);
        if (pullRequest.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PullRequest pr = pullRequest.get();
        PrAnalysis analysis = prAnalysisRepository.findById(id).orElse(null);

        Detail detail = new Detail(
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

        return ResponseEntity.ok(detail);
    }
}
