package com.gyansys.intellirelease.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Authentication and authorization for the POC.
 *
 * <p>Seeded users with roles, replaced by Entra ID (Lilly's IdP) or Keycloak in
 * an enterprise deployment. The roles themselves are not POC scaffolding — the
 * approval endpoints check {@code ROLE_APPROVER}, and that check is what makes
 * the governance gate real rather than a UI convention.
 *
 * <p>The webhook endpoint is deliberately unauthenticated: GitHub cannot present
 * a bearer token. It authenticates by HMAC signature instead, verified in
 * {@link com.gyansys.intellirelease.adapters.git.WebhookSignatureVerifier}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    public static final String ROLE_APPROVER = "APPROVER";
    public static final String ROLE_RELEASE_MANAGER = "RELEASE_MANAGER";
    public static final String ROLE_QA = "QA";
    public static final String ROLE_DEVELOPER = "DEVELOPER";

    private final IntelliReleaseProperties properties;

    public SecurityConfig(IntelliReleaseProperties properties) {
        this.properties = properties;
    }

    /**
     * Repository full names ({@code owner/repo}) travel as a single, slash-encoded
     * path segment — see {@code TomcatConfig}. Spring Security's default firewall
     * rejects an encoded slash before the request ever reaches routing, regardless
     * of the servlet container's own setting, so it needs its own opt-in here too.
     */
    @Bean
    public org.springframework.security.web.firewall.HttpFirewall httpFirewall() {
        var firewall = new org.springframework.security.web.firewall.StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        return firewall;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // Stateless API with no cookie-based session, so there is no CSRF
                // vector to protect. Re-enable the moment a session appears.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Signed by HMAC, not by a bearer token.
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/**").permitAll()
                        .requestMatchers(
                                "/swagger-ui.html", "/swagger-ui/**", "/api-docs/**",
                                "/actuator/health", "/actuator/info",
                                "/h2-console/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Approval is the governance boundary. Only an approver crosses it.
                        .requestMatchers(HttpMethod.POST, "/api/v1/releases/*/approve")
                            .hasRole(ROLE_APPROVER)
                        .requestMatchers(HttpMethod.POST, "/api/v1/releases/*/reject")
                            .hasRole(ROLE_APPROVER)
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                // The H2 console renders in a frame; only relevant in dev.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    /**
     * Seeded POC identities. Passwords are development-only and the whole bean
     * is replaced by the customer's IdP in any real deployment.
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("developer")
                        .password(encoder.encode("developer"))
                        .roles(ROLE_DEVELOPER).build(),
                User.withUsername("qa")
                        .password(encoder.encode("qa"))
                        .roles(ROLE_QA).build(),
                User.withUsername("releasemanager")
                        .password(encoder.encode("releasemanager"))
                        .roles(ROLE_RELEASE_MANAGER, ROLE_APPROVER).build(),
                User.withUsername("approver")
                        .password(encoder.encode("approver"))
                        .roles(ROLE_APPROVER).build()
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        // Actuator health/info are public (see authorizeHttpRequests above) so the
        // dashboard can show live backend status; CORS must be registered
        // separately since it does not fall under /api/**.
        source.registerCorsConfiguration("/actuator/**", configuration);
        return source;
    }
}
