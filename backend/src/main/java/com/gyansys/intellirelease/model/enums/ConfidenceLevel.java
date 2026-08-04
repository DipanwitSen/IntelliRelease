package com.gyansys.intellirelease.model.enums;

/**
 * Confidence attached to every derived claim. A recommendation without a
 * confidence class is a claim we cannot defend, so the engines never emit one.
 */
public enum ConfidenceLevel {

    HIGH,
    MEDIUM,
    LOW
}
