package com.gyansys.intellirelease.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A merged pull request on an SAP Commerce repository.
 *
 * <p>Everything on this entity is a FACT — observed from GitHub, never inferred.
 * The derived intelligence lives on {@link PrAnalysis} so that fact and
 * inference are never stored in the same shape.
 *
 * <p>{@code (tenant_id, repo_name, merge_sha)} is unique: the same merge
 * delivered twice produces one row, not two.
 */
@Entity
@Table(name = "pull_request")
@Getter
@Setter
@NoArgsConstructor
public class PullRequest {

    @Id
    @Column(name = "pr_id", nullable = false, updatable = false)
    private UUID prId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "repo_name", nullable = false, length = 255)
    private String repoName;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(name = "merge_sha", nullable = false, length = 100)
    private String mergeSha;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "author", length = 255)
    private String author;

    @Column(name = "branch", length = 255)
    private String branch;

    /** e.g. LILLY-4812. Tagged UNKNOWN downstream when absent. */
    @Column(name = "ticket_key", length = 100)
    private String ticketKey;

    @Column(name = "merged_at")
    private OffsetDateTime mergedAt;

    /** JSON array of {@code {path, changeType, additions, deletions}}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_files")
    private String changedFiles;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_metadata")
    private String rawMetadata;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public static PullRequest create(String tenantId, String repoName, Integer prNumber, String mergeSha) {
        PullRequest pr = new PullRequest();
        pr.prId = UUID.randomUUID();
        pr.tenantId = tenantId;
        pr.repoName = repoName;
        pr.prNumber = prNumber;
        pr.mergeSha = mergeSha;
        pr.createdAt = OffsetDateTime.now();
        return pr;
    }
}
