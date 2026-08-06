package com.gyansys.intellirelease.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.gyansys.intellirelease.domain.integration.IntegrationContextExtractor;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.ImpactedInterface;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.IntegrationContext;
import com.gyansys.intellirelease.infra.JsonMapper;
import com.gyansys.intellirelease.model.PrAnalysis;
import com.gyansys.intellirelease.model.PullRequest;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Assembles the structured package that is handed to the language model, and
 * exposes it so a reviewer can audit exactly what was sent.
 *
 * <h2>Why this exists</h2>
 * The architectural rule is that raw GitHub payloads and source code never
 * reach the model. That rule is easy to state and easy to violate quietly. This
 * class makes it inspectable: the same package the AI service received is
 * returned to the UI verbatim, so "we don't send your code" is something a
 * security reviewer can verify rather than something they have to believe.
 *
 * <h2>What gets removed</h2>
 * File <em>paths</em> and their deterministic classifications go in. File
 * <em>contents</em> never do. On top of that, anything resembling a credential
 * is redacted from the free-text fields that do travel — a PR title or branch
 * name is written by a human and occasionally contains a token.
 */
@Service
public class ContextPackageBuilder {

    private static final String ENGINE_VERSION = "context-package/1.0.0";

    /**
     * Patterns for values that must never leave this process.
     *
     * <p>Deliberately broad. A false positive costs a redacted word in a PR
     * title; a false negative costs a credential in a third party's logs.
     */
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(gh[pousr]_[A-Za-z0-9]{16,})\\b"),
            Pattern.compile("(?i)\\b(xox[baprs]-[A-Za-z0-9-]{10,})\\b"),
            Pattern.compile("(?i)\\b(sk-[A-Za-z0-9]{20,})\\b"),
            Pattern.compile("(?i)\\b(AKIA[0-9A-Z]{16})\\b"),
            Pattern.compile("(?i)(password|passwd|secret|token|api[_-]?key|client[_-]?secret)\\s*[=:]\\s*\\S+"),
            Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]{20,}=*"),
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"));

    private static final String REDACTED = "[REDACTED]";

    /** Paths that carry no analytical signal and would only dilute the package. */
    private static final List<Pattern> NOISE_PATHS = List.of(
            Pattern.compile("(?i).*/(node_modules|dist|build|target|out|coverage|__pycache__)/.*"),
            Pattern.compile("(?i).*\\.(lock|min\\.js|min\\.css|map|png|jpe?g|gif|svg|ico|woff2?|ttf|eot|zip|gz|jar|war|class|pdf)$"),
            Pattern.compile("(?i).*(package-lock\\.json|yarn\\.lock|pnpm-lock\\.yaml|Gemfile\\.lock)$"),
            Pattern.compile("(?i).*/generated/.*"),
            Pattern.compile("(?i).*\\.generated\\..*"));

    private final JsonMapper jsonMapper;
    private final IntegrationContextExtractor integrationExtractor;

    public ContextPackageBuilder(JsonMapper jsonMapper, IntegrationContextExtractor integrationExtractor) {
        this.jsonMapper = jsonMapper;
        this.integrationExtractor = integrationExtractor;
    }

    /**
     * Builds the package for one pull request.
     *
     * @param pullRequest the captured change (FACT)
     * @param analysis    its deterministic analysis, or null if it has not run
     */
    public ContextPackage build(PullRequest pullRequest, PrAnalysis analysis) {
        List<String> allPaths = changedPaths(pullRequest);
        List<String> included = new ArrayList<>();
        Map<String, Long> exclusionReasons = new LinkedHashMap<>();

        for (String path : allPaths) {
            String reason = exclusionReasonFor(path);
            if (reason == null) {
                included.add(path);
            } else {
                exclusionReasons.merge(reason, 1L, Long::sum);
            }
        }

        List<ContextSection> sections = new ArrayList<>();

        sections.add(new ContextSection(
                "change", "Change identity", ProvenanceClass.FACT, 1,
                redact(String.join("\n",
                        "repository: " + nullSafe(pullRequest.getRepoName()),
                        "pullRequest: #" + pullRequest.getPrNumber(),
                        "title: " + nullSafe(pullRequest.getTitle()),
                        "ticket: " + nullSafe(pullRequest.getTicketKey()),
                        "branch: " + nullSafe(pullRequest.getBranch()),
                        "mergedAt: " + nullSafe(String.valueOf(pullRequest.getMergedAt()))))));

        sections.add(new ContextSection(
                "files", "Changed file paths", ProvenanceClass.FACT, included.size(),
                // Paths only. No diff, no file content, no line ranges.
                String.join("\n", included)));

        if (analysis != null) {
            addIfPresent(sections, "commerceContext", "SAP Commerce classification",
                    ProvenanceClass.DERIVED_FACT, analysis.getSapCommerceContext());
            addIfPresent(sections, "impact", "Impact analysis",
                    ProvenanceClass.DERIVED_FACT, analysis.getImpactAnalysis());
            addIfPresent(sections, "risk", "Risk assessment",
                    ProvenanceClass.RULE_OUTPUT, analysis.getRiskReasons());
            addIfPresent(sections, "regression", "Regression recommendation",
                    ProvenanceClass.RULE_OUTPUT, analysis.getRegressionRecommendation());
            addIfPresent(sections, "drift", "Configuration drift",
                    ProvenanceClass.DERIVED_FACT, analysis.getConfigurationDrift());
        }

        IntegrationContext integration = integrationExtractor.extract(included);
        sections.add(new ContextSection(
                "integration", "Integration impact", ProvenanceClass.DERIVED_FACT,
                integration.impactedInterfaces().size(),
                renderIntegration(integration)));

        int rawBytes = utf8Length(String.join("\n", allPaths))
                + utf8Length(nullSafe(pullRequest.getDescription()));
        int packagedBytes = sections.stream()
                .mapToInt(section -> utf8Length(section.content()))
                .sum();

        return new ContextPackage(
                "ctx-" + pullRequest.getPrId(),
                pullRequest.getPrId().toString(),
                null,
                java.time.OffsetDateTime.now().toString(),
                ENGINE_VERSION,
                List.copyOf(sections),
                new ContextPackageStats(
                        allPaths.size(), included.size(), allPaths.size() - included.size(),
                        rawBytes, packagedBytes,
                        rawBytes == 0 ? 1.0 : round2((double) packagedBytes / rawBytes),
                        estimateTokens(packagedBytes),
                        exclusionReasons.entrySet().stream()
                                .map(entry -> new CountEntry(entry.getKey(), entry.getKey(), entry.getValue()))
                                .toList()),
                List.of(
                        "File contents and diffs are never included — only paths and their deterministic classification.",
                        "Credential-shaped values are redacted from every free-text field before packaging.",
                        "No GitHub API payload is forwarded; every fact here was captured and stored first."));
    }

    /* ------------------------------------------------------------ helpers */

    private void addIfPresent(List<ContextSection> sections, String key, String label,
                              ProvenanceClass provenance, String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        JsonNode node = jsonMapper.readTree(json);
        int count = node.isArray() ? node.size() : 1;
        sections.add(new ContextSection(key, label, provenance, count, redact(json)));
    }

    private List<String> changedPaths(PullRequest pullRequest) {
        JsonNode files = jsonMapper.readTree(pullRequest.getChangedFiles());
        List<String> paths = new ArrayList<>();
        if (files != null && files.isArray()) {
            files.forEach(entry -> {
                // The manifest is stored either as bare strings or as objects
                // with a path field, depending on the provider.
                JsonNode pathNode = entry.isTextual() ? entry : entry.get("path");
                if (pathNode != null && pathNode.isTextual()) {
                    paths.add(pathNode.asText());
                }
            });
        }
        return paths;
    }

    /** Null when the path should be included; otherwise why it was dropped. */
    private static String exclusionReasonFor(String path) {
        for (Pattern pattern : NOISE_PATHS) {
            if (pattern.matcher(path).matches()) {
                if (path.matches("(?i).*/(node_modules|dist|build|target|out|coverage|__pycache__)/.*")) {
                    return "Vendor or build output";
                }
                if (path.matches("(?i).*(lock)$|.*(package-lock\\.json|yarn\\.lock|pnpm-lock\\.yaml|Gemfile\\.lock)$")) {
                    return "Dependency lock file";
                }
                if (path.matches("(?i).*/generated/.*|.*\\.generated\\..*")) {
                    return "Generated code";
                }
                return "Binary or minified asset";
            }
        }
        return null;
    }

    private static String renderIntegration(IntegrationContext context) {
        if (!context.touched()) {
            // Said explicitly rather than left blank: "no integration surface is
            // affected" is a finding the model should be able to state, and a
            // silent omission would invite it to speculate.
            return "No integration surface is affected by this change.";
        }

        StringBuilder out = new StringBuilder();
        out.append("directions: ").append(String.join(", ", context.directions())).append('\n');
        out.append("topologies: ").append(String.join(", ", context.detectedTopologies())).append('\n');

        out.append("impactedInterfaces:\n");
        for (ImpactedInterface item : context.impactedInterfaces()) {
            out.append("  - ").append(item.name())
                    .append(" (").append(item.direction())
                    .append(", severity ").append(item.severity())
                    .append(", confidence ").append(item.confidence()).append(")\n");
        }

        appendList(out, "impactedMappings", context.impactedMappings());
        appendList(out, "impactedDtos", context.impactedDtos());
        appendList(out, "impactedCommerceModels", context.impactedCommerceModels());
        appendList(out, "impactedTargetObjects", context.impactedTargetObjects());
        appendList(out, "impactedMiddlewareFlows", context.impactedMiddlewareFlows());

        return out.toString();
    }

    private static void appendList(StringBuilder out, String label, List<String> values) {
        if (!values.isEmpty()) {
            out.append(label).append(": ").append(String.join(", ", values)).append('\n');
        }
    }

    /** Applies every secret pattern. Cheap, and run on everything that leaves. */
    static String redact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String result = value;
        for (Pattern pattern : SECRET_PATTERNS) {
            result = pattern.matcher(result).replaceAll(REDACTED);
        }
        return result;
    }

    /**
     * Rough token estimate at four bytes per token.
     *
     * <p>Deliberately approximate and labelled as such in the UI. An exact
     * count would need the model's own tokenizer, which this service does not
     * and should not have.
     */
    private static int estimateTokens(int bytes) {
        return Math.max(1, bytes / 4);
    }

    private static int utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String nullSafe(String value) {
        return value == null || "null".equals(value) ? "" : value;
    }

    /* ------------------------------------------------------------- wire */

    public record ContextPackage(String id, String prId, String releaseId, String generatedAt,
                                 String engineVersion, List<ContextSection> sections,
                                 ContextPackageStats stats, List<String> redactions) {
    }

    public record ContextSection(String key, String label, ProvenanceClass provenance,
                                 int itemCount, String content) {
    }

    public record ContextPackageStats(int filesExamined, int filesIncluded, int filesExcluded,
                                      int rawBytes, int packagedBytes, double compressionRatio,
                                      int estimatedTokens, List<CountEntry> exclusionReasons) {
    }

    public record CountEntry(String key, String label, long count) {
    }
}
