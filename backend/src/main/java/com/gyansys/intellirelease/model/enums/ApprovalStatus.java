package com.gyansys.intellirelease.model.enums;

import java.util.Set;

/**
 * Approval state machine: DRAFT -&gt; REVIEW -&gt; APPROVED | REJECTED -&gt; SENT.
 *
 * <p>The transition table lives here so the gate cannot be bypassed by calling
 * a different service. The Angular UI displays state; it can never set it.
 */
public enum ApprovalStatus {

    DRAFT,
    REVIEW,
    APPROVED,
    REJECTED,
    SENT;

    /** Legal successor states. Anything else is rejected with 409. */
    public Set<ApprovalStatus> allowedTransitions() {
        return switch (this) {
            case DRAFT -> Set.of(REVIEW, APPROVED, REJECTED);
            case REVIEW -> Set.of(APPROVED, REJECTED);
            // Re-approval after rejection is allowed; it re-enters review first.
            case REJECTED -> Set.of(REVIEW);
            case APPROVED -> Set.of(SENT, REJECTED);
            case SENT -> Set.of();
        };
    }

    public boolean canTransitionTo(ApprovalStatus target) {
        return allowedTransitions().contains(target);
    }

    /** Only an APPROVED note may be dispatched to an external audience. */
    public boolean isDispatchable() {
        return this == APPROVED;
    }
}
