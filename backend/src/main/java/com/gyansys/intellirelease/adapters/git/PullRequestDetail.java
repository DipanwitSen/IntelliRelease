package com.gyansys.intellirelease.adapters.git;

import com.gyansys.intellirelease.domain.context.ChangedFile;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Normalised pull request detail, provider-agnostic.
 *
 * <p>Everything here is a FACT observed from source control. Keeping the shape
 * provider-neutral is what makes {@code GitLabProvider} or
 * {@code AzureDevOpsProvider} a new adapter rather than a new pipeline.
 */
public record PullRequestDetail(
        String repoFullName,
        int prNumber,
        String title,
        String description,
        String author,
        String branch,
        String mergeSha,
        OffsetDateTime mergedAt,
        List<ChangedFile> changedFiles,
        String ticketKey
) {

    public PullRequestDetail {
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
    }
}
