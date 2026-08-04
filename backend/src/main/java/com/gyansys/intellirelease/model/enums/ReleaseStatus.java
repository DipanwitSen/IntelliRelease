package com.gyansys.intellirelease.model.enums;

/** Lifecycle of a release record as it moves through the pipeline. */
public enum ReleaseStatus {

    /** Created with from/to refs; contents not yet resolved from Git. */
    DRAFT,

    /** Release Builder has resolved the true PR set (cherry-picks, reverts). */
    BUILT,

    /** Impact, Regression, Risk, Drift and Readiness have all run. */
    ANALYZED,

    /** Four audience notes drafted. Client note is not yet approved. */
    NOTES_GENERATED,

    /** Client-facing communication approved by a human. */
    APPROVED,

    /** Communications dispatched and audited. */
    RELEASED
}
