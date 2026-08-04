package com.gyansys.intellirelease.api;

import com.gyansys.intellirelease.application.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The event-driven entry point. GitHub webhooks land on Spring Boot, never on
 * Angular — the UI is not on this path at all.
 *
 * <p>The body is taken as a raw {@link String} on purpose. HMAC is computed over
 * the exact bytes GitHub signed; letting Spring deserialise into a DTO first and
 * re-serialising to verify would change whitespace and key order, and every
 * signature would fail.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks", description = "Event-driven change capture from source control")
public class WebhookController {

    private final IngestionService ingestionService;

    public WebhookController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(value = "/github", consumes = "application/json")
    @Operation(
            summary = "Receive a GitHub webhook",
            description = """
                    Verifies the HMAC-SHA256 signature, deduplicates on delivery ID,
                    persists the raw event, then queues analysis and acknowledges.

                    Always returns 202 for a well-formed delivery, including duplicates:
                    a duplicate is a successful no-op, and returning an error would make
                    GitHub retry the delivery we just told it we already had.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Event captured (accepted, ignored, or duplicate)"),
            @ApiResponse(responseCode = "400", description = "Body was not valid JSON")
    })
    public ResponseEntity<IngestionService.IngestResult> receiveGitHubWebhook(
            @RequestBody String rawBody,
            @Parameter(description = "GitHub's unique delivery identifier — the idempotency key")
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @Parameter(description = "Event type, e.g. pull_request")
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @Parameter(description = "HMAC-SHA256 of the raw body, prefixed sha256=")
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {

        // A replayed sample payload has no delivery header. Synthesising one keeps
        // the demo path and the live path identical through the same pipeline.
        String effectiveDeliveryId = (deliveryId == null || deliveryId.isBlank())
                ? "local-" + UUID.randomUUID()
                : deliveryId;

        IngestionService.IngestResult result = ingestionService.ingest(
                rawBody,
                effectiveDeliveryId,
                eventType == null ? "pull_request" : eventType,
                signature);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }
}
