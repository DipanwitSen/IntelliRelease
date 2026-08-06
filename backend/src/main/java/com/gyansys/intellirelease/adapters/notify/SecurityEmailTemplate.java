package com.gyansys.intellirelease.adapters.notify;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Renders the "someone signed in" notification — same visual language as {@link ReleaseEmailTemplate}, far shorter. */
@Component
public class SecurityEmailTemplate {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss 'UTC'", Locale.US).withZone(java.time.ZoneOffset.UTC);

    public String renderHtml(String username, List<String> roles, OffsetDateTime at, String sourceIp) {
        String rolesText = roles.isEmpty() ? "no roles" : String.join(", ", roles);
        return """
                <!doctype html>
                <html>
                <body style="margin:0;padding:0;background:#f3f4f6;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f3f4f6;padding:24px 0;">
                    <tr><td align="center">
                      <table role="presentation" width="520" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;border:1px solid #e5e7eb;">
                        <tr>
                          <td style="background:#111827;padding:20px 32px;">
                            <span style="color:#9ca3af;font-size:12px;letter-spacing:.06em;text-transform:uppercase;">IntelliRelease &middot; Security</span>
                            <div style="color:#ffffff;font-size:20px;font-weight:700;margin-top:4px;">New sign-in</div>
                          </td>
                        </tr>
                        <tr><td style="padding:24px 32px 8px 32px;">
                          <p style="margin:0 0 12px 0;font-size:14px;line-height:1.6;color:#111827;">
                            <strong>%s</strong> signed in to IntelliRelease.
                          </p>
                          <table role="presentation" cellpadding="0" cellspacing="0" style="font-size:13px;color:#374151;">
                            <tr><td style="padding:3px 12px 3px 0;color:#6b7280;">Time</td><td>%s</td></tr>
                            <tr><td style="padding:3px 12px 3px 0;color:#6b7280;">Roles</td><td>%s</td></tr>
                            <tr><td style="padding:3px 12px 3px 0;color:#6b7280;">Source</td><td>%s</td></tr>
                          </table>
                        </td></tr>
                        <tr><td style="padding:20px 32px;background:#f9fafb;border-top:1px solid #e5e7eb;">
                          <div style="font-size:12px;color:#9ca3af;line-height:1.5;">
                            Automated notice — sent on every successful sign-in. If this was not you, rotate the account's credentials.
                          </div>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(escape(username), TIMESTAMP.format(at), escape(rolesText), escape(sourceIp));
    }

    public String renderText(String username, List<String> roles, OffsetDateTime at, String sourceIp) {
        String rolesText = roles.isEmpty() ? "no roles" : String.join(", ", roles);
        return "IntelliRelease — new sign-in\n\n"
                + username + " signed in to IntelliRelease.\n"
                + "Time: " + TIMESTAMP.format(at) + "\n"
                + "Roles: " + rolesText + "\n"
                + "Source: " + sourceIp + "\n\n"
                + "Automated notice — sent on every successful sign-in. If this was not you, rotate the account's credentials.";
    }

    private static String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }
}
