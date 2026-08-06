package com.gyansys.intellirelease.model;

import com.gyansys.intellirelease.model.enums.DeploymentStrategyType;
import com.gyansys.intellirelease.model.enums.ReadinessStatus;
import com.gyansys.intellirelease.model.enums.ReleaseStatus;
import com.gyansys.intellirelease.model.enums.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A release: the exact set of changes shipping between two Git refs.
 *
 * <p>Contents come from the Git graph, never from a date range. {@code excludedPrs}
 * records what was deliberately left out and why — a merged-then-reverted PR
 * appears there rather than silently vanishing, because "we announced a change
 * that never shipped" is the failure this table exists to prevent.
 */
@Entity
@Table(name = "release")
@Getter
@Setter
@NoArgsConstructor
public class Release {

    @Id
    @Column(name = "release_id", nullable = false, updatable = false)
    private UUID releaseId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "repo_name", nullable = false, length = 255)
    private String repoName;

    @Column(name = "version", nullable = false, length = 100)
    private String version;

    @Column(name = "from_ref", nullable = false, length = 255)
    private String fromRef;

    @Column(name = "to_ref", nullable = false, length = 255)
    private String toRef;

    @Column(name = "resolved_pr_count")
    private Integer resolvedPrCount;

    /** JSON array of {@code {prNumber, reason, evidence}} — reverts, non-release commits. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "excluded_prs")
    private String excludedPrs;

    @Column(name = "aggregate_risk_score")
    private Integer aggregateRiskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_risk_level", length = 20)
    private RiskLevel aggregateRiskLevel;

    @Column(name = "readiness_score")
    private Integer readinessScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "readiness_status", length = 30)
    private ReadinessStatus readinessStatus;

    /**
     * The deployment strategy the release inherits from its riskiest included
     * pull request — see {@link com.gyansys.intellirelease.domain.deployment.DeploymentStrategyEngine#aggregate}.
     * Computed when the release is built, not when created; null until then.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deployment_strategy")
    private String deploymentStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "deployment_strategy_type", length = 20)
    private DeploymentStrategyType deploymentStrategyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReleaseStatus status = ReleaseStatus.DRAFT;

    // --- Release-level engine output (v2) ---------------------------------
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "impact_analysis")
    private String impactAnalysis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "regression_recommendation")
    private String regressionRecommendation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration_drift")
    private String configurationDrift;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "readiness_factors")
    private String readinessFactors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attention_flags")
    private String attentionFlags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_synthesis")
    private String aiSynthesis;

    @Column(name = "ai_fallback_used", nullable = false)
    private boolean aiFallbackUsed;

    /** QA sign-off state feeding Deployment Readiness. Seeded in the POC. */
    @Column(name = "qa_signed_off", nullable = false)
    private boolean qaSignedOff;

    @Column(name = "regression_executed_count")
    private Integer regressionExecutedCount;

    @Column(name = "built_at")
    private OffsetDateTime builtAt;

    @Column(name = "analyzed_at")
    private OffsetDateTime analyzedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Human-asserted confirmation that this version is actually running in
     * the target environment. Deliberately independent of {@link #status} —
     * {@link com.gyansys.intellirelease.model.enums.ReleaseStatus#RELEASED}
     * means communications were sent, which is a different fact from the
     * code being live, and the two do not always happen in that order.
     */
    @Column(name = "deployed_at")
    private OffsetDateTime deployedAt;

    @Column(name = "deployed_by", length = 255)
    private String deployedBy;

    public boolean isDeployed() {
        return deployedAt != null;
    }

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "release_pr",
            joinColumns = @JoinColumn(name = "release_id"),
            inverseJoinColumns = @JoinColumn(name = "pr_id")
    )
    private Set<PullRequest> pullRequests = new LinkedHashSet<>();

    public static Release create(String tenantId, String repoName, String version,
                                 String fromRef, String toRef) {
        Release release = new Release();
        release.releaseId = UUID.randomUUID();
        release.tenantId = tenantId;
        release.repoName = repoName;
        release.version = version;
        release.fromRef = fromRef;
        release.toRef = toRef;
        release.status = ReleaseStatus.DRAFT;
        release.createdAt = OffsetDateTime.now();
        return release;
    }
}
