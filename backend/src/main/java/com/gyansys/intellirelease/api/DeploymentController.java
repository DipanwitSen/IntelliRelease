package com.gyansys.intellirelease.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.infra.TenantContext;
import com.gyansys.intellirelease.model.Release;
import com.gyansys.intellirelease.model.enums.RiskLevel;
import com.gyansys.intellirelease.repository.ReleaseRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Deployment history, built from {@code Release.deployedAt}/{@code deployedBy} —
 * the human attestation recorded by {@code POST /releases/{id}/deploy}. There is
 * no CI/CD or SAP Commerce Cloud integration behind this yet, so every event
 * here is a completed, human-confirmed fact: IntelliRelease has no notion of
 * an in-progress or failed deployment to report.
 */
@RestController
@RequestMapping("/api/v1/deployments")
@Tag(name = "Deployments", description = "Human-confirmed deployment history")
public class DeploymentController {

    private final ReleaseRepository releaseRepository;
    private final TenantContext tenantContext;

    public DeploymentController(ReleaseRepository releaseRepository, TenantContext tenantContext) {
        this.releaseRepository = releaseRepository;
        this.tenantContext = tenantContext;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Event(
            UUID id, UUID releaseId, String repoName, String version, String environment,
            String status, OffsetDateTime startedAt, OffsetDateTime finishedAt, long durationMs,
            String triggeredBy, RiskLevel riskLevel, String buildStatus,
            Integer failedTests, Integer totalTests, String notes, String rollbackOf
    ) {
    }

    @GetMapping
    @Operation(
            summary = "Deployment confirmations, most recent first",
            description = """
                    One event per release marked deployed. `environment` defaults to
                    "production" — the platform does not yet track which environment a
                    deployment targeted, only that the release manager confirmed it is live.
                    """)
    public PageResponse<Event> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size,
                                    @RequestParam(required = false) String repo) {
        String tenantId = tenantContext.currentTenantId();
        List<Release> deployed = releaseRepository
                .findByTenantIdAndDeployedAtIsNotNullOrderByDeployedAtDesc(tenantId).stream()
                .filter(release -> repo == null || repo.isBlank() || repo.equals(release.getRepoName()))
                .toList();

        int safeSize = size <= 0 ? 50 : Math.min(size, 200);
        int fromIndex = Math.min(Math.max(page, 0) * safeSize, deployed.size());
        int toIndex = Math.min(fromIndex + safeSize, deployed.size());

        List<Event> items = deployed.subList(fromIndex, toIndex).stream()
                .map(this::toEvent)
                .toList();

        return PageResponse.of(items, deployed.size(), page, safeSize);
    }

    private Event toEvent(Release release) {
        return new Event(
                release.getReleaseId(), release.getReleaseId(), release.getRepoName(), release.getVersion(),
                "production", "SUCCEEDED", release.getDeployedAt(), release.getDeployedAt(), 0,
                release.getDeployedBy(), release.getAggregateRiskLevel(), null, null, null, null, null);
    }
}
