package com.gyansys.intellirelease.domain.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyansys.intellirelease.domain.errors.ErrorModel.Catalogue;
import com.gyansys.intellirelease.domain.errors.ErrorModel.ErrorExplanation;
import com.gyansys.intellirelease.domain.errors.ErrorModel.ErrorLayer;
import com.gyansys.intellirelease.domain.errors.ErrorModel.ErrorLocation;
import com.gyansys.intellirelease.domain.errors.ErrorModel.ErrorPattern;
import com.gyansys.intellirelease.domain.errors.ErrorModel.ErrorSignature;
import com.gyansys.intellirelease.domain.integration.IntegrationCatalog;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.InterfaceDefinition;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Turns a raw exception, fault or log excerpt into something actionable.
 *
 * <h2>How matching works</h2>
 * Each catalogued pattern declares weighted signatures. The engine scores every
 * pattern by the weight of the signatures that fire, expressed as a percentage
 * of that pattern's total weight, and returns the highest scorer above the
 * catalogue's confidence floor.
 *
 * <p>Weighting rather than first-match-wins is what stops a bare HTTP 500 —
 * which appears in half the catalogue — outranking a precise exception class.
 * And the floor is what lets the engine say <em>"I do not recognise this"</em>,
 * which is the single most important behaviour here: a confidently wrong root
 * cause sends an engineer down the wrong path for an hour, while an honest
 * non-answer costs them a minute.
 *
 * <p>Everything this class produces is {@link ProvenanceClass#RULE_OUTPUT}. The
 * AI narrative is attached by a caller, separately and labelled as such.
 */
@Service
public class ErrorIntelligenceEngine {

    private static final Logger log = LoggerFactory.getLogger(ErrorIntelligenceEngine.class);
    private static final String RESOURCE_PATH = "/integration/error_catalog.json";

    /** Enough of the input to identify it in a list without storing a whole log. */
    private static final int EXCERPT_LIMIT = 600;

    private static final String UNMATCHED_GUIDANCE = """
            Nothing in the catalogue recognised this text, so no root cause is being guessed at. \
            Include the exception class name and the first line of the message rather than only a \
            stack trace tail — those carry almost all of the signal. If this failure mode is one \
            your team sees regularly, it is worth adding to the error catalogue so the next person \
            gets a full explanation.""";

    private final Catalogue catalogue;
    private final List<CompiledPattern> compiled;
    private final IntegrationCatalog integrationCatalog;

    public ErrorIntelligenceEngine(ObjectMapper objectMapper, IntegrationCatalog integrationCatalog) {
        this.catalogue = load(objectMapper);
        this.integrationCatalog = integrationCatalog;
        this.compiled = catalogue.patterns().stream().map(CompiledPattern::of).toList();

        log.info("Error catalogue loaded: {} patterns, confidence floor {}%",
                compiled.size(), catalogue.confidenceFloor());
    }

    /* ------------------------------------------------------------ catalogue */

    public List<ErrorPattern> patterns() {
        return catalogue.patterns();
    }

    public Optional<ErrorPattern> findPattern(String id) {
        return catalogue.patterns().stream().filter(pattern -> pattern.id().equals(id)).findFirst();
    }

    public String version() {
        return catalogue.meta() == null ? "unknown" : catalogue.meta().version();
    }

    /* --------------------------------------------------------- explanation */

    /**
     * Classifies raw text.
     *
     * @param rawText     an exception, stack trace, SOAP fault or message log
     * @param layerHint   optional narrowing when the caller knows the origin
     * @param interfaceId optional interface the failure was observed on
     */
    public ErrorExplanation explain(String rawText, String layerHint, String interfaceId) {
        String excerpt = excerpt(rawText);
        if (rawText == null || rawText.isBlank()) {
            return ErrorExplanation.unmatched(excerpt,
                    "No text was supplied. Paste the exception, fault or log excerpt you want explained.");
        }

        String haystack = rawText.toLowerCase();

        Optional<Scored> best = compiled.stream()
                .map(pattern -> score(pattern, rawText, haystack))
                .filter(scored -> scored.confidence() >= catalogue.confidenceFloor())
                .max(Comparator.comparingInt(Scored::confidence));

        if (best.isEmpty()) {
            return ErrorExplanation.unmatched(excerpt, UNMATCHED_GUIDANCE);
        }

        Scored scored = best.get();
        ErrorPattern pattern = scored.pattern();

        return new ErrorExplanation(
                excerpt,
                true,
                pattern,
                scored.confidence(),
                scored.matchedSignatures(),
                pattern.technicalExplanation(),
                locate(pattern, rawText, layerHint),
                affectedInterfaces(pattern, interfaceId),
                affectedPayloadFields(rawText),
                List.of(),
                similarPatterns(pattern),
                null,
                ProvenanceClass.RULE_OUTPUT,
                null);
    }

    /* ------------------------------------------------------------- scoring */

    private Scored score(CompiledPattern pattern, String rawText, String lowerText) {
        int matchedWeight = 0;
        List<String> matched = new ArrayList<>();

        for (CompiledSignature signature : pattern.signatures()) {
            if (signature.matches(rawText, lowerText)) {
                matchedWeight += signature.source().weight();
                matched.add(signature.source().kind() + ": " + signature.source().value());
            }
        }

        int total = pattern.pattern().totalWeight();
        int confidence = total == 0 ? 0 : Math.min(100, Math.round((matchedWeight * 100f) / total));
        return new Scored(pattern.pattern(), confidence, List.copyOf(matched));
    }

    /* ------------------------------------------------------------ location */

    /**
     * Where the failure surfaced.
     *
     * <p>A layer hint from the caller wins, because they observed it and the
     * catalogue only knows where this pattern <em>can</em> surface. Without a
     * hint every declared layer is returned with MEDIUM confidence, which is
     * an honest "it could be any of these" rather than a false pick.
     */
    private List<ErrorLocation> locate(ErrorPattern pattern, String rawText, String layerHint) {
        List<ErrorLayer> candidates = pattern.layers();
        if (candidates.isEmpty()) {
            return List.of();
        }

        if (layerHint != null && !layerHint.isBlank()) {
            List<ErrorLocation> narrowed = candidates.stream()
                    .filter(layer -> layer.layer().equalsIgnoreCase(layerHint))
                    .map(layer -> toLocation(layer, rawText, "HIGH",
                            "Caller reported this failure in the " + layer.layer() + " layer."))
                    .toList();
            if (!narrowed.isEmpty()) {
                return narrowed;
            }
        }

        return candidates.stream()
                .map(layer -> toLocation(layer, rawText, "MEDIUM",
                        "This pattern can surface in the " + layer.layer()
                                + " layer; no signal in the supplied text narrows it further."))
                .toList();
    }

    private ErrorLocation toLocation(ErrorLayer layer, String rawText, String confidence, String evidence) {
        String component = firstJavaClass(rawText).orElse(layer.layer());
        return new ErrorLocation(layer.layer(), component, layer.typicalSymptom(), evidence, confidence);
    }

    /** The first fully-qualified class name in a stack trace, if there is one. */
    private static Optional<String> firstJavaClass(String rawText) {
        Matcher matcher = JAVA_CLASS.matcher(rawText);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    private static final Pattern JAVA_CLASS =
            Pattern.compile("\\b(?:[a-z][\\w$]*\\.){2,}[A-Z][\\w$]*");

    /* ------------------------------------------------------------- context */

    /**
     * Interfaces plausibly involved.
     *
     * <p>An explicit interface id from the caller wins outright. Otherwise the
     * pattern's tags are matched against interface tags — a "pricing" pattern
     * surfaces pricing interfaces. Deliberately not "every interface", which
     * would be technically true and completely useless.
     */
    private List<String> affectedInterfaces(ErrorPattern pattern, String interfaceId) {
        if (interfaceId != null && !interfaceId.isBlank()) {
            return integrationCatalog.findInterface(interfaceId)
                    .map(definition -> List.of(definition.name()))
                    .orElse(List.of());
        }

        Set<String> tags = new LinkedHashSet<>(pattern.tags());
        if (tags.isEmpty()) {
            return List.of();
        }

        return integrationCatalog.interfaces().stream()
                .filter(definition -> definition.tags().stream().anyMatch(tags::contains))
                .map(InterfaceDefinition::name)
                .limit(6)
                .toList();
    }

    /**
     * Field names quoted in the error text.
     *
     * <p>Target systems name the offending field in quotes or after a path
     * separator far more often than people expect, and surfacing it turns a
     * wall of text into a specific thing to go and look at.
     */
    private static List<String> affectedPayloadFields(String rawText) {
        Set<String> fields = new LinkedHashSet<>();

        Matcher quoted = QUOTED_FIELD.matcher(rawText);
        while (quoted.find() && fields.size() < 10) {
            String candidate = quoted.group(1);
            if (candidate.length() >= 2 && candidate.length() <= 60 && !candidate.contains(" ")) {
                fields.add(candidate);
            }
        }

        Matcher path = XPATH_FIELD.matcher(rawText);
        while (path.find() && fields.size() < 10) {
            fields.add(path.group());
        }

        return List.copyOf(fields);
    }

    private static final Pattern QUOTED_FIELD = Pattern.compile("['\"]([A-Za-z][\\w./\\-]{1,59})['\"]");
    private static final Pattern XPATH_FIELD = Pattern.compile("\\b[A-Z][A-Za-z]+(?:/[A-Z][A-Za-z]+)+\\b");

    /** Catalogued neighbours, so a near-miss is one click away. */
    private List<ErrorModel.SimilarIncident> similarPatterns(ErrorPattern pattern) {
        return pattern.relatedPatternIds().stream()
                .map(this::findPattern)
                .flatMap(Optional::stream)
                .map(related -> new ErrorModel.SimilarIncident(
                        related.id(), related.title(), null,
                        related.suggestedFixes().isEmpty() ? null : related.suggestedFixes().get(0).summary(),
                        0))
                .toList();
    }

    /* ---------------------------------------------------------- internals */

    private static String excerpt(String rawText) {
        if (rawText == null) {
            return "";
        }
        String trimmed = rawText.strip();
        return trimmed.length() <= EXCERPT_LIMIT ? trimmed : trimmed.substring(0, EXCERPT_LIMIT) + "…";
    }

    private static Catalogue load(ObjectMapper objectMapper) {
        try (InputStream in = ErrorIntelligenceEngine.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Error catalogue not found on classpath: " + RESOURCE_PATH);
            }
            return objectMapper.readValue(in, Catalogue.class);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load the error catalogue", exception);
        }
    }

    private record Scored(ErrorPattern pattern, int confidence, List<String> matchedSignatures) {
    }

    private record CompiledPattern(ErrorPattern pattern, List<CompiledSignature> signatures) {
        static CompiledPattern of(ErrorPattern pattern) {
            return new CompiledPattern(pattern,
                    pattern.signatures().stream().map(CompiledSignature::of).toList());
        }
    }

    /**
     * A signature with its regex pre-compiled.
     *
     * <p>A malformed regex in the catalogue is logged and disabled rather than
     * thrown: one bad rule authored by a consultant should cost that rule, not
     * the application's ability to start.
     */
    private record CompiledSignature(ErrorSignature source, Pattern regex, String lowerValue) {

        static CompiledSignature of(ErrorSignature source) {
            Pattern regex = null;
            if ("MESSAGE_REGEX".equals(source.kind())) {
                try {
                    regex = Pattern.compile(source.value());
                } catch (PatternSyntaxException exception) {
                    log.warn("Disabling malformed signature regex '{}': {}", source.value(), exception.getMessage());
                }
            }
            return new CompiledSignature(source, regex, source.value().toLowerCase());
        }

        boolean matches(String rawText, String lowerText) {
            return switch (source.kind()) {
                case "MESSAGE_REGEX" -> regex != null && regex.matcher(rawText).find();
                case "EXCEPTION_CLASS" -> lowerText.contains(lowerValue);
                case "HTTP_STATUS" -> matchesStatus(rawText);
                case "SOAP_FAULT_CODE", "ERROR_CODE" -> lowerText.contains(lowerValue);
                default -> lowerText.contains(lowerValue);
            };
        }

        /**
         * A status code must appear as a standalone token.
         *
         * <p>Without the boundary check, "500" matches an order value of
         * 1500.00 and every large invoice looks like a server error.
         */
        private boolean matchesStatus(String rawText) {
            return Pattern.compile("(?<![\\d.])" + Pattern.quote(source.value()) + "(?![\\d.])")
                    .matcher(rawText).find();
        }
    }
}
