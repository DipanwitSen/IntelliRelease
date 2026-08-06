package com.gyansys.intellirelease.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.adapters.ai.AiServiceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Surfaces the AI service's own self-report rather than caching or inferring
 * one — every call is a live {@code GET /healthz} against the Python service,
 * so the dashboard never claims a provider is reachable after it has failed.
 */
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Status", description = "Live reachability of the stateless AI explanation service")
public class AiStatusController {

    private final AiServiceClient aiServiceClient;

    public AiStatusController(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Status(boolean available, String provider, String model, boolean deterministicFallback,
                         String message) {
    }

    @GetMapping("/status")
    @Operation(summary = "Live AI service reachability, provider and model")
    public Status status() {
        Map<String, Object> health = aiServiceClient.health();
        if (health == null) {
            return new Status(false, null, null, true,
                    "AI service unreachable at " + aiServiceClient.serviceUrl() + " — narration falls back to deterministic templates");
        }
        boolean providerReachable = Boolean.TRUE.equals(health.get("providerReachable"));
        String provider = health.get("provider") == null ? null : String.valueOf(health.get("provider"));
        String model = health.get("model") == null ? null : String.valueOf(health.get("model"));
        return new Status(providerReachable, provider, model, !providerReachable,
                providerReachable ? null : "AI service is up but its model provider is not reachable — narration falls back to deterministic templates");
    }
}
