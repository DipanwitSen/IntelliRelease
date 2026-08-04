package com.gyansys.intellirelease.model.enums;

/**
 * The five provenance classes. Non-negotiable: every stored piece of
 * intelligence carries one, and the UI renders it on every card.
 *
 * <p>This is the guarantee that lets an auditor trust the output — it makes
 * "how do you know?" answerable for every claim on the page.
 */
public enum ProvenanceClass {

    /** Directly observed. "PR #102 merged 2026-07-28 14:32 by Rahul K." */
    FACT("Directly observed system fact"),

    /** Deterministically derived from facts. "items.xml changed -> Type System Change" */
    DERIVED_FACT("Deterministically derived from observed facts"),

    /** Rule engine output with evidence. "Risk 40 MEDIUM: type system +20, impex +10, large +10" */
    RULE_OUTPUT("Explainable rule engine output with evidence"),

    /** AI-generated explanation. Never a fact, never a number, never a decision. */
    AI_INFERENCE("AI-generated explanation of deterministic output"),

    /** Explicitly undetermined. Said out loud rather than guessed. */
    UNKNOWN("Explicitly undetermined");

    private final String description;

    ProvenanceClass(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
