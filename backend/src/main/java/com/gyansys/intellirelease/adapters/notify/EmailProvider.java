package com.gyansys.intellirelease.adapters.notify;

import com.gyansys.intellirelease.config.IntelliReleaseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP dispatch. Points at MailHog in the POC ({@code spring.mail.host}), a
 * real relay in any other deployment — this class does not know or care
 * which, it only knows the address to send from and the message to send.
 *
 * <p>A failed send is reported to the caller, never swallowed: a release
 * manager who thinks notes went out when they didn't is worse off than one
 * who is told the send failed.
 */
@Component
public class EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(EmailProvider.class);

    private final JavaMailSender mailSender;
    private final String from;

    public EmailProvider(JavaMailSender mailSender, IntelliReleaseProperties properties) {
        this.mailSender = mailSender;
        this.from = properties.email().from();
    }

    /** @return true if the message was handed off to the SMTP server without error */
    public boolean send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
            return true;
        } catch (MailException exception) {
            log.warn("Failed to send email to {}: {}", to, exception.getMessage());
            return false;
        }
    }
}
