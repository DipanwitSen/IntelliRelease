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

    /**
     * Raw content of one file at a specific ref (typically a merge commit
     * SHA), decoded from the provider's transport encoding.
     *
     * <p>Used only by deterministic parsers — the ImpEx Analysis Engine reads
     * a file this way to count and link what it declares. Nothing this
     * returns is ever forwarded to the AI service; that invariant lives in
     * the callers, not here.
     *
     * @return empty when the provider is unreachable, unconfigured, or the
     *         file does not exist at that ref
     */
    Optional<String> fetchFileContent(String repoFullName, String path, String ref);

    /** Whether credentials are present. False means live calls are skipped. */
    boolean isConfigured();
}
