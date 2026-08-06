package com.gyansys.intellirelease.api;

import com.gyansys.intellirelease.domain.errors.ErrorIntelligenceEngine;
import com.gyansys.intellirelease.domain.errors.ErrorModel.ErrorExplanation;
import com.gyansys.intellirelease.domain.errors.ErrorModel.ErrorOccurrence;
import com.gyansys.intellirelease.domain.errors.ErrorModel.ErrorPattern;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Error Intelligence: raw text in, an actionable explanation out.
 *
 * <p>The AI narrative is strictly opt-in via {@code includeNarrative}. Everything
 * that matters — what happened, where, likely causes, fixes, tests — is
 * deterministic and is produced whether or not a model is reachable. That
 * ordering is the module's whole argument, so the API makes it structural
 * rather than a matter of configuration.
 */
@RestController
@RequestMapping("/api/v1/errors")
@Tag(name = "Error Intelligence", description = "Translate exceptions and faults into cause, fix and test")
public class ErrorIntelligenceController {

    private final ErrorIntelligenceEngine engine;

    public ErrorIntelligenceController(ErrorIntelligenceEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/patterns")
    @Operation(summary = "The catalogued failure modes")
    public PageResponse<ErrorPattern> patterns(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String layer,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {

        String term = q == null ? "" : q.trim().toLowerCase();

        List<ErrorPattern> matches = engine.patterns().stream()
                .filter(pattern -> term.isEmpty()
                        || pattern.searchableText().toLowerCase().contains(term))
                .filter(pattern -> category == null || category.equalsIgnoreCase(pattern.category()))
                .filter(pattern -> severity == null || severity.equalsIgnoreCase(pattern.severity()))
                .filter(pattern -> layer == null || pattern.layers().stream()
                        .anyMatch(entry -> entry.layer().equalsIgnoreCase(layer)))
                .sorted(Comparator
                        .comparingInt((ErrorPattern pattern) -> -severityRank(pattern.severity()))
                        .thenComparing(ErrorPattern::title))
                .toList();

        return PageResponse.slice(matches, page, size);
    }

    @GetMapping("/patterns/{id}")
    @Operation(summary = "One catalogued failure mode in full")
    public ResponseEntity<ErrorPattern> pattern(@PathVariable String id) {
        return engine.findPattern(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/explain")
    @Operation(
            summary = "Explain a raw exception, fault or log excerpt",
            description = """
                    Matching is deterministic and weighted: a pattern scores by the weight of the
                    signatures that fire, so a bare HTTP 500 cannot outrank a precise exception
                    class. Below the catalogue's confidence floor the response reports that nothing
                    matched, with guidance — a confidently wrong root cause costs an engineer an
                    hour, an honest non-answer costs a minute.
                    """)
    public ErrorExplanation explain(@RequestBody ExplainRequest request) {
        // includeNarrative is accepted and deliberately not yet acted on: the
        // deterministic explanation is complete on its own, and wiring the
        // model in here would make the response quietly non-reproducible.
        return engine.explain(request.content(), request.layer(), request.interfaceId());
    }

    @GetMapping("/occurrences")
    @Operation(
            summary = "Recorded error occurrences",
            description = """
                    Empty until an error-capture source is connected. Returning an empty page is the
                    honest answer; seeding it with examples would make a demo look livelier and make
                    the module untrustworthy.
                    """)
    public PageResponse<ErrorOccurrence> occurrences(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return PageResponse.slice(List.of(), page, size);
    }

    private static int severityRank(String severity) {
        return switch (severity == null ? "" : severity) {
            case "CRITICAL" -> 5;
            case "HIGH" -> 4;
            case "MEDIUM" -> 3;
            case "LOW" -> 2;
            default -> 1;
        };
    }

    public record ExplainRequest(@NotBlank String content, String layer,
                                 String interfaceId, Boolean includeNarrative) {
    }
}
