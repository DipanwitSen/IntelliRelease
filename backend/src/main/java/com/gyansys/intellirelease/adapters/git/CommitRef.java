package com.gyansys.intellirelease.adapters.git;

import java.time.OffsetDateTime;

/**
 * One commit in the release range, with the trailers the Release Builder reads.
 *
 * @param sha             full commit SHA
 * @param message         full commit message including trailers
 * @param author          commit author
 * @param committedAt     commit timestamp
 * @param cherryPickedFrom source SHA from a {@code (cherry picked from commit ...)}
 *                        trailer, when present
 * @param revertsSha      SHA this commit reverts, parsed from a revert message
 * @param pullRequestNumber PR number parsed from a merge commit subject, when present
 */
public record CommitRef(
        String sha,
        String message,
        String author,
        OffsetDateTime committedAt,
        String cherryPickedFrom,
        String revertsSha,
        Integer pullRequestNumber
) {

    public boolean isRevert() {
        return revertsSha != null && !revertsSha.isBlank();
    }

    public boolean isCherryPick() {
        return cherryPickedFrom != null && !cherryPickedFrom.isBlank();
    }

    public String shortSha() {
        return sha == null || sha.length() < 7 ? sha : sha.substring(0, 7);
    }
}
