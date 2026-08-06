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
 * Error Intelligence: a curated catalogue of recognised failure modes plus a
 * classifier that matches raw error text against it. Neither the catalogue
 * nor the classifier exists yet, so every endpoint here is an honest empty
 * state or 501 — never a guessed match.
 */
@RestController
@RequestMapping("/api/v1/errors")
@Tag(name = "Error Intelligence", description = "Failure-mode catalogue and classifier — not yet built")
public class ErrorIntelligenceController {

    @GetMapping("/patterns")
    @Operation(summary = "Recognised error patterns — empty until the catalogue is curated")
    public PageResponse<Object> patterns(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(List.of(), 0, page, size);
    }

    @GetMapping("/patterns/{id}")
    @Operation(summary = "One error pattern — not found until the catalogue is curated")
    public ResponseEntity<Object> pattern(@PathVariable String id) {
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/explain")
    @Operation(summary = "Not implemented — the error classifier is not built yet")
    public ResponseEntity<Object> explain(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/occurrences")
    @Operation(summary = "Recorded error occurrences — empty until occurrence capture is built")
    public PageResponse<Object> occurrences(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(List.of(), 0, page, size);
    }
}
