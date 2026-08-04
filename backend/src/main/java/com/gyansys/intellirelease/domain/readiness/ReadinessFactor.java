package com.gyansys.intellirelease.domain.readiness;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One contributing signal in the readiness composition, with its evidence.
 *
 * @param factor       stable identifier
 * @param label        what the release manager reads
 * @param contribution points awarded (negative for attention flags)
 * @param maximum      points available for this factor
 * @param blocker      true when this factor alone can force NOT_READY
 * @param evidence     why it scored what it scored
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadinessFactor(
        String factor,
        String label,
        int contribution,
        int maximum,
        boolean blocker,
        String evidence
) {

    public static ReadinessFactor of(String factor, String label, int contribution, int maximum, String evidence) {
        return new ReadinessFactor(factor, label, contribution, maximum, false, evidence);
    }

    public static ReadinessFactor blocker(String factor, String label, int contribution, int maximum, String evidence) {
        return new ReadinessFactor(factor, label, contribution, maximum, true, evidence);
    }

    /** Attention flags are penalties: negative contribution, no maximum. */
    public static ReadinessFactor attention(String factor, String label, int penalty, String evidence) {
        return new ReadinessFactor(factor, label, -Math.abs(penalty), 0, false, evidence);
    }
}
