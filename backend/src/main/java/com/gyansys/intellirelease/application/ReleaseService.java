package com.gyansys.intellirelease.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gyansys.intellirelease.adapters.ai.AiServiceClient;
import com.gyansys.intellirelease.adapters.ai.AiSynthesisRequest;
import com.gyansys.intellirelease.adapters.ai.AiSynthesisResponse;
import com.gyansys.intellirelease.adapters.ai.DeterministicNarrator;
import com.gyansys.intellirelease.domain.context.ChangedFile;
import com.gyansys.intellirelease.domain.context.ContextResult;
import com.gyansys.intellirelease.domain.context.FileContext;
import com.gyansys.intellirelease.domain.deployment.DeploymentStrategyEngine;
import com.gyansys.intellirelease.domain.deployment.DeploymentStrategyResult;
import com.gyansys.intellirelease.domain.release.ExcludedChange;
import com.gyansys.intellirelease.domain.release.ReleaseResolver;
import com.gyansys.intellirelease.infra.AuditWriter;
import com.gyansys.intellirelease.infra.JsonMapper;
import com.gyansys.intellirelease.infra.TenantContext;
import com.gyansys.intellirelease.model.PrAnalysis;
import com.gyansys.intellirelease.model.PullRequest;
import com.gyansys.intellirelease.model.Release;
import com.gyansys.intellirelease.model.enums.ReleaseStatus;
import com.gyansys.intellirelease.repository.PrAnalysisRepository;
import com.gyansys.intellirelease.repository.PullRequestRepository;
import com.gyansys.intellirelease.repository.ReleaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Minimal release lifecycle: create a release record from Git refs, resolve
 * its true contents from the commit graph, generate a changelog, and record
 * deployment confirmation.
 *
 * <p>This deliberately does not run the aggregate risk/impact/regression pass
 * — that is the rest of the planned release pipeline (see CLAUDE.md), not
 * built yet. What exists here is enough to answer "what actually shipped in
 * this release, and did it go out."
 */
@Service
public class ReleaseService {

    private final ReleaseRepository releaseRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PrAnalysisRepository prAnalysisRepository;
    private final ReleaseResolver releaseResolver;
    private final DeploymentStrategyEngine deploymentStrategyEngine;
    private final AiServiceClient aiServiceClient;
    private final DeterministicNarrator narrator;
    private final TenantContext tenantContext;
    private final AuditWriter auditWriter;
    private final JsonMapper jsonMapper;

    public ReleaseService(ReleaseRepository releaseRepository,
                          PullRequestRepository pullRequestRepository,
                          PrAnalysisRepository prAnalysisRepository,
                          ReleaseResolver releaseResolver,
                          DeploymentStrategyEngine deploymentStrategyEngine,
                          AiServiceClient aiServiceClient,
                          DeterministicNarrator narrator,
                          TenantContext tenantContext,
                          AuditWriter auditWriter,
                          JsonMapper jsonMapper) {
        this.releaseRepository = releaseRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.prAnalysisRepository = prAnalysisRepository;
        this.releaseResolver = releaseResolver;
        this.deploymentStrategyEngine = deploymentStrategyEngine;
        this.aiServiceClient = aiServiceClient;
        this.narrator = narrator;
        this.tenantContext = tenantContext;
        this.auditWriter = auditWriter;
        this.jsonMapper = jsonMapper;
    }

    public record CreateReleaseRequest(String repoName, String version, String fromRef, String toRef) {
    }

    /** What {@link #build} found, beyond what fits on the {@link Release} row itself. */
    public record BuildResult(
            Release release,
            int commitsExamined,
            boolean gitAvailable,
            List<Integer> unmatchedPrNumbers,
            List<String> resolverNotes
    ) {
    }

    public record NoteBullet(Integer prNumber, String ticketKey, String text) {
    }

    /** The same four audience notes {@code NotificationService} emails — previewed here first. */
    public record AudienceNotes(String developer, String qa, String business, String client) {
    }

    public record ReleaseNotes(
            String version, String plainText, List<NoteBullet> bullets,
            AudienceNotes audienceNotes, String releaseSummary, String knownRisks, String deploymentRecommendation,
            boolean fallback, String provider
    ) {
    }

    @Transactional
    public Release create(CreateReleaseRequest request) {
        String tenantId = tenantContext.currentTenantId();

        releaseRepository.findByTenantIdAndRepoNameAndVersion(tenantId, request.repoName(), request.version())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Release " + request.version() + " already exists for " + request.repoName());
                });

        Release release = Release.create(tenantId, request.repoName(), request.version(),
                request.fromRef(), request.toRef());
        Release saved = releaseRepository.save(release);

        auditWriter.record("RELEASE_CREATED", "Release", saved.getReleaseId().toString(),
                "Release " + saved.getVersion() + " created for " + saved.getRepoName()
                        + " (" + saved.getFromRef() + " -> " + saved.getToRef() + ")");

        return saved;
    }

    public List<Release> list() {
        return releaseRepository.findByTenantIdOrderByCreatedAtDesc(tenantContext.currentTenantId());
    }

    public Optional<Release> get(UUID releaseId) {
        return releaseRepository.findWithPullRequestsByReleaseId(releaseId);
    }

    /**
     * Resolves the release's true contents from the Git graph (cherry-pick
     * aware, revert-netted — see {@link ReleaseResolver}) and attaches every
     * resolved PR number that IntelliRelease has actually captured via
     * webhook. A resolved PR that was never captured (e.g. merged before the
     * webhook was configured) is reported in {@code unmatchedPrNumbers}
     * rather than silently dropped or fabricated.
     */
    @Transactional
    public Optional<BuildResult> build(UUID releaseId) {
        Optional<Release> found = releaseRepository.findWithPullRequestsByReleaseId(releaseId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Release release = found.get();
        String tenantId = release.getTenantId();

        ReleaseResolver.ResolvedRelease resolved =
                releaseResolver.resolve(release.getRepoName(), release.getFromRef(), release.getToRef());

        Set<PullRequest> attached = new LinkedHashSet<>();
        List<Integer> unmatched = new ArrayList<>();
        for (Integer prNumber : resolved.prNumbers()) {
            pullRequestRepository.findByTenantIdAndRepoNameAndPrNumber(tenantId, release.getRepoName(), prNumber)
                    .ifPresentOrElse(attached::add, () -> unmatched.add(prNumber));
        }

        release.getPullRequests().clear();
        release.getPullRequests().addAll(attached);
        release.setResolvedPrCount(resolved.prNumbers().size());
        release.setExcludedPrs(jsonMapper.toJson(resolved.excluded()));
        release.setBuiltAt(OffsetDateTime.now());
        release.setStatus(ReleaseStatus.BUILT);

        DeploymentStrategyResult deploymentStrategy = aggregateDeploymentStrategy(attached);
        release.setDeploymentStrategy(jsonMapper.toJson(deploymentStrategy));
        release.setDeploymentStrategyType(deploymentStrategy.strategy());

        Release saved = releaseRepository.save(release);

        auditWriter.record("RELEASE_BUILT", "Release", saved.getReleaseId().toString(),
                resolved.commitsExamined() + " commit(s) examined, " + resolved.prNumbers().size()
                        + " PR(s) resolved (" + attached.size() + " matched, " + unmatched.size()
                        + " not yet captured), " + resolved.excluded().size() + " excluded");

        return Optional.of(new BuildResult(saved, resolved.commitsExamined(), resolved.gitAvailable(),
                unmatched, resolved.resolverNotes()));
    }

    /**
     * A changelog line per included PR. Tries the AI service first — title,
     * description, changed file paths (never content) and SAP Commerce
     * artifact classification, all facts already on file, handed over for
     * narration only. Falls back to a deterministic template (title plus the
     * description's first sentence) when the AI service is unreachable or its
     * output fails validation, exactly like PR-level analysis does.
     */
    public Optional<ReleaseNotes> generateNotes(UUID releaseId) {
        return releaseRepository.findWithPullRequestsByReleaseId(releaseId).map(release -> {
            AiSynthesisResponse response = synthesizeRelease(release);

            List<NoteBullet> bullets = response.changelogBullets().stream()
                    .map(bullet -> new NoteBullet(bullet.prNumber(), bullet.ticketKey(), bullet.text()))
                    .toList();

            String text = renderPlainText(release.getVersion(), bullets);
            AudienceNotes audienceNotes = new AudienceNotes(
                    response.developerNote(), response.qaNote(), response.businessNote(), response.clientNote());

            return new ReleaseNotes(release.getVersion(), text, bullets, audienceNotes,
                    response.releaseSummary(), response.knownRisks(), response.deploymentRecommendation(),
                    response.fallback(), response.provider());
        });
    }

    /**
     * The single AI synthesis call behind both the notes preview and
     * notification dispatch, so a release manager previewing notes and the
     * emails/Teams post that eventually go out are guaranteed to say the same
     * thing — never two independently-generated versions of "what shipped."
     */
    public AiSynthesisResponse synthesizeRelease(Release release) {
        AiSynthesisRequest request = buildSynthesisRequest(release);
        AiSynthesisResponse response = aiServiceClient.synthesize(request);
        return response == null ? narrator.narrateRelease(request) : response;
    }

    private AiSynthesisRequest buildSynthesisRequest(Release release) {
        Map<UUID, PrAnalysis> analyses = analysesFor(release.getPullRequests());

        List<AiSynthesisRequest.PrSummary> pullRequests = release.getPullRequests().stream()
                .sorted((left, right) -> {
                    OffsetDateTime leftMerged = left.getMergedAt();
                    OffsetDateTime rightMerged = right.getMergedAt();
                    return leftMerged == null || rightMerged == null ? 0 : leftMerged.compareTo(rightMerged);
                })
                .map(pr -> toSummary(pr, analyses.get(pr.getPrId())))
                .toList();

        List<ExcludedChange> excludedChanges = jsonMapper.fromJson(release.getExcludedPrs(),
                new TypeReference<List<ExcludedChange>>() {
                });
        List<AiSynthesisRequest.ExcludedPr> excludedPrs = excludedChanges == null ? List.of()
                : excludedChanges.stream()
                        .map(ex -> new AiSynthesisRequest.ExcludedPr(ex.prNumber(), ex.sha(), ex.reason(), ex.evidence()))
                        .toList();

        return new AiSynthesisRequest(
                release.getVersion(), release.getRepoName(), release.getFromRef(), release.getToRef(),
                pullRequests.size(), pullRequests, excludedPrs,
                null, null, null, null, null);
    }

    private AiSynthesisRequest.PrSummary toSummary(PullRequest pr, PrAnalysis analysis) {
        List<ChangedFile> changedFiles = jsonMapper.fromJson(pr.getChangedFiles(),
                new TypeReference<List<ChangedFile>>() {
                });
        List<String> changedFilePaths = changedFiles == null ? List.of()
                : changedFiles.stream().map(ChangedFile::path).toList();

        List<String> artifactTypes = List.of();
        List<String> businessCapabilities = List.of();
        if (analysis != null) {
            ContextResult context = jsonMapper.fromJson(analysis.getSapCommerceContext(), ContextResult.class);
            if (context != null) {
                artifactTypes = context.files().stream()
                        .map(FileContext::artifactDisplayName)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
                businessCapabilities = context.files().stream()
                        .flatMap(file -> file.businessCapabilityTags().stream())
                        .distinct()
                        .toList();
            }
        }

        return new AiSynthesisRequest.PrSummary(
                pr.getPrNumber(), pr.getTitle(), pr.getDescription(), pr.getTicketKey(),
                analysis == null || analysis.getRiskScore() == null ? 0 : analysis.getRiskScore(),
                analysis == null || analysis.getRiskLevel() == null ? null : analysis.getRiskLevel().name(),
                businessCapabilities, changedFilePaths, artifactTypes);
    }

    private static String renderPlainText(String version, List<NoteBullet> bullets) {
        StringBuilder text = new StringBuilder("Release Notes ").append(version).append('\n');
        if (bullets.isEmpty()) {
            text.append('\n').append("No changes resolved for this release yet.");
        } else {
            for (NoteBullet bullet : bullets) {
                text.append(bullet.text());
                if (bullet.ticketKey() != null && !bullet.ticketKey().isBlank()) {
                    text.append(" (").append(bullet.ticketKey()).append(')');
                }
                text.append('\n');
            }
        }
        return text.toString();
    }

    /**
     * The governance gate: client-facing communication cannot go out until a
     * human has approved it here. {@link NotificationService} refuses to send
     * for any release not in this state — see {@link ApprovalRequiredException}.
     */
    @Transactional
    public Optional<Release> approve(UUID releaseId, String approvedBy) {
        return releaseRepository.findById(releaseId).map(release -> {
            release.setStatus(ReleaseStatus.APPROVED);
            Release saved = releaseRepository.save(release);

            auditWriter.record("RELEASE_APPROVED", "Release", saved.getReleaseId().toString(),
                    "Release " + saved.getVersion() + " approved by " + approvedBy);

            return saved;
        });
    }

    /**
     * Records that a release is actually running in the target environment.
     * Deliberately independent of {@code status}: re-confirming an already
     * -deployed release just moves the timestamp forward and re-attributes
     * it, rather than being rejected, since "who confirmed it and when" is
     * exactly the fact a correction should update.
     */
    @Transactional
    public Optional<Release> markDeployed(UUID releaseId, String deployedBy) {
        return releaseRepository.findById(releaseId).map(release -> {
            release.setDeployedAt(OffsetDateTime.now());
            release.setDeployedBy(deployedBy);
            Release saved = releaseRepository.save(release);

            auditWriter.record("RELEASE_DEPLOYED", "Release", saved.getReleaseId().toString(),
                    "Release " + saved.getVersion() + " on " + saved.getRepoName()
                            + " confirmed deployed by " + deployedBy);

            return saved;
        });
    }

    /** Risk band per PR, when analysis has already run — used to enrich the release view. */
    public Map<UUID, PrAnalysis> analysesFor(Collection<PullRequest> pullRequests) {
        List<UUID> ids = pullRequests.stream().map(PullRequest::getPrId).toList();
        return prAnalysisRepository.findByPrIdIn(ids).stream()
                .collect(Collectors.toMap(PrAnalysis::getPrId, a -> a));
    }

    /**
     * Reads each attached pull request's already-computed {@link DeploymentStrategyResult}
     * (stored per PR at analysis time — see {@link PullRequestAnalysisService}) and rolls
     * them up via {@link DeploymentStrategyEngine#aggregate}. A PR not yet analysed
     * contributes nothing, exactly like any other engine output on an unanalysed PR.
     */
    private DeploymentStrategyResult aggregateDeploymentStrategy(Collection<PullRequest> attached) {
        List<DeploymentStrategyResult> prResults = analysesFor(attached).values().stream()
                .map(analysis -> jsonMapper.fromJson(analysis.getDeploymentStrategy(), DeploymentStrategyResult.class))
                .filter(Objects::nonNull)
                .toList();
        return deploymentStrategyEngine.aggregate(prResults);
    }
}
