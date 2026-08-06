package com.gyansys.intellirelease.adapters.ai;

import com.gyansys.intellirelease.config.IntelliReleaseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * The only path between Spring Boot and the Python AI service.
 *
 * <p>Spring Boot calls the AI service. The AI service never calls back, never
 * reaches the database, never touches GitHub and holds no credentials. That
 * asymmetry is what makes the model a component rather than an agent, and it is
 * enforced here by there being no other client.
 *
 * <p>Failure is expected and handled: the deterministic engines have already
 * produced every fact by the time this runs, so an unavailable model degrades
 * the prose, not the intelligence. Callers fall back to
 * {@link DeterministicNarrator} templates and label the result.
 */
@Component
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final RestClient restClient;
    private final IntelliReleaseProperties.AiService config;

    public AiServiceClient(IntelliReleaseProperties properties) {
        this.config = properties.aiService();
        Duration timeout = Duration.ofSeconds(config.timeoutSeconds());

        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) timeout.toMillis());
        requestFactory.setReadTimeout((int) timeout.toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(config.url())
                .requestFactory(requestFactory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        if (config.sendDiffs()) {
            // Loud on purpose. The default is false and should stay false.
            log.warn("LLM_SEND_DIFFS is enabled. Raw change content may reach the model. "
                    + "This is not the recommended posture for a regulated customer.");
        }
    }

    /**
     * Asks the AI to explain one pull request's deterministic analysis.
     *
     * @return null when the service is unreachable or returns an unusable body;
     *         the caller narrates deterministically instead
     */
    public AiAnalysisResponse analyze(AiAnalysisRequest request) {
        try {
            AiAnalysisResponse response = restClient.post()
                    .uri("/analyze")
                    .body(request)
                    .retrieve()
                    .body(AiAnalysisResponse.class);

            if (response == null) {
                log.warn("AI service returned an empty body for PR #{}", request.prNumber());
                return null;
            }
            if (response.fallback()) {
                log.info("AI service reported its own fallback for PR #{} — content is templated, not model output",
                        request.prNumber());
            }
            return response;
        } catch (RuntimeException exception) {
            log.warn("AI service /analyze unavailable for PR #{}: {}. "
                            + "Deterministic analysis is unaffected; narration will be templated.",
                    request.prNumber(), exception.getMessage());
            return null;
        }
    }

    /** Asks the AI to synthesise the four audience notes for a release. */
    public AiSynthesisResponse synthesize(AiSynthesisRequest request) {
        try {
            AiSynthesisResponse response = restClient.post()
                    .uri("/synthesize")
                    .body(request)
                    .retrieve()
                    .body(AiSynthesisResponse.class);

            if (response == null) {
                log.warn("AI service returned an empty body for release {}", request.version());
                return null;
            }
            return response;
        } catch (RuntimeException exception) {
            log.warn("AI service /synthesize unavailable for release {}: {}. Falling back to templates.",
                    request.version(), exception.getMessage());
            return null;
        }
    }

    /** Liveness check surfaced on the dashboard so the demo can show state honestly. */
    public boolean isHealthy() {
        try {
            restClient.get().uri("/healthz").retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * The AI service's own self-report: which provider and model actually
     * answered its last reachability check. Null when the service cannot be
     * reached at all — the caller reports that as unavailable rather than
     * guessing a provider name.
     */
    @SuppressWarnings("unchecked")
    public java.util.Map<String, Object> health() {
        try {
            return restClient.get().uri("/healthz").retrieve().body(java.util.Map.class);
        } catch (RuntimeException exception) {
            log.warn("AI service /healthz unavailable: {}", exception.getMessage());
            return null;
        }
    }

    public String serviceUrl() {
        return config.url();
    }
}
