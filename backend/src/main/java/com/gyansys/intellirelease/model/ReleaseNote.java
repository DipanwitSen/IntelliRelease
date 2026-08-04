package com.gyansys.intellirelease.model;

import com.gyansys.intellirelease.model.enums.ApprovalStatus;
import com.gyansys.intellirelease.model.enums.Audience;
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

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One audience-specific communication for one release.
 *
 * <p>Four rows per release, all generated from the same deterministic truth.
 * The CLIENT row is the one the approval gate blocks: it cannot reach
 * {@link ApprovalStatus#SENT} without passing through
 * {@link ApprovalStatus#APPROVED} with a named human on the record.
 */
@Entity
@Table(name = "release_note")
@Getter
@Setter
@NoArgsConstructor
public class ReleaseNote {

    @Id
    @Column(name = "note_id", nullable = false, updatable = false)
    private UUID noteId;

    @Column(name = "release_id", nullable = false)
    private UUID releaseId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false, length = 30)
    private Audience audience;

    @Column(name = "content", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 30)
    private ApprovalStatus approvalStatus = ApprovalStatus.DRAFT;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "rejected_reason")
    private String rejectedReason;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    /**
     * AI_INFERENCE when the model produced the prose, RULE_OUTPUT when the
     * deterministic template fallback did. The reviewer sees which.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "provenance_class", nullable = false, length = 30)
    private ProvenanceClass provenanceClass = ProvenanceClass.AI_INFERENCE;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public static ReleaseNote create(UUID releaseId, String tenantId, Audience audience,
                                     String content, ProvenanceClass provenanceClass) {
        ReleaseNote note = new ReleaseNote();
        note.noteId = UUID.randomUUID();
        note.releaseId = releaseId;
        note.tenantId = tenantId;
        note.audience = audience;
        note.content = content;
        note.provenanceClass = provenanceClass;
        note.approvalStatus = ApprovalStatus.DRAFT;
        note.createdAt = OffsetDateTime.now();
        return note;
    }
}
