package com.gyansys.intellirelease.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The conversational assistant. Not built yet — the AI service today only
 * answers two narrow, structured requests ({@code /analyze}, {@code /synthesize})
 * grounded in deterministic engine output; there is no open-ended chat
 * endpoint behind it, so a fabricated reply here would be worse than an
 * honest 501.
 */
@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "Assistant", description = "Conversational assistant — not built yet")
public class AssistantController {

    @PostMapping("/ask")
    @Operation(summary = "Not implemented — there is no open-ended assistant endpoint yet")
    public ResponseEntity<Object> ask(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Suggested prompts — empty until the assistant is built")
    public List<Object> suggestions(@RequestParam(required = false) String context) {
        return List.of();
    }
}
