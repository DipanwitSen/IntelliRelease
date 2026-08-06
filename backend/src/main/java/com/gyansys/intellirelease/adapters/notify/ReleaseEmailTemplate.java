package com.gyansys.intellirelease.adapters.notify;

import com.gyansys.intellirelease.adapters.ai.AiSynthesisResponse;
import com.gyansys.intellirelease.model.Release;
import com.gyansys.intellirelease.model.enums.RiskLevel;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

/**
 * Renders one audience's release-note email as table-based HTML with inline
 * styles — the subset that survives Outlook's Word rendering engine, MailHog's
 * raw view, and a phone client alike. A plain-text sibling ({@link #renderText})
 * is sent alongside it in the same message for clients that don't render HTML.
 */
@Component
public class ReleaseEmailTemplate {

    public String renderHtml(Release release, String audience, String note, AiSynthesisResponse notes) {
        String riskColor = riskColor(release.getAggregateRiskLevel());
        StringBuilder changelog = new StringBuilder();
        for (AiSynthesisResponse.ChangelogBullet bullet : notes.changelogBullets()) {
            changelog.append("<tr>")
                    .append("<td style=\"padding:6px 10px;border-bottom:1px solid #e5e7eb;color:#6b7280;font-size:13px;white-space:nowrap;\">")
                    .append(bullet.prNumber() == null ? "&mdash;" : "#" + bullet.prNumber())
                    .append(bullet.ticketKey() == null || bullet.ticketKey().isBlank() ? "" : " &middot; " + escape(bullet.ticketKey()))
                    .append("</td>")
                    .append("<td style=\"padding:6px 10px;border-bottom:1px solid #e5e7eb;color:#111827;font-size:13px;\">")
                    .append(escape(bullet.text()))
                    .append("</td></tr>");
        }

        String changelogBlock = changelog.isEmpty() ? "" : """
                <tr><td style="padding:24px 32px 8px 32px;">
                  <div style="font-size:12px;font-weight:600;letter-spacing:.04em;color:#6b7280;text-transform:uppercase;">What shipped</div>
                </td></tr>
                <tr><td style="padding:0 32px 24px 32px;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;border:1px solid #e5e7eb;border-radius:6px;overflow:hidden;">
                    %s
                  </table>
                </td></tr>
                """.formatted(changelog);

        String risksBlock = (notes.knownRisks() == null || notes.knownRisks().isBlank()) ? "" : """
                <tr><td style="padding:0 32px 24px 32px;">
                  <div style="font-size:12px;font-weight:600;letter-spacing:.04em;color:#6b7280;text-transform:uppercase;margin-bottom:6px;">Known risks</div>
                  <div style="font-size:14px;line-height:1.6;color:#374151;">%s</div>
                </td></tr>
                """.formatted(paragraphs(notes.knownRisks()));

        String recommendationBlock = (notes.deploymentRecommendation() == null || notes.deploymentRecommendation().isBlank()) ? "" : """
                <tr><td style="padding:0 32px 24px 32px;">
                  <div style="font-size:12px;font-weight:600;letter-spacing:.04em;color:#6b7280;text-transform:uppercase;margin-bottom:6px;">Deployment recommendation</div>
                  <div style="font-size:14px;line-height:1.6;color:#374151;">%s</div>
                </td></tr>
                """.formatted(paragraphs(notes.deploymentRecommendation()));

        String provenance = notes.fallback()
                ? "Deterministic fallback content (AI narration was unavailable when this was generated)."
                : "AI-generated narration (provider: " + escape(notes.provider() == null ? "unknown" : notes.provider()) + "), from deterministic SAP Commerce facts — not an approval decision.";

        return """
                <!doctype html>
                <html>
                <body style="margin:0;padding:0;background:#f3f4f6;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f3f4f6;padding:24px 0;">
                    <tr><td align="center">
                      <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;border:1px solid #e5e7eb;">
                        <tr>
                          <td style="background:#111827;padding:20px 32px;">
                            <span style="color:#9ca3af;font-size:12px;letter-spacing:.06em;text-transform:uppercase;">IntelliRelease &middot; %s</span>
                            <div style="color:#ffffff;font-size:20px;font-weight:700;margin-top:4px;">%s %s</div>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:16px 32px 0 32px;">
                            <span style="display:inline-block;padding:4px 10px;border-radius:999px;background:%s;color:#ffffff;font-size:12px;font-weight:600;">%s RISK</span>
                          </td>
                        </tr>
                        <tr><td style="padding:20px 32px 8px 32px;">
                          <div style="font-size:14px;line-height:1.6;color:#111827;">%s</div>
                        </td></tr>
                        %s
                        %s
                        %s
                        <tr><td style="padding:20px 32px;background:#f9fafb;border-top:1px solid #e5e7eb;">
                          <div style="font-size:12px;color:#9ca3af;line-height:1.5;">
                            %s<br/>
                            AI explains; it never approves or decides. This release was approved by a human before this email was sent.<br/>
                            Sent by IntelliRelease for %s.
                          </div>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                escape(audience), escape(release.getRepoName()), escape(release.getVersion()),
                riskColor, release.getAggregateRiskLevel() == null ? "UNKNOWN" : release.getAggregateRiskLevel().name(),
                paragraphs(note),
                changelogBlock, risksBlock, recommendationBlock,
                provenance, escape(audience));
    }

    public String renderText(Release release, String audience, String note, AiSynthesisResponse notes) {
        StringBuilder text = new StringBuilder();
        text.append("IntelliRelease — ").append(release.getRepoName()).append(' ').append(release.getVersion())
                .append(" (").append(audience).append(")\n\n");
        text.append(note).append("\n\n");

        if (!notes.changelogBullets().isEmpty()) {
            text.append("What shipped:\n");
            for (AiSynthesisResponse.ChangelogBullet bullet : notes.changelogBullets()) {
                text.append("  - ")
                        .append(bullet.prNumber() == null ? "" : "#" + bullet.prNumber() + " ")
                        .append(bullet.text()).append('\n');
            }
            text.append('\n');
        }
        if (notes.knownRisks() != null && !notes.knownRisks().isBlank()) {
            text.append("Known risks: ").append(notes.knownRisks()).append("\n\n");
        }
        if (notes.deploymentRecommendation() != null && !notes.deploymentRecommendation().isBlank()) {
            text.append("Deployment recommendation: ").append(notes.deploymentRecommendation()).append("\n\n");
        }
        text.append(notes.fallback()
                ? "(Deterministic fallback content, AI narration unavailable.)"
                : "(AI-generated narration, provider: " + notes.provider() + ".)");
        return text.toString();
    }

    private static String riskColor(RiskLevel level) {
        if (level == null) {
            return "#6b7280";
        }
        return switch (level) {
            case HIGH -> "#dc2626";
            case MEDIUM -> "#d97706";
            case LOW -> "#16a34a";
        };
    }

    private static String paragraphs(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();
        StringBuilder html = new StringBuilder();
        for (String line : lines) {
            html.append("<p style=\"margin:0 0 10px 0;\">").append(escape(line)).append("</p>");
        }
        return html.toString();
    }

    private static String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }
}
