package com.gyansys.intellirelease.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.adapters.ai.AiServiceClient;
import com.gyansys.intellirelease.config.IntelliReleaseProperties;
import com.gyansys.intellirelease.infra.TenantContext;
import com.gyansys.intellirelease.repository.PrAnalysisRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Platform configuration, read from {@link IntelliReleaseProperties} — every
 * value here is what actually governs the running system, not a stored
 * preference. There is deliberately no {@code PUT} persistence yet: these
 * values are environment configuration, and accepting a write that silently
 * did nothing would be a worse UI than an honest 501.
 */
@RestController
@RequestMapping("/api/v1/settings")
@Tag(name = "Settings", description = "The configuration actually governing this running instance")
public class SettingsController {

    private final IntelliReleaseProperties properties;
    private final AiServiceClient aiServiceClient;
    private final PrAnalysisRepository prAnalysisRepository;
    private final TenantContext tenantContext;

    public SettingsController(IntelliReleaseProperties properties, AiServiceClient aiServiceClient,
                              PrAnalysisRepository prAnalysisRepository, TenantContext tenantContext) {
        this.properties = properties;
        this.aiServiceClient = aiServiceClient;
        this.prAnalysisRepository = prAnalysisRepository;
        this.tenantContext = tenantContext;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IntegrationSettings(List<String> enabledTopologies, boolean middlewareEnabled,
                                      String middlewareName, String targetSystemName,
                                      List<String> enabledProtocols, List<String> enabledFormats) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AiSettings(boolean enabled, String provider, String model, boolean deterministicFallback,
                             Integer maxContextTokens, boolean redactSecrets) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AudienceConfig(String audience, List<String> recipients, boolean enabled) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NotificationSettings(boolean emailEnabled, boolean teamsEnabled, List<AudienceConfig> audiences) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GovernanceSettings(boolean approvalRequired, String riskPolicyVersion,
                                     boolean blockOnCriticalRisk, boolean requireTestsForHighRisk) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConnectionStatus(String key, String label, String status, String detail,
                                   OffsetDateTime lastCheckedAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Settings(String tenantId, String tenantName, IntegrationSettings integration, AiSettings ai,
                           NotificationSettings notifications, GovernanceSettings governance,
                           List<ConnectionStatus> connections) {
    }

    @GetMapping
    @Operation(summary = "Configuration currently governing this instance")
    public Settings get() {
        String tenantId = tenantContext.currentTenantId();
        OffsetDateTime now = OffsetDateTime.now();

        IntelliReleaseProperties.GitHub github = properties.github();
        IntelliReleaseProperties.Teams teams = properties.teams();
        IntelliReleaseProperties.Email email = properties.email();
        IntelliReleaseProperties.AiService aiConfig = properties.aiService();

        Map<String, Object> aiHealth = aiServiceClient.health();
        boolean aiReachable = aiHealth != null && Boolean.TRUE.equals(aiHealth.get("providerReachable"));
        String provider = aiHealth == null ? null : (String) aiHealth.get("provider");
        String model = aiHealth == null ? null : (String) aiHealth.get("model");

        IntegrationSettings integration = new IntegrationSettings(
                List.of(), false, null, "SAP Commerce Cloud", List.of(), List.of());

        AiSettings ai = new AiSettings(true, provider, model, true, null, !aiConfig.sendDiffs());

        List<AudienceConfig> audiences = new ArrayList<>();
        audiences.add(audience("developer", email.developerDistribution()));
        audiences.add(audience("qa", email.qaDistribution()));
        audiences.add(audience("business", email.businessDistribution()));
        audiences.add(audience("client", email.clientDistribution()));
        NotificationSettings notifications = new NotificationSettings(true, teams.isConfigured(), audiences);

        String riskPolicyVersion = prAnalysisRepository.findByTenantId(tenantId).stream()
                .max(java.util.Comparator.comparing(a -> a.getGeneratedAt()))
                .map(a -> a.getRiskPolicyVersion())
                .orElse(null);
        GovernanceSettings governance = new GovernanceSettings(true, riskPolicyVersion, false, false);

        List<ConnectionStatus> connections = List.of(
                new ConnectionStatus("github", "GitHub Webhooks",
                        github.hasWebhookSecret() ? "HEALTHY" : "NOT_CONFIGURED",
                        github.hasWebhookSecret() ? "HMAC verification enabled" : "No webhook secret configured — signature verification is skipped",
                        now),
                new ConnectionStatus("ai-service", "AI Service", aiReachable ? "HEALTHY" : "UNHEALTHY",
                        aiHealth == null ? "Unreachable at " + aiServiceClient.serviceUrl() : "Reachable", now),
                new ConnectionStatus("teams", "Microsoft Teams",
                        teams.isConfigured() ? "HEALTHY" : "NOT_CONFIGURED",
                        teams.isConfigured() ? "Webhook configured" : "No Teams webhook URL configured", now)
        );

        return new Settings(tenantId, tenantId, integration, ai, notifications, governance, connections);
    }

    private AudienceConfig audience(String name, String distribution) {
        boolean enabled = distribution != null && !distribution.isBlank();
        return new AudienceConfig(name, enabled ? List.of(distribution) : List.of(), enabled);
    }

    @PutMapping
    @Operation(
            summary = "Not implemented",
            description = "Settings are environment configuration in this POC, not a stored preference — there is "
                    + "nothing to durably write yet. Returns 501 rather than accepting a change that would not persist.")
    public ResponseEntity<Settings> update(@RequestBody Map<String, Object> ignored) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
