package com.gyansys.intellirelease.adapters.notify;

import com.gyansys.intellirelease.config.IntelliReleaseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Posts one message to a Microsoft Teams incoming webhook — a channel
 * notification, not a per-person message. Unconfigured means skipped, not
 * failed: a Teams webhook is optional infrastructure the POC may not have.
 */
@Component
public class TeamsWebhookProvider {

    private static final Logger log = LoggerFactory.getLogger(TeamsWebhookProvider.class);

    private final RestClient restClient;
    private final IntelliReleaseProperties.Teams config;

    public TeamsWebhookProvider(IntelliReleaseProperties properties) {
        this.config = properties.teams();
        this.restClient = RestClient.create();
    }

    public boolean isConfigured() {
        return config.isConfigured();
    }

    /** @return true if the webhook accepted the post; false if unconfigured or it failed */
    public boolean send(String title, String text) {
        if (!isConfigured()) {
            return false;
        }
        try {
            restClient.post()
                    .uri(config.webhookUrl())
                    .body(Map.of(
                            "@type", "MessageCard",
                            "@context", "http://schema.org/extensions",
                            "title", title,
                            "text", text))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RuntimeException exception) {
            log.warn("Teams webhook post failed: {}", exception.getMessage());
            return false;
        }
    }
}
