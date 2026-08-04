package com.gyansys.intellirelease.model.enums;

/**
 * Impact classification. The Impact Analysis Engine never elevates POTENTIAL
 * to CONFIRMED without direct evidence — that separation is the whole point.
 */
public enum ImpactType {

    /** Directly changed capability. Evidence is the changed file itself. */
    CONFIRMED,

    /** Reachable via a known dependency edge. Verify before relying on it. */
    POTENTIAL
}
