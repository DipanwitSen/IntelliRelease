package com.gyansys.intellirelease.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger sits on the developer boundary. It documents the backend for
 * engineers and is never on the user path — no role in the platform reaches
 * IntelliRelease through Swagger.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI intelliReleaseOpenApi() {
        return new OpenAPI().info(new Info()
                .title("IntelliRelease v2 API")
                .version("2.0.0-POC")
                .description("""
                        AI-Powered SAP Commerce Release Intelligence & Deployment Governance Platform.

                        Architectural invariants this API enforces:

                        * Deterministic engines produce every score. The AI service explains them
                          and can never alter them.
                        * Client-facing communication cannot be dispatched without human approval.
                          Attempting it returns 409 APPROVAL_REQUIRED — the gate is in the backend,
                          not in the UI.
                        * Every response carries provenance: FACT, DERIVED_FACT, RULE_OUTPUT,
                          AI_INFERENCE or UNKNOWN.
                        * Every consequential action writes an audit event with actor and timestamp.
                        """)
                .contact(new Contact().name("GyanSys").email("intellirelease@gyansys.com"))
                .license(new License().name("Proprietary — GyanSys AI Innovation Challenge 2026")));
    }
}
