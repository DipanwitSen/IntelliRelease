package com.gyansys.intellirelease.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gyansys.intellirelease.adapters.notify.EmailProvider;
import com.gyansys.intellirelease.adapters.notify.SecurityEmailTemplate;
import com.gyansys.intellirelease.config.IntelliReleaseProperties;
import com.gyansys.intellirelease.infra.AuditWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The sign-in confirmation endpoint.
 *
 * <p>HTTP Basic itself has no "login moment" — credentials travel on every
 * request and Spring Security accepts or rejects each one independently. This
 * endpoint exists to give the frontend (and this backend) exactly one: the
 * login screen calls it once, a 401 here means the credentials were wrong
 * (unlike probing {@code /actuator/health}, which is public and accepts
 * anything), and a 200 is the one moment a "you signed in" notification makes
 * sense to send.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Sign-in confirmation")
public class AuthController {

    private final EmailProvider emailProvider;
    private final SecurityEmailTemplate emailTemplate;
    private final IntelliReleaseProperties.Email emailConfig;
    private final AuditWriter auditWriter;

    public AuthController(EmailProvider emailProvider,
                          SecurityEmailTemplate emailTemplate,
                          IntelliReleaseProperties properties,
                          AuditWriter auditWriter) {
        this.emailProvider = emailProvider;
        this.emailTemplate = emailTemplate;
        this.emailConfig = properties.email();
        this.auditWriter = auditWriter;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginResponse(String username, List<String> roles, OffsetDateTime loggedInAt) {
    }

    @PostMapping("/login")
    @Operation(
            summary = "Confirm sign-in",
            description = """
                    Reaching this method at all means Spring Security already accepted the
                    Basic Auth credentials — a 401 never gets here. Records an audit entry and
                    sends a "you signed in" notification (MailHog always; Outlook too if the
                    relay is configured) before returning who signed in and with what roles.
                    """)
    public LoginResponse login(Authentication authentication, HttpServletRequest request) {
        String username = authentication.getName();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        OffsetDateTime now = OffsetDateTime.now();
        String sourceIp = request.getRemoteAddr();

        auditWriter.record("USER_LOGIN", "User", username, "Signed in from " + sourceIp);

        String subject = "[IntelliRelease] Sign-in: " + username;
        String html = emailTemplate.renderHtml(username, roles, now, sourceIp);
        String text = emailTemplate.renderText(username, roles, now, sourceIp);
        emailProvider.send(emailConfig.securityNotificationRecipient(), subject, html, text);

        return new LoginResponse(username, roles, now);
    }
}
