package com.intellirelease.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellirelease.model.ReleaseNarrative;
import com.intellirelease.model.ReleaseSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DefaultAiIntelligenceClient implements AiIntelligenceClient {

    private final String aiServiceUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DefaultAiIntelligenceClient(@Value("${intellirelease.ai-service-url:}") String aiServiceUrl) {
        this.aiServiceUrl = aiServiceUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ReleaseNarrative explain(ReleaseSummary summary) {
        if (aiServiceUrl == null || aiServiceUrl.isBlank()) {
            return fallback(summary, "deterministic");
        }

        try {
            String requestJson = objectMapper.writeValueAsString(buildRequest(summary));
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiServiceUrl + "/ai/explain"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> responseBody = objectMapper.readValue(response.body(), new TypeReference<>() {
            });

            if (responseBody == null) {
                return fallback(summary, "deterministic");
            }

            return new ReleaseNarrative(
                valueAsString(responseBody, "provider", "ai-service"),
                valueAsString(responseBody, "business_summary", summary.businessSummary()),
                valueAsString(responseBody, "technical_summary", summary.technicalSummary()),
                valueAsString(responseBody, "executive_summary", summary.readinessStatus()),
                valueAsString(responseBody, "risk_explanation", "Risk computed by deterministic rules."),
                valueAsString(responseBody, "qa_guidance", String.join(", ", summary.recommendedTests())),
                valueAsString(responseBody, "readiness_explanation", summary.readinessStatus()),
                responseBody
            );
        } catch (Exception ex) {
            return fallback(summary, "fallback");
        }
    }

    private Map<String, Object> buildRequest(ReleaseSummary summary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("release_id", summary.releaseId());
        payload.put("repository", summary.repository());
        payload.put("branch", summary.branch());
        payload.put("pr_number", summary.prNumber());
        payload.put("title", summary.title());
        payload.put("author", summary.author());
        payload.put("risk_score", summary.riskScore());
        payload.put("readiness_score", summary.readinessScore());
        payload.put("risk_level", summary.riskLevel().name());
        payload.put("impacted_capabilities", summary.impactedCapabilities());
        payload.put("recommended_tests", summary.recommendedTests());
        payload.put("config_drift", summary.configDrift());
        payload.put("business_summary", summary.businessSummary());
        payload.put("technical_summary", summary.technicalSummary());
        return payload;
    }

    private ReleaseNarrative fallback(ReleaseSummary summary, String provider) {
        return new ReleaseNarrative(
                provider,
                summary.businessSummary(),
                summary.technicalSummary(),
                "Release " + summary.releaseId() + " is " + summary.readinessStatus() + " at " + summary.readinessScore() + "/100 readiness.",
                "Risk score " + summary.riskScore() + "/100 with level " + summary.riskLevel() + ".",
                String.join(", ", summary.recommendedTests()),
                summary.readinessStatus(),
                Map.of("fallback", true)
        );
    }

    private String valueAsString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }
}