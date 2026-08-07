package com.gyansys.intellirelease.application;

import com.gyansys.intellirelease.adapters.ai.AiSynthesisResponse;
import com.gyansys.intellirelease.adapters.notify.EmailProvider;
import com.gyansys.intellirelease.adapters.notify.ReleaseEmailTemplate;
import com.gyansys.intellirelease.adapters.notify.TeamsWebhookProvider;
import com.gyansys.intellirelease.config.IntelliReleaseProperties;
import com.gyansys.intellirelease.infra.AuditWriter;
import com.gyansys.intellirelease.model.Release;
import com.gyansys.intellirelease.model.enums.ReleaseStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dispatches release notes: one audience-specific email per distribution
 * list, and one combined post to Teams.
 *
 * <p>Every path here is gated on {@link ReleaseStatus#APPROVED}. This is the
 * backend control ARCHITECTURE.md's philosophy chain ends on — "AI explains. Humans
 * approve." — enforced structurally: {@link ApprovalRequiredException} is
 * thrown from this service, not checked in the controller, so there is no
 * code path that reaches an SMTP send or a Teams post without a human having
 * moved the release to APPROVED first.
 */
@Service
public class NotificationService {

    private final ReleaseService releaseService;
    private final EmailProvider emailProvider;
    private final ReleaseEmailTemplate emailTemplate;
    private final TeamsWebhookProvider teamsProvider;
    private final IntelliReleaseProperties.Email emailConfig;
    private final AuditWriter auditWriter;

    public NotificationService(ReleaseService releaseService,
                               EmailProvider emailProvider,
                               ReleaseEmailTemplate emailTemplate,
                               TeamsWebhookProvider teamsProvider,
                               IntelliReleaseProperties properties,
                               AuditWriter auditWriter) {
        this.releaseService = releaseService;
        this.emailProvider = emailProvider;
        this.emailTemplate = emailTemplate;
        this.teamsProvider = teamsProvider;
        this.emailConfig = properties.email();
        this.auditWriter = auditWriter;
    }

    /**
     * One line per audience.
     *
     * @param sent            whether MailHog captured the message
     * @param relayConfigured whether a real SMTP relay (Outlook) was configured for this send
     * @param relayed         whether the relay leg actually delivered
     */
    public record EmailOutcome(String audience, String recipient, boolean sent,
                               boolean relayConfigured, boolean relayed) {
    }

    public record DispatchResult(
            List<EmailOutcome> emails,
            boolean teamsConfigured,
            boolean teamsSent,
            boolean fallback,
            String provider
    ) {
    }

    @Transactional
    public Optional<DispatchResult> sendReleaseNotes(UUID releaseId, String actor) {
        Optional<Release> found = releaseService.get(releaseId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Release release = found.get();

        if (release.getStatus() != ReleaseStatus.APPROVED) {
            throw new ApprovalRequiredException(
                    "Release " + release.getVersion() + " is " + release.getStatus()
                            + ", not APPROVED. Client-facing communication cannot be sent until a human approves it.");
        }

        AiSynthesisResponse notes = releaseService.synthesizeRelease(release);
        String subjectPrefix = "[IntelliRelease] " + release.getRepoName() + " " + release.getVersion() + " — ";

        List<EmailOutcome> emails = new ArrayList<>();
        emails.add(sendAudienceEmail(release, "Developer", emailConfig.developerDistribution(),
                subjectPrefix + "Developer Notes", notes.developerNote(), notes));
        emails.add(sendAudienceEmail(release, "QA", emailConfig.qaDistribution(),
                subjectPrefix + "QA Notes", notes.qaNote(), notes));
        emails.add(sendAudienceEmail(release, "Business", emailConfig.businessDistribution(),
                subjectPrefix + "Business Notes", notes.businessNote(), notes));
        emails.add(sendAudienceEmail(release, "Client", emailConfig.clientDistribution(),
                subjectPrefix + "Client Notes", notes.clientNote(), notes));

        boolean teamsSent = teamsProvider.send(
                "Release " + release.getRepoName() + " " + release.getVersion() + " shipped",
                "**Risk:** " + (release.getAggregateRiskLevel() == null ? "UNKNOWN" : release.getAggregateRiskLevel())
                        + "  \n\n" + notes.releaseSummary() + "\n\n" + notes.clientNote());

        int sentCount = (int) emails.stream().filter(EmailOutcome::sent).count();
        int relayedCount = (int) emails.stream().filter(EmailOutcome::relayed).count();
        auditWriter.record("RELEASE_NOTES_SENT", "Release", release.getReleaseId().toString(),
                sentCount + "/" + emails.size() + " email(s) captured by MailHog, "
                        + relayedCount + " relayed to Outlook, sent by " + actor
                        + (teamsProvider.isConfigured() ? "; Teams post " + (teamsSent ? "succeeded" : "failed") : "; Teams not configured")
                        + (notes.fallback() ? " (deterministic fallback content, AI unavailable)" : " (AI-generated content)"));

        return Optional.of(new DispatchResult(emails, teamsProvider.isConfigured(), teamsSent,
                notes.fallback(), notes.provider()));
    }

    private EmailOutcome sendAudienceEmail(Release release, String audience, String recipient, String subject,
                                           String note, AiSynthesisResponse notes) {
        String html = emailTemplate.renderHtml(release, audience, note, notes);
        String text = emailTemplate.renderText(release, audience, note, notes);
        EmailProvider.Outcome outcome = emailProvider.send(recipient, subject, html, text);
        return new EmailOutcome(audience, recipient, outcome.sent(), outcome.relayConfigured(), outcome.relayed());
    }
}
