package com.gyansys.intellirelease.adapters.git;

import java.util.List;
import java.util.Optional;

/**
 * Source control adapter.
 *
 * <p>Only {@code GitHubProvider} is wired in the POC. GitLab, Azure DevOps and
 * Bitbucket implement this same interface — the deterministic engines never
 * learn which one is behind it, which is why onboarding a second provider is an
 * adapter rather than a rewrite.
 *
 * <p>Access is read-only and scoped. Nothing in this interface writes.
 */
public interface GitProvider {

    /** e.g. {@code github}. Stored on the webhook event for traceability. */
    String providerName();

    /**
     * Full detail for one merged pull request, including the changed file
     * manifest the Context Engine classifies.
     *
     * @return empty when the provider is unreachable or unconfigured — the
     *         caller degrades to webhook payload data rather than failing
     */
    Optional<PullRequestDetail> fetchPullRequest(String repoFullName, int prNumber);

    /**
     * Commits reachable from {@code toRef} but not {@code fromRef}: the Git
     * graph diff that gives a release its true contents. Not a date range —
     * dates lie about cherry-picks.
     */
    List<CommitRef> listCommitsBetween(String repoFullName, String fromRef, String toRef);

    /** Whether credentials are present. False means live calls are skipped. */
    boolean isConfigured();
}
