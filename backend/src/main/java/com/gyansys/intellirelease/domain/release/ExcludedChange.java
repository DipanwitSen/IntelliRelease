package com.gyansys.intellirelease.domain.release;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A change that was in the range but is not in the release, and the evidence
 * for why.
 *
 * <p>Exclusions are recorded, never silent. "This PR was merged then reverted
 * before release, so it is not in your changelog" is the sentence that stops a
 * client being told about a feature they will not receive.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExcludedChange(
        Integer prNumber,
        String sha,
        String title,
        String reason,
        String evidence
) {

    public static ExcludedChange reverted(Integer prNumber, String sha, String title, String revertingSha) {
        return new ExcludedChange(prNumber, sha, title,
                "Merged then reverted before release",
                "Commit " + shortSha(sha) + " is reverted by " + shortSha(revertingSha)
                        + " within this release range; the two cancel out");
    }

    private static String shortSha(String sha) {
        return sha == null || sha.length() < 7 ? String.valueOf(sha) : sha.substring(0, 7);
    }
}
