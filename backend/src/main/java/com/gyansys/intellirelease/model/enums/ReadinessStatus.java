package com.gyansys.intellirelease.model.enums;

/**
 * Deployment readiness verdict. Advisory: it informs the release manager's
 * go/no-go call, it never overrides it.
 */
public enum ReadinessStatus {

    READY,
    READY_WITH_WARNINGS,
    NOT_READY
}
