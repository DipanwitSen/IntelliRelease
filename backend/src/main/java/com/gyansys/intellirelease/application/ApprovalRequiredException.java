package com.gyansys.intellirelease.application;

/**
 * Thrown when something tries to dispatch client-facing communication that no
 * human has approved.
 *
 * <p>Mapped to <strong>409 APPROVAL_REQUIRED</strong>. This exception existing —
 * and being thrown from the service layer rather than checked in a controller —
 * is what makes the governance claim structural: every path to dispatch goes
 * through {@link ApprovalService}, and every unapproved path ends here.
 */
public class ApprovalRequiredException extends RuntimeException {

    public ApprovalRequiredException(String message) {
        super(message);
    }
}
