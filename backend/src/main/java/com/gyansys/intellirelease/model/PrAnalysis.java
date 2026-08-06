package com.gyansys.intellirelease.model;

import com.gyansys.intellirelease.model.enums.DeploymentStrategyType;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import com.gyansys.intellirelease.model.enums.ReadinessStatus;
import com.gyansys.intellirelease.model.enums.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Everything the deterministic engines concluded about one pull request,
 * plus the AI's explanation of those conclusions.
 *
 * <p>The ordering of the fields mirrors the philosophy chain. Note what the AI
 * column is <em>not</em> next to: {@code risk_score} and {@code risk_level} are
 * produced by {@link com.gyansys.intellirelease.domain.risk.RiskEngine} alone.
 * {@code ai_summary} can only ever narrate them.
 *
 * <p>Shares its primary key with {@link PullRequest#getPrId()}.
 */
@Entity
@Table(name = "pr_analysis")
@Getter
@Setter
@NoArgsConstructor
public class PrAnalysis {

    @Id
    @Column(name = "pr_id", nullable = false, updatable = false)
    private UUID prId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    // --- Deterministic: SAP Commerce Context Engine (DERIVED_FACT) ---------
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sap_commerce_context", nullable = false)
    private String sapCommerceContext;

    // --- Deterministic: Deployment Strategy Engine (RULE_OUTPUT) -----------
    // Runs immediately after the Context Engine, off the same file
    // classifications — see DeploymentStrategyEngine.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deployment_strategy")
    private String deploymentStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "deployment_strategy_type", length = 20)
    private DeploymentStrategyType deploymentStrategyType;

    // --- Human decision on top of the recommendation above -----------------
    @Enumerated(EnumType.STRING)
    @Column(name = "confirmed_deployment_strategy", length = 20)
    private DeploymentStrategyType confirmedDeploymentStrategy;

    @Column(name = "confirmed_by", length = 100)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    // --- Deterministic: rule engines (RULE_OUTPUT) ------------------------
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "impact_analysis", nullable = false)
    private String impactAnalysis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "regression_recommendation")
    private String regressionRecommendation;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_reasons", nullable = false)
    private String riskReasons;

    /** Which version of the weighted rule set produced the score above. */
    @Column(name = "risk_policy_version", length = 20)
    private String riskPolicyVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration_drift")
    private String configurationDrift;

    // --- AI: explanation only (AI_INFERENCE) ------------------------------
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_summary")
    private String aiSummary;

    /** True when the model failed schema validation and templates were used. */
    @Column(name = "ai_fallback_used", nullable = false)
    private boolean aiFallbackUsed;

    // --- Deterministic: composite (RULE_OUTPUT) ---------------------------
    @Column(name = "deployment_readiness_score")
    private Integer deploymentReadinessScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "deployment_readiness_status", length = 30)
    private ReadinessStatus deploymentReadinessStatus;

    /**
     * Provenance of the record as a whole. Nested payloads carry their own
     * finer-grained class, which is what the UI renders per card.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "provenance_class", nullable = false, length = 30)
    private ProvenanceClass provenanceClass = ProvenanceClass.RULE_OUTPUT;

    // --- Cost/traceability instrumentation from day one -------------------
    @Column(name = "model_provider", length = 100)
    private String modelProvider;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    public static PrAnalysis forPullRequest(UUID prId, String tenantId) {
        PrAnalysis analysis = new PrAnalysis();
        analysis.prId = prId;
        analysis.tenantId = tenantId;
        analysis.provenanceClass = ProvenanceClass.RULE_OUTPUT;
        analysis.generatedAt = OffsetDateTime.now();
        return analysis;
    }
}
