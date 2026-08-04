package com.gyansys.intellirelease.model;

import com.gyansys.intellirelease.model.enums.ConfidenceLevel;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Output of the Cleanup Intelligence Engine, which runs on a schedule rather
 * than per-PR. Advisory only: a human approves any retention change through the
 * same approval workflow as any other communication.
 */
@Entity
@Table(name = "cleanup_recommendation")
@Getter
@Setter
@NoArgsConstructor
public class CleanupRecommendation {

    @Id
    @Column(name = "recommendation_id", nullable = false, updatable = false)
    private UUID recommendationId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "cronjob_name", nullable = false, length = 255)
    private String cronjobName;

    @Column(name = "current_retention_days")
    private Integer currentRetentionDays;

    @Column(name = "recommended_retention_days")
    private Integer recommendedRetentionDays;

    @Column(name = "table_size_gb")
    private BigDecimal tableSizeGb;

    @Column(name = "estimated_reduction_pct")
    private Integer estimatedReductionPct;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", nullable = false, length = 20)
    private ConfidenceLevel confidence;

    /** Whether the cronjob itself is executing successfully. Feeds Readiness. */
    @Column(name = "healthy", nullable = false)
    private boolean healthy = true;

    @Column(name = "rationale")
    private String rationale;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence")
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "provenance_class", nullable = false, length = 30)
    private ProvenanceClass provenanceClass = ProvenanceClass.RULE_OUTPUT;

    @Column(name = "analyzed_at", nullable = false)
    private OffsetDateTime analyzedAt;

    public static CleanupRecommendation create(String tenantId, String cronjobName) {
        CleanupRecommendation rec = new CleanupRecommendation();
        rec.recommendationId = UUID.randomUUID();
        rec.tenantId = tenantId;
        rec.cronjobName = cronjobName;
        rec.provenanceClass = ProvenanceClass.RULE_OUTPUT;
        rec.analyzedAt = OffsetDateTime.now();
        return rec;
    }
}
