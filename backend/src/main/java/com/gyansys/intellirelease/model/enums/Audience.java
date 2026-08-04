package com.gyansys.intellirelease.model.enums;

/**
 * The four audiences. One underlying truth, four translations.
 *
 * <p>{@link #CLIENT} is the only externally-facing audience, and it is the one
 * the approval gate structurally blocks.
 */
public enum Audience {

    DEVELOPER(false),
    QA(false),
    BUSINESS(false),
    CLIENT(true);

    private final boolean externallyFacing;

    Audience(boolean externallyFacing) {
        this.externallyFacing = externallyFacing;
    }

    /**
     * Externally-facing notes require human approval before dispatch. This is
     * enforced in {@link com.gyansys.intellirelease.application.ApprovalService},
     * never in the UI.
     */
    public boolean isExternallyFacing() {
        return externallyFacing;
    }
}
