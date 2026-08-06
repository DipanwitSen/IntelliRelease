package com.gyansys.intellirelease.domain.context.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Loads {@code sap_context.json} once at startup and answers "what SAP
 * Commerce artifact is this changed path?"
 *
 * <p>This is deterministic, hand-curated domain knowledge read from a data
 * file, not inferred by a model — the same guarantee
 * {@link com.gyansys.intellirelease.domain.context.CapabilityLibrary} made
 * when its rules were Java code. Moving the rules into data means the
 * knowledge base can be revised by someone who knows SAP Commerce without
 * touching Java, while classification stays exactly as auditable: every
 * match traces back to one named pattern at one priority in one versioned
 * file.
 *
 * <p>Patterns are matched by priority, highest first — not by array order —
 * so a specific rule like {@code **}{@code /resources/*-items.xml} always
 * wins over a broader one even if the broader one appears earlier in the
 * source file.
 *
 * <p>Glob matching is hand-rolled rather than delegated to
 * {@code java.nio.file.PathMatcher}: the JDK's {@code **} does not match
 * zero directory segments (confirmed empirically — see
 * {@code SapCommerceKnowledgeBaseTest}), which silently misses exactly the
 * shallow paths — a root {@code manifest.json}, a file sitting directly
 * under {@code resources/} — these patterns were written to catch.
 */
@Component
public class SapCommerceKnowledgeBase {

    private static final String RESOURCE_PATH = "/sap-commerce/sap_context.json";

    private final SapContextKnowledgeBase knowledgeBase;
    private final List<CompiledRule> compiledRules;

    public SapCommerceKnowledgeBase(ObjectMapper objectMapper) {
        this.knowledgeBase = load(objectMapper);
        this.compiledRules = knowledgeBase.fileClassification().stream()
                .sorted(Comparator.comparingInt(FileClassificationRule::priority).reversed())
                .map(rule -> new CompiledRule(PathGlob.compile(rule.pattern()), rule))
                .toList();
    }

    /** The highest-priority pattern that matches this changed-file path, if any. */
    public Optional<Match> classify(String rawPath) {
        String candidate = PathGlob.normalise(rawPath);
        for (CompiledRule compiled : compiledRules) {
            if (compiled.regex.matcher(candidate).matches()) {
                ArtifactTypeDefinition definition = knowledgeBase.artifactTypes().get(compiled.rule.artifactType());
                return Optional.of(new Match(compiled.rule.artifactType(), definition,
                        compiled.rule.pattern(), compiled.rule.priority()));
            }
        }
        return Optional.empty();
    }

    public String version() {
        return knowledgeBase.meta() == null ? "unknown" : knowledgeBase.meta().version();
    }

    public int ruleCount() {
        return compiledRules.size();
    }

    public int artifactTypeCount() {
        return knowledgeBase.artifactTypes().size();
    }

    private static SapContextKnowledgeBase load(ObjectMapper objectMapper) {
        try (InputStream in = SapCommerceKnowledgeBase.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("SAP Commerce knowledge base not found on classpath: " + RESOURCE_PATH);
            }
            return objectMapper.readValue(in, SapContextKnowledgeBase.class);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load SAP Commerce knowledge base", exception);
        }
    }

    private record CompiledRule(Pattern regex, FileClassificationRule rule) {
    }

    /**
     * @param artifactType   the stable key, e.g. {@code "items_xml"}
     * @param definition     the full curated entry; never null when a pattern matched
     * @param matchedPattern the glob that fired, for traceability
     * @param priority       the priority that made this pattern win
     */
    public record Match(String artifactType, ArtifactTypeDefinition definition, String matchedPattern, int priority) {
    }
}
