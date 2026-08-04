package com.gyansys.intellirelease;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * IntelliRelease v2 — AI-Powered SAP Commerce Release Intelligence &amp;
 * Deployment Governance Platform.
 *
 * <p>One Spring Boot deployable. Twelve logical modules as packages, never
 * microservices. This application is the single orchestrator: Angular never
 * reaches the database or GitHub, and the Python AI service is a compute
 * component reached over REST that holds no credentials and no authority.
 *
 * <p>The invariant every package obeys, in this order:
 * <pre>
 *   System facts
 *     -> SAP Commerce deterministic intelligence
 *     -> explainable rule engines
 *     -> AI explanation
 *     -> human approval
 *     -> communication
 * </pre>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class IntelliReleaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntelliReleaseApplication.class, args);
    }
}
