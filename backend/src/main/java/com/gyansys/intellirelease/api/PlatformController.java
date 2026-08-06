package com.gyansys.intellirelease.api;

import com.gyansys.intellirelease.adapters.ai.AiServiceClient;
import com.gyansys.intellirelease.config.IntelliReleaseProperties;
import com.gyansys.intellirelease.domain.errors.ErrorIntelligenceEngine;
import com.gyansys.intellirelease.domain.integration.IntegrationCatalog;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.InterfaceDefinition;
import com.gyansys.intellirelease.domain.payload.PayloadAnalyzer;
import com.gyansys.intellirelease.infra.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Platform-level endpoints: what this tenant's landscape looks like, whether
 * the AI service is reachable, and what an arbitrary blob of text actually is.
 *
 * <p>The settings response is the contract that keeps the rest of the product
 * honest. {@code middlewareEnabled} and {@code enabledTopologies} are derived
 * from the catalogue rather than hard-coded, so a customer running nothing but
 * nightly CSV gets {@code middlewareEnabled = false} and every module stops
 * describing a middleware layer they do not have.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Platform", description = "Landscape settings, AI service status and input inspection")
public class PlatformController {

    private final IntegrationCatalog catalog;
    private final ErrorIntelligenceEngine errorEngine;
    private final PayloadAnalyzer analyzer;
    private final AiServiceClient aiServiceClient;
    private final IntelliReleaseProperties properties;
    private final TenantContext tenantContext;

    public PlatformController(IntegrationCatalog catalog,
                              ErrorIntelligenceEngine errorEngine,
                              PayloadAnalyzer analyzer,
                              AiServiceClient aiServiceClient,
                              IntelliReleaseProperties properties,
                              TenantContext tenantContext) {
        this.catalog = catalog;
        this.errorEngine = errorEngine;
        this.analyzer = analyzer;
        this.aiServiceClient = aiServiceClient;
        this.properties = properties;
        this.tenantContext = tenantContext;
    }

    /* ------------------------------------------------------------ settings */

    @GetMapping("/settings")
    @Operation(summary = "This tenant's landscape, AI, notification and governance configuration")
    public PlatformSettings settings() {
        List<InterfaceDefinition> interfaces = catalog.interfaces();

        Set<String> topologies = interfaces.stream()
                .map(InterfaceDefinition::topology)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        // Middleware is a fact about the catalogue, not a setting someone
        // remembered to tick: if no interface routes through one, there is none.
        List<String> middlewareNames = interfaces.stream()
                .map(InterfaceDefinition::middleware)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();

        List<String> targetSystems = interfaces.stream()
                .map(InterfaceDefinition::targetSystem)
                .filter(name -> name != null && !name.isBlank() && !"Commerce".equals(name))
                .distinct()
                .toList();

        return new PlatformSettings(
                tenantContext.currentTenantId(),
                "IntelliRelease",
                new IntegrationSettings(
                        List.copyOf(topologies),
                        !middlewareNames.isEmpty(),
                        middlewareNames.isEmpty() ? null : String.join(", ", middlewareNames),
                        targetSystems.isEmpty() ? "Target system" : String.join(", ", targetSystems),
                        distinctValues(interfaces, InterfaceDefinition::protocols),
                        distinctValues(interfaces, InterfaceDefinition::formats)),
                new AiSettings(
                        true,
                        null, null,
                        // The deterministic narrator is always present, which is
                        // why the AI service being down degrades prose only.
                        true,
                        null,
                        // sendDiffs is the architectural invariant made visible:
                        // when false, no source code ever reaches the model.
                        !properties.aiService().sendDiffs()),
                new NotificationSettings(
                        properties.email() != null,
                        properties.teams() != null && properties.teams().isConfigured(),
                        audiences()),
                new GovernanceSettings(true, null, true, true),
                connections());
    }

    @PutMapping("/settings")
    @Operation(
            summary = "Not implemented",
            description = "Settings are derived from the running configuration and catalogues, not a "
                    + "stored preference — there is nothing to durably write yet. Returns 501 rather "
                    + "than accepting a change that would not persist.")
    public ResponseEntity<PlatformSettings> updateSettings(@RequestBody java.util.Map<String, Object> ignored) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /* ---------------------------------------------------------- AI status */

    @GetMapping("/ai/status")
    @Operation(
            summary = "Whether the AI service is reachable",
            description = """
                    Drives the ambient status pill in the UI. A user must be able to tell at a
                    glance whether they are reading model prose or deterministic narration —
                    burying that in a settings page would undermine the product's central claim.
                    """)
    public AiServiceStatus aiStatus() {
        boolean available = aiServiceClient.isHealthy();
        return new AiServiceStatus(
                available,
                // The provider and model are the AI service's own business; it
                // reports them per response rather than exposing them here, so
                // this endpoint answers reachability and nothing it cannot know.
                null,
                null,
                true,
                available
                        ? null
                        : "The AI service at " + aiServiceClient.serviceUrl() + " is not reachable. Every score, "
                        + "impact and readiness verdict is unaffected — only natural-language narration falls "
                        + "back to the deterministic narrator.");
    }

    /* ------------------------------------------------------ input sniffing */

    @PostMapping("/inputs/inspect")
    @Operation(
            summary = "Identify what a blob of text actually is",
            description = "Sniffs the format from content rather than from a file extension.")
    public ParsedInput inspect(@RequestBody InspectRequest request) {
        String format = analyzer.detectFormat(request.content(), request.filename());
        var parsed = analyzer.parse(request.content(), request.filename(), format);

        return new ParsedInput(
                format,
                describeKind(format),
                null,
                "HIGH",
                summarise(format, parsed),
                List.of(),
                parsed.validation().issues().stream()
                        .map(PayloadAnalyzer.ValidationIssue::message)
                        .toList());
    }

    /* --------------------------------------------------------- assistant */

    @GetMapping("/assistant/suggestions")
    @Operation(summary = "Starting prompts for the AI assistant")
    public List<AssistantSuggestion> suggestions(@RequestParam(required = false) String context) {
        return List.of(
                new AssistantSuggestion("Explain a SOAP fault",
                        "Explain this SOAP fault and tell me which side is at fault.", "Errors"),
                new AssistantSuggestion("Why was this order rejected?",
                        "Why did the target system reject this order?", "Errors"),
                new AssistantSuggestion("Show the order flow",
                        "Show me the create-order flow end to end.", "Flows"),
                new AssistantSuggestion("Find a missing mapping",
                        "Which fields on the outbound order interface are unmapped?", "Mappings"),
                new AssistantSuggestion("Generate release notes",
                        "Draft release notes for the current release, per audience.", "Releases"),
                new AssistantSuggestion("Suggest a rollback plan",
                        "What would a rollback of this release involve?", "Releases"));
    }

    /* ---------------------------------------------------------- internals */

    private List<AudienceConfig> audiences() {
        var email = properties.email();
        if (email == null) {
            return List.of();
        }
        return List.of(
                audience("DEVELOPER", email.developerDistribution()),
                audience("QA", email.qaDistribution()),
                audience("BUSINESS", email.businessDistribution()),
                audience("CLIENT", email.clientDistribution()));
    }

    private static AudienceConfig audience(String name, String recipients) {
        List<String> list = recipients == null || recipients.isBlank()
                ? List.of()
                : List.of(recipients.split("\\s*,\\s*"));
        return new AudienceConfig(name, list, !list.isEmpty());
    }

    private List<ConnectionStatus> connections() {
        return List.of(
                new ConnectionStatus("github", "GitHub",
                        properties.github().hasToken() ? "HEALTHY" : "NOT_CONFIGURED",
                        properties.github().hasToken()
                                ? "Token configured"
                                : "No token configured — commit-graph resolution is unavailable",
                        null),
                new ConnectionStatus("github-webhook", "GitHub webhook signature",
                        properties.github().hasWebhookSecret() ? "HEALTHY" : "DEGRADED",
                        properties.github().hasWebhookSecret()
                                ? "Signature verification enabled"
                                : "No shared secret — events are stored but flagged unverified",
                        null),
                new ConnectionStatus("ai", "AI service",
                        aiServiceClient.isHealthy() ? "HEALTHY" : "DEGRADED",
                        aiServiceClient.isHealthy()
                                ? "Reachable"
                                : "Unreachable — deterministic narration in use",
                        null),
                new ConnectionStatus("teams", "Microsoft Teams",
                        properties.teams() != null && properties.teams().isConfigured()
                                ? "HEALTHY" : "NOT_CONFIGURED",
                        null, null),
                new ConnectionStatus("catalogues", "Knowledge catalogues", "HEALTHY",
                        "Integration catalogue " + catalog.version()
                                + ", error catalogue " + errorEngine.version(),
                        null));
    }

    private static List<String> distinctValues(
            List<InterfaceDefinition> interfaces,
            java.util.function.Function<InterfaceDefinition, List<String>> extractor) {
        return interfaces.stream()
                .flatMap(definition -> extractor.apply(definition).stream())
                .distinct()
                .sorted()
                .toList();
    }

    private static String describeKind(String format) {
        return switch (format) {
            case "SOAP_ENVELOPE" -> "SOAP envelope";
            case "IDOC_XML" -> "IDoc document";
            case "EDMX" -> "OData service metadata";
            case "WSDL" -> "SOAP service contract";
            case "XSD" -> "XML schema";
            case "STACK_TRACE" -> "Stack trace or log excerpt";
            case "IMPEX" -> "ImpEx import script";
            case "CSV" -> "Delimited data file";
            default -> format;
        };
    }

    private static String summarise(String format, PayloadAnalyzer.ParsedPayload parsed) {
        if (parsed.table() != null) {
            return "Delimited file with " + parsed.table().headers().size() + " column(s) and "
                    + parsed.table().totalRows() + " data row(s), delimiter '"
                    + parsed.table().delimiter() + "'.";
        }
        if (parsed.tree() != null) {
            return "Structured " + format + " document.";
        }
        return "Recognised as " + describeKind(format) + ". No structural parser applies, so it is shown as text.";
    }

    /* ------------------------------------------------------------- wire */

    public record PlatformSettings(String tenantId, String tenantName,
                                   IntegrationSettings integration, AiSettings ai,
                                   NotificationSettings notifications, GovernanceSettings governance,
                                   List<ConnectionStatus> connections) {
    }

    public record IntegrationSettings(List<String> enabledTopologies, boolean middlewareEnabled,
                                      String middlewareName, String targetSystemName,
                                      List<String> enabledProtocols, List<String> enabledFormats) {
    }

    public record AiSettings(boolean enabled, String provider, String model,
                             boolean deterministicFallback, Integer maxContextTokens,
                             boolean redactSecrets) {
    }

    public record NotificationSettings(boolean emailEnabled, boolean teamsEnabled,
                                       List<AudienceConfig> audiences) {
    }

    public record AudienceConfig(String audience, List<String> recipients, boolean enabled) {
    }

    public record GovernanceSettings(boolean approvalRequired, String riskPolicyVersion,
                                     boolean blockOnCriticalRisk, boolean requireTestsForHighRisk) {
    }

    public record ConnectionStatus(String key, String label, String status,
                                   String detail, String lastCheckedAt) {
    }

    public record AiServiceStatus(boolean available, String provider, String model,
                                  boolean deterministicFallback, String message) {
    }

    public record ParsedInput(String detectedFormat, String detectedKind, String direction,
                              String confidence, String summary, List<ParsedEntry> entries,
                              List<String> issues) {
    }

    public record ParsedEntry(String name, String format, int sizeBytes, String summary) {
    }

    public record AssistantSuggestion(String label, String prompt, String category) {
    }

    public record InspectRequest(String content, String filename) {
    }
}
