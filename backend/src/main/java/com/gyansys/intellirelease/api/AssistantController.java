package com.gyansys.intellirelease.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The conversational assistant. Not built yet — the AI service today only
 * answers two narrow, structured requests ({@code /analyze}, {@code /synthesize})
 * grounded in deterministic engine output; there is no open-ended chat
 * endpoint behind it, so a fabricated reply here would be worse than an
 * honest 501.
 *
 * <p>Starting prompts ({@code GET /assistant/suggestions}) live on
 * {@code PlatformController} — a static list needs no engine behind it, so
 * it was built ahead of the chat endpoint itself.
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
}
