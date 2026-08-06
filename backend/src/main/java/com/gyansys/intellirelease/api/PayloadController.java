package com.gyansys.intellirelease.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Captured integration payloads and the parse/compare tooling built on top of
 * them. No payload has been captured for this tenant yet, and the format
 * sniffer behind parse/compare is not implemented — both are honest 501s
 * rather than a fabricated parse result.
 */
@RestController
@RequestMapping("/api/v1/payloads")
@Tag(name = "Payloads", description = "Captured integration payloads — not yet populated")
public class PayloadController {

    @GetMapping
    @Operation(summary = "Captured payloads — empty until a payload sample is captured")
    public PageResponse<Object> list(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(List.of(), 0, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One payload — not found until a payload sample is captured")
    public ResponseEntity<Object> get(@PathVariable String id) {
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/compare")
    @Operation(summary = "Not implemented — the payload diff engine is not built yet")
    public ResponseEntity<Object> compare(@RequestParam String left, @RequestParam String right) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/parse")
    @Operation(summary = "Not implemented — the format-sniffing parser is not built yet")
    public ResponseEntity<Object> parse(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/compare")
    @Operation(summary = "Not implemented — the payload diff engine is not built yet")
    public ResponseEntity<Object> compareRaw(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
