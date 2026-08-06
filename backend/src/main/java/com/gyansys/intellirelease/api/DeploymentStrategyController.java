package com.gyansys.intellirelease.api;

import com.gyansys.intellirelease.domain.deployment.DeploymentStrategyResult;
import com.gyansys.intellirelease.infra.JsonMapper;
import com.gyansys.intellirelease.model.PrAnalysis;
import com.gyansys.intellirelease.model.Release;
import com.gyansys.intellirelease.repository.PrAnalysisRepository;
import com.gyansys.intellirelease.repository.ReleaseRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The Deployment Strategy Advisor's dedicated read surface.
 *
 * <p>The same {@link DeploymentStrategyResult} already rides inside {@code
 * GET /pull-requests/{id}} ({@code deploymentStrategy}) and is summarised on
 * {@code GET /releases/{id}} ({@code deploymentStrategyType}) — this
 * controller exists so a caller who only wants the recommendation (the
 * Release Dashboard card, an external integration, a test) can fetch it
 * directly without pulling the rest of either payload.
 *
 * <p>Both endpoints are pure reads: nothing here re-runs {@code
 * DeploymentStrategyEngine}. The recommendation was already computed —
 * per pull request at analysis time, per release when it was built — and
 * this controller only returns what was stored, exactly like every other
 * read endpoint in this platform.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Deployment Strategy", description = "Deterministic ROLLING/MIGRATE recommendation and why")
public class DeploymentStrategyController {

    private final PrAnalysisRepository prAnalysisRepository;
    private final ReleaseRepository releaseRepository;
    private final JsonMapper jsonMapper;

    public DeploymentStrategyController(PrAnalysisRepository prAnalysisRepository,
                                        ReleaseRepository releaseRepository, JsonMapper jsonMapper) {
        this.prAnalysisRepository = prAnalysisRepository;
        this.releaseRepository = releaseRepository;
        this.jsonMapper = jsonMapper;
    }

    @GetMapping("/pull-requests/{id}/deployment-strategy")
    @Operation(
            summary = "This pull request's deployment strategy recommendation",
            description = """
                    ROLLING or MIGRATE, decided entirely from the changed files' SAP Commerce
                    artifact types — see DeploymentStrategyEngine. 404 before the pull request has
                    been analysed; the recommendation does not exist until then, so this endpoint
                    never approximates one.
                    """)
    public ResponseEntity<DeploymentStrategyResult> forPullRequest(@PathVariable UUID id) {
        return prAnalysisRepository.findById(id)
                .map(PrAnalysis::getDeploymentStrategy)
                .map(json -> jsonMapper.fromJson(json, DeploymentStrategyResult.class))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/releases/{id}/deployment-strategy")
    @Operation(
            summary = "This release's deployment strategy recommendation",
            description = """
                    The release inherits the strategy any of its resolved pull requests already
                    required — one change touching items.xml makes the whole release a migrate
                    deployment. Computed when the release is built (see POST /releases/{id}/build);
                    404 before that, or if the release has no resolved pull requests yet.
                    """)
    public ResponseEntity<DeploymentStrategyResult> forRelease(@PathVariable UUID id) {
        return releaseRepository.findById(id)
                .map(Release::getDeploymentStrategy)
                .map(json -> jsonMapper.fromJson(json, DeploymentStrategyResult.class))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
