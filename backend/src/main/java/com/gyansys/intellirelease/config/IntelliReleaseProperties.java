package com.gyansys.intellirelease.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Type-safe binding for everything under the {@code intellirelease} prefix.
 *
 * <p>Records with constructor binding, so configuration is immutable once the
 * context is up and no component can quietly mutate a shared setting.
 */
@ConfigurationProperties(prefix = "intellirelease")
public record IntelliReleaseProperties(
        Tenant tenant,
        GitHub github,
        AiService aiService,
        Teams teams,
        Email email,
        Security security,
        Cors cors,
        Jobs jobs
) {

    public record Tenant(String defaultId) {
        public Tenant {
            defaultId = defaultId == null || defaultId.isBlank() ? "eli-lilly" : defaultId;
        }
    }

    /**
     * @param webhookSecret HMAC-SHA256 shared secret. Blank disables verification,
     *                      which is a POC-only affordance: events are still stored
     *                      but flagged {@code signature_valid = false}.
     * @param token         read-only PAT for the POC; a GitHub App issues short-lived
     *                      installation tokens in a pilot.
     */
    public record GitHub(String apiBaseUrl, String webhookSecret, String appId,
                         String privateKeyPath, String token) {
        public GitHub {
            apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://api.github.com" : apiBaseUrl;
        }

        public boolean hasWebhookSecret() {
            return webhookSecret != null && !webhookSecret.isBlank();
        }

        public boolean hasToken() {
            return token != null && !token.isBlank();
        }
    }

    /**
     * @param sendDiffs architectural invariant, kept as an explicit switch so the
     *                  answer to "does our code reach the model?" is a config value
     *                  a security reviewer can read, not a claim in a slide.
     */
    public record AiService(String url, int timeoutSeconds, boolean sendDiffs) {
        public AiService {
            url = url == null || url.isBlank() ? "http://localhost:8000" : url;
            timeoutSeconds = timeoutSeconds <= 0 ? 60 : timeoutSeconds;
        }
    }

    public record Teams(String webhookUrl) {
        public boolean isConfigured() {
            return webhookUrl != null && !webhookUrl.isBlank();
        }
    }

    public record Email(String from, String developerDistribution, String qaDistribution,
                        String businessDistribution, String clientDistribution,
                        String securityNotificationRecipient, Relay relay) {
        public Email {
            relay = relay == null ? new Relay(null, 0, null, null, null) : relay;
            securityNotificationRecipient = securityNotificationRecipient == null || securityNotificationRecipient.isBlank()
                    ? "security-notifications@demo.local" : securityNotificationRecipient;
        }

        /**
         * Real-world SMTP relay (e.g. Office365/Outlook) that mirrors every send
         * MailHog captures. Optional: {@link #isConfigured()} is false until both
         * a username and password are supplied, and the email provider skips
         * the relay entirely in that case — MailHog capture keeps working either way.
         *
         * @param testRecipientOverride POC safety valve. Audience distribution lists
         *                              (dev-team@demo.local etc.) are not real inboxes;
         *                              when set, every relayed message is redirected here
         *                              instead of the audience address, so testing real
         *                              delivery never risks mailing a real distribution list.
         */
        public record Relay(String host, int port, String username, String password,
                            String testRecipientOverride) {
            public Relay {
                host = host == null || host.isBlank() ? "smtp.office365.com" : host;
                port = port <= 0 ? 587 : port;
            }

            public boolean isConfigured() {
                return username != null && !username.isBlank() && password != null && !password.isBlank();
            }

            public String effectiveRecipient(String audienceRecipient) {
                return testRecipientOverride != null && !testRecipientOverride.isBlank()
                        ? testRecipientOverride
                        : audienceRecipient;
            }
        }
    }

    public record Security(Jwt jwt) {
        public record Jwt(String issuer, String audience, String secret, int ttlMinutes) {
            public Jwt {
                ttlMinutes = ttlMinutes <= 0 ? 480 : ttlMinutes;
            }
        }
    }

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of("http://localhost:4200") : List.copyOf(allowedOrigins);
        }
    }

    public record Jobs(long pollIntervalMs, int batchSize, boolean workerEnabled) {
        public Jobs {
            pollIntervalMs = pollIntervalMs <= 0 ? 2000 : pollIntervalMs;
            batchSize = batchSize <= 0 ? 5 : batchSize;
        }
    }
}
