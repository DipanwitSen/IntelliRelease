package com.gyansys.intellirelease.adapters.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.gyansys.intellirelease.config.IntelliReleaseProperties;
import com.gyansys.intellirelease.domain.context.ChangedFile;
import com.gyansys.intellirelease.infra.JsonMapper;
import com.gyansys.intellirelease.model.enums.ChangeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * GitHub adapter: webhooks in, REST API out.
 *
 * <p>Two-way by design. The webhook says "something happened"; the REST API
 * answers "what exactly changed?" Both directions are authenticated and
 * read-only — this adapter has no method that writes to a repository.
 *
 * <p>Every remote call degrades to {@link Optional#empty()} rather than
 * throwing. An unreachable GitHub should downgrade the analysis and say so, not
 * take the ingestion pipeline down with it.
 */
@Component
public class GitHubProvider implements GitProvider {

    private static final Logger log = LoggerFactory.getLogger(GitHubProvider.class);

    /** Matches "LILLY-4812" style ticket keys in a PR title or body. */
    private static final Pattern TICKET_PATTERN = Pattern.compile("\\b([A-Z][A-Z0-9]+-\\d+)\\b");

    /** Git's own trailer, appended by `git cherry-pick -x`. */
    private static final Pattern CHERRY_PICK_PATTERN =
            Pattern.compile("cherry picked from commit ([0-9a-f]{7,40})", Pattern.CASE_INSENSITIVE);

    /** Git's own revert subject, produced by `git revert`. */
    private static final Pattern REVERT_PATTERN =
            Pattern.compile("This reverts commit ([0-9a-f]{7,40})", Pattern.CASE_INSENSITIVE);

    private static final Pattern MERGE_PR_PATTERN =
            Pattern.compile("Merge pull request #(\\d+)");

    private final IntelliReleaseProperties.GitHub config;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;

    public GitHubProvider(IntelliReleaseProperties properties, JsonMapper jsonMapper) {
        this.config = properties.github();
        this.jsonMapper = jsonMapper;
        this.restClient = RestClient.builder()
                .baseUrl(this.config.apiBaseUrl())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    @Override
    public String providerName() {
        return "github";
    }

    @Override
    public boolean isConfigured() {
        return config.hasToken();
    }

    @Override
    public Optional<PullRequestDetail> fetchPullRequest(String repoFullName, int prNumber) {
        if (!isConfigured()) {
            log.debug("GitHub API not configured; caller will fall back to webhook payload data");
            return Optional.empty();
        }
        try {
            JsonNode pr = get("/repos/" + repoFullName + "/pulls/" + prNumber);
            if (pr == null) {
                return Optional.empty();
            }
            List<ChangedFile> files = fetchChangedFiles(repoFullName, prNumber);
            return Optional.of(toDetail(repoFullName, pr, files));
        } catch (RuntimeException exception) {
            log.warn("GitHub API call failed for {}#{}: {}", repoFullName, prNumber, exception.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<CommitRef> listCommitsBetween(String repoFullName, String fromRef, String toRef) {
        if (!isConfigured()) {
            return List.of();
        }
        try {
            // The compare endpoint is the Git graph diff: commits reachable from
            // toRef but not fromRef. This is what makes cherry-picks correct.
            JsonNode comparison = get("/repos/" + repoFullName + "/compare/" + fromRef + "..." + toRef);
            if (comparison == null || !comparison.has("commits")) {
                return List.of();
            }

            List<CommitRef> commits = new ArrayList<>();
            for (JsonNode node : comparison.get("commits")) {
                commits.add(toCommitRef(node));
            }
            return commits;
        } catch (RuntimeException exception) {
            log.warn("GitHub compare failed for {} {}...{}: {}",
                    repoFullName, fromRef, toRef, exception.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<String> fetchFileContent(String repoFullName, String path, String ref) {
        if (!isConfigured() || ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        try {
            String encodedPath = Arrays.stream(path.split("/"))
                    .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                    .collect(Collectors.joining("/"));
            JsonNode file = get("/repos/" + repoFullName + "/contents/" + encodedPath + "?ref=" + ref);
            if (file == null || !file.has("content")) {
                return Optional.empty();
            }
            String encoded = file.path("content").asText("");
            if (!"base64".equals(file.path("encoding").asText("base64"))) {
                return Optional.of(encoded);
            }
            byte[] decoded = Base64.getMimeDecoder().decode(encoded);
            return Optional.of(new String(decoded, StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            log.warn("GitHub content fetch failed for {}/{}@{}: {}", repoFullName, path, ref, exception.getMessage());
            return Optional.empty();
        }
    }

    /** Parses a webhook body into the same shape the REST API produces. */
    public Optional<PullRequestDetail> parseWebhookPayload(String rawPayload) {
        JsonNode root = jsonMapper.readTree(rawPayload);
        JsonNode pr = root.path("pull_request");
        if (pr.isMissingNode()) {
            return Optional.empty();
        }
        String repoFullName = root.path("repository").path("full_name").asText(null);
        if (repoFullName == null) {
            return Optional.empty();
        }

        // Some fixtures embed the file manifest so the demo runs without a live
        // API call. Labelled as such in the UI when that path is taken.
        List<ChangedFile> files = new ArrayList<>();
        for (JsonNode file : root.path("changed_files_detail")) {
            files.add(toChangedFile(file));
        }

        return Optional.of(toDetail(repoFullName, pr, files));
    }

    // ------------------------------------------------------------------

    private List<ChangedFile> fetchChangedFiles(String repoFullName, int prNumber) {
        List<ChangedFile> files = new ArrayList<>();
        // GitHub paginates at 100; a PR beyond 300 files is already flagged as a
        // large change, so three pages is enough for the risk signal.
        for (int page = 1; page <= 3; page++) {
            JsonNode response = get("/repos/" + repoFullName + "/pulls/" + prNumber
                    + "/files?per_page=100&page=" + page);
            if (response == null || !response.isArray() || response.isEmpty()) {
                break;
            }
            for (JsonNode file : response) {
                files.add(toChangedFile(file));
            }
            if (response.size() < 100) {
                break;
            }
        }
        return files;
    }

    private JsonNode get(String path) {
        RestClient.RequestHeadersSpec<?> request = restClient.get().uri(path);
        if (config.hasToken()) {
            request = request.header("Authorization", "Bearer " + config.token());
        }
        return request.retrieve().body(JsonNode.class);
    }

    private PullRequestDetail toDetail(String repoFullName, JsonNode pr, List<ChangedFile> files) {
        String title = pr.path("title").asText(null);
        String body = pr.path("body").asText(null);

        return new PullRequestDetail(
                repoFullName,
                pr.path("number").asInt(),
                title,
                body,
                pr.path("user").path("login").asText(null),
                pr.path("head").path("ref").asText(null),
                firstNonBlank(pr.path("merge_commit_sha").asText(null), pr.path("head").path("sha").asText(null)),
                parseTimestamp(pr.path("merged_at").asText(null)),
                files,
                extractTicketKey(title, body)
        );
    }

    private ChangedFile toChangedFile(JsonNode file) {
        return new ChangedFile(
                file.path("filename").asText(file.path("path").asText("")),
                ChangeType.fromGitHubStatus(file.path("status").asText(null)),
                file.path("additions").asInt(0),
                file.path("deletions").asInt(0)
        );
    }

    private CommitRef toCommitRef(JsonNode node) {
        String message = node.path("commit").path("message").asText("");
        return new CommitRef(
                node.path("sha").asText(null),
                message,
                node.path("commit").path("author").path("name").asText(null),
                parseTimestamp(node.path("commit").path("author").path("date").asText(null)),
                firstGroup(CHERRY_PICK_PATTERN, message),
                firstGroup(REVERT_PATTERN, message),
                parsePrNumber(message)
        );
    }

    /** Ticket key from the title first, then the body. Null when absent — tagged UNKNOWN downstream. */
    private String extractTicketKey(String title, String body) {
        String fromTitle = firstGroup(TICKET_PATTERN, title);
        return fromTitle != null ? fromTitle : firstGroup(TICKET_PATTERN, body);
    }

    private static Integer parsePrNumber(String message) {
        String value = firstGroup(MERGE_PR_PATTERN, message);
        return value == null ? null : Integer.valueOf(value);
    }

    private static String firstGroup(Pattern pattern, String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static OffsetDateTime parseTimestamp(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() && !"null".equals(first) ? first : second;
    }
}
