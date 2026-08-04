package com.gyansys.intellirelease.model.enums;

/**
 * Configuration drift classification. The engine performs a structural diff,
 * not a string diff: {@code payment.timeout=8s -> 2s} is MATERIAL, a reformatted
 * comment is COSMETIC. Only MATERIAL drift reaches Deployment Readiness.
 */
public enum DriftClassification {

    /** Behaviour-affecting. Feeds Readiness as a warning signal. */
    MATERIAL,

    /** Comment or formatting only. Recorded, never escalated. */
    COSMETIC
}
