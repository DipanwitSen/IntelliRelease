package com.gyansys.intellirelease.domain.risk;

/**
 * The weighted rule set. Versioned and tunable per customer.
 *
 * <p>Every weight here is a number a human chose and can defend in a validation
 * review. That is the entire point: the same evidence produces the same score
 * every time, and the "why" is on the page. A model that emitted a plausible
 * percentage would be faster to build and impossible to defend.
 *
 * <p>Bump {@link #POLICY_VERSION} whenever a weight or threshold changes, so a
 * score computed last quarter stays explainable against the rules that produced
 * it rather than today's.
 */
public final class RiskPolicy {

    private RiskPolicy() {
    }

    public static final String POLICY_VERSION = "risk-policy-v1";

    // --- Weights ----------------------------------------------------------

    /** items.xml / type system: schema, generated code, possible reindex. */
    public static final int TYPE_SYSTEM_CHANGE = 20;

    /** Authentication, authorization, session handling. */
    public static final int SECURITY_CHANGE = 20;

    /** OCC or any published API contract consumed outside the platform. */
    public static final int API_CHANGE = 15;

    /** Checkout and payment: revenue-critical paths. */
    public static final int CHECKOUT_CHANGE = 15;

    /** Properties and YAML: behaviour changes without code changes. */
    public static final int CONFIGURATION_CHANGE = 10;

    /** More files than a reviewer can hold in their head at once. */
    public static final int LARGE_CHANGE = 10;

    /** Solr configuration and search services. */
    public static final int SEARCH_CHANGE = 10;

    /** No test file accompanied the change. */
    public static final int NO_TESTS = 10;

    /** Impex data import: changes data state on the target environment. */
    public static final int DATA_IMPORT = 10;

    /**
     * Files the Context Engine could not classify. Unknown is treated as risk,
     * never as safety.
     */
    public static final int UNCLASSIFIED_FILES = 5;

    // --- Thresholds -------------------------------------------------------

    /** File count above which {@link #LARGE_CHANGE} applies. */
    public static final int LARGE_CHANGE_FILE_COUNT = 15;

    /** Bands: LOW &lt; 30 · MEDIUM 30-59 · HIGH &gt;= 60. */
    public static final int LOW_THRESHOLD = 30;
    public static final int HIGH_THRESHOLD = 60;

    /** Scores are capped so a single enormous PR cannot exceed the scale. */
    public static final int MAX_SCORE = 100;
}
