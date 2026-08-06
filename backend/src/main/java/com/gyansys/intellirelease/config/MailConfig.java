package com.gyansys.intellirelease.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Both {@code JavaMailSender} beans, defined explicitly rather than leaning on
 * Spring Boot's {@code spring.mail.*} autoconfiguration for either.
 *
 * <p>That autoconfigured bean's name is not the documented, stable
 * {@code "mailSender"} some guides assume — with a second {@code JavaMailSender}
 * bean on the context (the Outlook relay below), unqualified injection of the
 * autoconfigured one silently resolved to the <em>wrong</em> sender instead of
 * failing loudly, which is how this shipped once already: MailHog sends were
 * quietly being routed at {@code smtp.office365.com} and rejected for auth.
 * Owning both beans by name here removes the guess entirely.
 */
@Configuration
public class MailConfig {

    @Bean
    @Primary
    public JavaMailSenderImpl mailHogSender(
            @Value("${SMTP_HOST:localhost}") String host,
            @Value("${SMTP_PORT:1025}") int port) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setDefaultEncoding("UTF-8");
        return sender;
    }

    @Bean
    public JavaMailSenderImpl outlookMailSender(IntelliReleaseProperties properties) {
        IntelliReleaseProperties.Email.Relay relay = properties.email().relay();

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(relay.host());
        sender.setPort(relay.port());
        sender.setUsername(relay.username());
        sender.setPassword(relay.password());
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        return sender;
    }
}
