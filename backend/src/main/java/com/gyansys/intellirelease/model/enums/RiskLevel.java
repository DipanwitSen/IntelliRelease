package com.gyansys.intellirelease.model.enums;

/**
 * Risk band. Derived from the deterministic score by
 * {@link com.gyansys.intellirelease.domain.risk.RiskPolicy} thresholds only —
 * the AI never produces or adjusts this value.
 */
public enum RiskLevel {

    LOW,
    MEDIUM,
    HIGH;

    /** Bands: LOW &lt; 30 · MEDIUM 30-59 · HIGH &gt;= 60. */
    public static RiskLevel fromScore(int score, int lowThreshold, int highThreshold) {
        if (score >= highThreshold) {
            return HIGH;
        }
        if (score >= lowThreshold) {
            return MEDIUM;
        }
        return LOW;
    }
}
