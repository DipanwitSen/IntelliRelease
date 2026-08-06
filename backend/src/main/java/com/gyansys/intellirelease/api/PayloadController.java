package com.gyansys.intellirelease.api;

import com.gyansys.intellirelease.domain.integration.IntegrationCatalog;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.SamplePayload;
import com.gyansys.intellirelease.domain.payload.PayloadAnalyzer;
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

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

/**
 * The Payload Explorer's API: browse catalogued payloads, parse arbitrary
 * pasted content, and diff two documents structurally.
 *
 * <p>Nothing here reaches the AI service. Format detection, validation and
 * comparison are all deterministic — which is what makes the results
 * reproducible and safe to run against payloads that may contain customer data.
 */
@RestController
@RequestMapping("/api/v1/payloads")
@Tag(name = "Payloads", description = "View, validate and compare payloads in any supported format")
public class PayloadController {

    /** Guard against a paste large enough to stall the request thread. */
    private static final int MAX_CONTENT_BYTES = 5 * 1024 * 1024;

    private final IntegrationCatalog catalog;
    private final PayloadAnalyzer analyzer;

    public PayloadController(IntegrationCatalog catalog, PayloadAnalyzer analyzer) {
        this.catalog = catalog;
        this.analyzer = analyzer;
    }

    /* ------------------------------------------------------------ browsing */

    @GetMapping
    @Operation(summary = "Catalogued sample payloads")
    public PageResponse<PayloadDocument> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String interfaceId,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {

        String term = q == null ? "" : q.trim().toLowerCase();

        List<PayloadDocument> matches = catalog.samplePayloads().stream()
                .filter(payload -> term.isEmpty()
                        || (payload.name() + " " + payload.description() + " " + payload.businessObject())
                        .toLowerCase().contains(term))
                .filter(payload -> format == null || format.equalsIgnoreCase(payload.format()))
                .filter(payload -> interfaceId == null || interfaceId.equals(payload.interfaceId()))
                .filter(payload -> direction == null || direction.equalsIgnoreCase(payload.direction()))
                .sorted(Comparator.comparing(SamplePayload::name))
                .map(PayloadController::toDocument)
                .toList();

        return PageResponse.slice(matches, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One payload, parsed and validated")
    public ResponseEntity<PayloadContent> get(@PathVariable String id) {
        return catalog.findPayload(id)
                .map(payload -> {
                    var parsed = analyzer.parse(payload.content(), payload.name(), payload.format());
                    return ResponseEntity.ok(new PayloadContent(
                            toDocument(payload), parsed.raw(), parsed.tree(), parsed.validation(), parsed.table()));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /* ------------------------------------------------------------- parsing */

    @PostMapping("/parse")
    @Operation(
            summary = "Parse arbitrary pasted or uploaded content",
            description = """
                    The format is sniffed from the content, not taken from the file extension —
                    a .txt holding an IDoc is still recognised as an IDoc. A supplied format is
                    treated as a hint the sniffer may override.
                    """)
    public PayloadContent parse(@RequestBody ParseRequest request) {
        reject(request.content());

        var parsed = analyzer.parse(request.content(), request.filename(), request.format());
        String name = request.filename() == null || request.filename().isBlank()
                ? "pasted-content"
                : request.filename();

        PayloadDocument document = new PayloadDocument(
                "inline", name, parsed.format(), null, null, null,
                request.content().getBytes(StandardCharsets.UTF_8).length,
                null, null, "Parsed from supplied content", List.of());

        return new PayloadContent(document, parsed.raw(), parsed.tree(), parsed.validation(), parsed.table());
    }

    /* --------------------------------------------------------- comparison */

    @GetMapping("/compare")
    @Operation(summary = "Structurally diff two catalogued payloads")
    public ResponseEntity<PayloadAnalyzer.PayloadComparison> compareCatalogued(
            @RequestParam String left, @RequestParam String right) {

        var leftPayload = catalog.findPayload(left);
        var rightPayload = catalog.findPayload(right);

        if (leftPayload.isEmpty() || rightPayload.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(analyzer.compare(
                leftPayload.get().content(), rightPayload.get().content(),
                leftPayload.get().name(), rightPayload.get().name(),
                leftPayload.get().format()));
    }

    @PostMapping("/compare")
    @Operation(
            summary = "Structurally diff two supplied documents",
            description = """
                    Reports added, removed, value-changed and type-changed fields. Removals and
                    type changes are flagged as breaking, because those are what break a consumer;
                    an added field is reported but not breaking, since a tolerant reader ignores it.
                    """)
    public PayloadAnalyzer.PayloadComparison compareRaw(@RequestBody CompareRequest request) {
        reject(request.left());
        reject(request.right());

        return analyzer.compare(
                request.left(), request.right(),
                request.leftLabel() == null ? "Left" : request.leftLabel(),
                request.rightLabel() == null ? "Right" : request.rightLabel(),
                request.format());
    }

    /* ---------------------------------------------------------- internals */

    private static void reject(String content) {
        if (content != null && content.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw new IllegalArgumentException(
                    "Content exceeds the " + (MAX_CONTENT_BYTES / (1024 * 1024))
                            + "MB limit for interactive parsing. Trim it to the section you need to inspect.");
        }
    }

    private static PayloadDocument toDocument(SamplePayload payload) {
        return new PayloadDocument(
                payload.id(), payload.name(), payload.format(), payload.interfaceId(),
                payload.businessObject(), payload.direction(),
                payload.content().getBytes(StandardCharsets.UTF_8).length,
                null, null, payload.description(), payload.tags());
    }

    /* ------------------------------------------------------------- wire */

    public record PayloadDocument(
            String id, String name, String format, String interfaceId, String businessObject,
            String direction, int sizeBytes, String version, String capturedAt,
            String description, List<String> tags
    ) {
    }

    public record PayloadContent(
            PayloadDocument document, String raw, PayloadAnalyzer.PayloadNode tree,
            PayloadAnalyzer.PayloadValidation validation, PayloadAnalyzer.PayloadTable table
    ) {
    }

    public record ParseRequest(@NotBlank String content, String filename, String format, String schema) {
    }

    public record CompareRequest(@NotBlank String left, @NotBlank String right,
                                 String leftLabel, String rightLabel, String format) {
    }
}
