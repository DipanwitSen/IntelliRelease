package com.gyansys.intellirelease.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gyansys.intellirelease.adapters.ai.AiAnalysisRequest;
import com.gyansys.intellirelease.adapters.ai.AiAnalysisResponse;
import com.gyansys.intellirelease.adapters.ai.AiServiceClient;
import com.gyansys.intellirelease.adapters.ai.DeterministicNarrator;
import com.gyansys.intellirelease.domain.context.ChangedFile;
import com.gyansys.intellirelease.domain.context.ContextResult;
import com.gyansys.intellirelease.domain.context.SAPCommerceContextEngine;
import com.gyansys.intellirelease.domain.drift.ConfigurationDriftEngine;
import com.gyansys.intellirelease.domain.drift.DriftResult;
import com.gyansys.intellirelease.domain.impact.ImpactAnalyzer;
import com.gyansys.intellirelease.domain.impact.ImpactResult;
import com.gyansys.intellirelease.domain.readiness.DeploymentReadinessEngine;
import com.gyansys.intellirelease.domain.readiness.ReadinessInput;
import com.gyansys.intellirelease.domain.readiness.ReadinessResult;
import com.gyansys.intellirelease.domain.regression.RegressionRecommender;
import com.gyansys.intellirelease.domain.regression.RegressionResult;
import com.gyansys.intellirelease.domain.risk.RiskEngine;
import com.gyansys.intellirelease.domain.risk.RiskPolicy;
import com.gyansys.intellirelease.domain.risk.RiskResult;
import com.gyansys.intellirelease.infra.AuditWriter;
import com.gyansys.intellirelease.infra.JsonMapper;
import com.gyansys.intellirelease.model.PrAnalysis;
import com.gyansys.intellirelease.model.PullRequest;
import com.gyansys.intellirelease.model.enums.SapCapability;
import com.gyansys.intellirelease.repository.PrAnalysisRepository;
import com.gyansys.intellirelease.repository.PullRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Runs the philosophy chain for one pull request, in order.
 *
 * <pre>
 *   facts -> SAP Commerce context -> four parallel rule engines
 *         -> readiness composition -> AI explanation
 * </pre>
 *
 * <p>The AI call is deliberately last and deliberately optional. By the time it
 * runs, every fact, score and recommendation already exists and is persisted;
 * the model is being handed finished work to describe. If it never answers, the
 * analysis is complete and a template narrates it.
 */
@Service
public class PullRequestAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(PullRequestAnalysisService.class);

    private final PullRequestRepository pullRequestRepository;
    private final PrAnalysisRepository prAnalysisRepository;
    private final SAPCommerceContextEngine contextEngine;
    private final ImpactAnalyzer impactAnalyzer;
    private final RiskEngine riskEngine;
    private final RegressionRecommender regressionRecommender;
    private final ConfigurationDriftEngine driftEngine;
    private final DeploymentReadinessEngine readinessEngine;
    private final ConfigurationBaselineProvider baselineProvider;
    private final AiServiceClient aiServiceClient;
    private final DeterministicNarrator narrator;
    private final JsonMapper jsonMapper;
    private final AuditWriter auditWriter;

    public PullRequestAnalysisService(PullRequestRepository pullRequestRepository,
                                      PrAnalysisRepository prAnalysisRepository,
                                      SAPCommerceContextEngine contextEngine,
                                      ImpactAnalyzer impactAnalyzer,
                                      RiskEngine riskEngine,
                                      RegressionRecommender regressionRecommender,
                                      ConfigurationDriftEngine driftEngine,
                                      DeploymentReadinessEngine readinessEngine,
                                      ConfigurationBaselineProvider baselineProvider,
                                      AiServiceClient aiServiceClient,
                                      DeterministicNarrator narrator,
                                      JsonMapper jsonMapper,
                                      AuditWriter auditWriter) {
        this.pullRequestRepository = pullRequestRepository;
        this.prAnalysisRepository = prAnalysisRepository;
        this.contextEngine = contextEngine;
        this.impactAnalyzer = impactAnalyzer;
        this.riskEngine = riskEngine;
        this.regressionRecommender = regressionRecommender;
        this.driftEngine = driftEngine;
        this.readinessEngine = readinessEngine;
        this.baselineProvider = baselineProvider;
        this.aiServiceClient = aiServiceClient;
        this.narrator = narrator;
        this.jsonMapper = jsonMapper;
        this.auditWriter = auditWriter;
    }

    @Transactional
    public PrAnalysis analyze(UUID prId) {
        PullRequest pullRequest = pullRequestRepository.findById(prId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown pull request " + prId));

        List<ChangedFile> changedFiles = readChangedFiles(pullRequest);

        // 1. Deterministic SAP Commerce meaning. Everything below reads this.
        ContextResult context = contextEngine.analyze(changedFiles);

        // 2. Four engines off the same context. Independent of each other.
        ImpactResult impact = impactAnalyzer.analyze(context);
        RiskResult risk = riskEngine.evaluate(context);
        RegressionResult regression = regressionRecommender.recommend(impact, risk);
        DriftResult drift = evaluateDrift(context);

        // 3. Composition. Approval and QA sign-off are release-level facts, so a
        //    single PR always scores them as pending rather than assuming them.
        ReadinessResult readiness = readinessEngine.evaluate(
                ReadinessInput.forPullRequest(risk, impact, drift, regression.suiteCount()));

        PrAnalysis analysis = PrAnalysis.forPullRequest(prId, pullRequest.getTenantId());
        analysis.setSapCommerceContext(jsonMapper.toJson(context));
        analysis.setImpactAnalysis(jsonMapper.toJson(impact));
        analysis.setRegressionRecommendation(jsonMapper.toJson(regression));
        analysis.setRiskScore(risk.score());
        analysis.setRiskLevel(risk.level());
        analysis.setRiskReasons(jsonMapper.toJson(risk.reasons()));
        analysis.setRiskPolicyVersion(RiskPolicy.POLICY_VERSION);
        analysis.setConfigurationDrift(jsonMapper.toJson(drift));
        analysis.setDeploymentReadinessScore(readiness.score());
        analysis.setDeploymentReadinessStatus(readiness.status());

        // 4. Explanation last. Never a source of fact.
        applyNarration(analysis, pullRequest, context, impact, risk, regression, drift, readiness);

        PrAnalysis saved = prAnalysisRepository.save(analysis);

        auditWriter.recordAs(AuditWriter.SYSTEM_WORKER, "PR_ANALYZED",
                "PullRequest", prId.toString(),
                "risk=" + risk.score() + " " + risk.level()
                        + " capabilities=" + context.capabilities().size()
                        + " readiness=" + readiness.score() + " " + readiness.status()
                        + " aiFallback=" + saved.isAiFallbackUsed());

        log.info("Analysed PR #{} on {}: risk {} {}, {} confirmed / {} potential impacts",
                pullRequest.getPrNumber(), pullRequest.getRepoName(),
                risk.score(), risk.level(), impact.confirmedCount(), impact.potentialCount());

        return saved;
    }

    // ------------------------------------------------------------------

    /**
     * Drift is only meaningful when the change actually touched configuration.
     * Running it on a pure Java change would report every pre-existing
     * environment difference as though this release caused it.
     */
    private DriftResult evaluateDrift(ContextResult context) {
        if (!context.hasCapability(SapCapability.CONFIGURATION)) {
            return DriftResult.unavailable("No configuration files changed in this pull request");
        }
        return driftEngine.compare(
                baselineProvider.productionBaseline(),
                baselineProvider.releaseSnapshot("current"));
    }

    private void applyNarration(PrAnalysis analysis, PullRequest pullRequest, ContextResult context,
                                ImpactResult impact, RiskResult risk, RegressionResult regression,
                                DriftResult drift, ReadinessResult readiness) {
        AiAnalysisRequest request = new AiAnalysisRequest(
                pullRequest.getPrNumber(),
                pullRequest.getTitle(),
                pullRequest.getTicketKey(),
                pullRequest.getRepoName(),
                context, impact, risk, regression, drift, readiness);

        AiAnalysisResponse response = aiServiceClient.analyze(request);
        boolean usedFallback = response == null || response.fallback();

        if (response == null) {
            response = narrator.narrate(request);
        }

        analysis.setAiSummary(jsonMapper.toJson(response));
        analysis.setAiFallbackUsed(usedFallback);
        analysis.setModelProvider(response.provider());
        analysis.setModelName(response.model());
        analysis.setTokensUsed(response.tokensUsed());
    }

    private List<ChangedFile> readChangedFiles(PullRequest pullRequest) {
        List<ChangedFile> files = jsonMapper.fromJson(
                pullRequest.getChangedFiles(), new TypeReference<List<ChangedFile>>() {
                });
        return files == null ? List.of() : files;
    }
}
