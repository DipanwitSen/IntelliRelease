package com.gyansys.intellirelease.adapters.notify;

import com.gyansys.intellirelease.config.IntelliReleaseProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * SMTP dispatch, to up to two destinations per message:
 *
 * <ol>
 *   <li>MailHog — always attempted, needs no credentials. This is the POC's
 *       record of "did the send actually happen", visible at localhost:8025.</li>
 *   <li>A real SMTP AUTH relay (Office365/Outlook), only when
 *       {@code intellirelease.email.relay} has credentials. Mirrors the same
 *       message so a release manager can see it land in an actual inbox.</li>
 * </ol>
 *
 * <p>Every message is sent as HTML with a plain-text alternative, so it reads
 * well in Outlook, a phone mail client, or MailHog's raw view alike.
 *
 * <p>A failed send is reported to the caller, never swallowed: a release
 * manager who thinks notes went out when they didn't is worse off than one
 * who is told the send failed.
 */
@Component
public class EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(EmailProvider.class);

    private final JavaMailSender mailHogSender;
    private final JavaMailSender outlookSender;
    private final String from;
    private final IntelliReleaseProperties.Email.Relay relay;

    public EmailProvider(@Qualifier("mailHogSender") JavaMailSender mailHogSender,
                         @Qualifier("outlookMailSender") JavaMailSender outlookMailSender,
                         IntelliReleaseProperties properties) {
        this.mailHogSender = mailHogSender;
        this.outlookSender = outlookMailSender;
        this.from = properties.email().from();
        this.relay = properties.email().relay();
    }

    /**
     * @param to      audience distribution address (MailHog always sees this exact address)
     * @param subject email subject
     * @param html    HTML body
     * @param text    plain-text alternative, for clients that don't render HTML
     * @return outcome for both legs — see {@link Outcome}
     */
    public Outcome send(String to, String subject, String html, String text) {
        if (to == null || to.isBlank()) {
            return new Outcome(false, relay.isConfigured(), false);
        }

        boolean captured = sendVia(mailHogSender, from, to, subject, html, text, "MailHog");

        boolean relayed = false;
        if (relay.isConfigured()) {
            String recipient = relay.effectiveRecipient(to);
            relayed = sendVia(outlookSender, relay.username(), recipient, subject, html, text, "Outlook relay");
        }

        return new Outcome(captured, relay.isConfigured(), relayed);
    }

    private boolean sendVia(JavaMailSender sender, String fromAddress, String to, String subject,
                            String html, String text, String label) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            sender.send(message);
            return true;
        } catch (MailException | jakarta.mail.MessagingException exception) {
            log.warn("{} send to {} failed: {}", label, to, exception.getMessage());
            return false;
        }
    }

    /** @param sent whether MailHog captured the message — the value callers treat as "was this sent" */
    public record Outcome(boolean sent, boolean relayConfigured, boolean relayed) {
    }
}
