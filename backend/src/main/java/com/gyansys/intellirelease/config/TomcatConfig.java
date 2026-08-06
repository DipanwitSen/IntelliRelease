package com.gyansys.intellirelease.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Repository full names are {@code owner/repo} and appear as a single path
 * segment in URLs like {@code GET /api/v1/repositories/{name}} — the caller
 * percent-encodes the slash. Tomcat rejects an encoded slash in a URI by
 * default (historically a path-traversal precaution); this decodes it like
 * any other percent-encoded character rather than rejecting the request,
 * since Spring's own path matching — not raw URI structure — is what
 * resolves the route.
 */
@Configuration
public class TomcatConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> encodedSlashCustomizer() {
        TomcatConnectorCustomizer customizer = this::allowEncodedSlash;
        return factory -> factory.addConnectorCustomizers(customizer);
    }

    private void allowEncodedSlash(Connector connector) {
        connector.setEncodedSolidusHandling("decode");
    }
}
