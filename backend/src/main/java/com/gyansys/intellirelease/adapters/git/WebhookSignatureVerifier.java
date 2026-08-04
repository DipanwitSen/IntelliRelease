package com.gyansys.intellirelease.adapters.git;

import com.gyansys.intellirelease.config.IntelliReleaseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.util.HexFormat;

/**
 * Verifies that an inbound webhook really came from the configured GitHub App.
 *
 * <p>HMAC-SHA256 over the exact raw request body, compared against
 * {@code X-Hub-Signature-256}. The comparison is constant-time:
 * {@code String.equals} short-circuits on the first differing byte, which leaks
 * how much of a forged signature was correct and turns forgery into a
 * measurable search.
 *
 * <p>The body must be the bytes as received. Re-serialising parsed JSON changes
 * whitespace and key order, and the signature stops matching.
 */
@Component
public class WebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureVerifier.class);

    private static final String ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final IntelliReleaseProperties.GitHub config;

    public WebhookSignatureVerifier(IntelliReleaseProperties properties) {
        this.config = properties.github();
    }

    /**
     * @param rawBody         the request body exactly as received
     * @param signatureHeader value of {@code X-Hub-Signature-256}
     * @return true when the signature is valid for the configured secret
     */
    public boolean isValid(String rawBody, String signatureHeader) {
        if (!config.hasWebhookSecret()) {
            // POC affordance: no secret configured means we cannot verify. The
            // event is still stored, flagged unverified, and labelled in the UI.
            log.warn("No webhook secret configured — event will be recorded with signature_valid=false");
            return false;
        }
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            log.warn("Webhook rejected: missing or malformed X-Hub-Signature-256 header");
            return false;
        }

        try {
            byte[] expected = computeHmac(rawBody, config.webhookSecret());
            byte[] provided = HexFormat.of().parseHex(signatureHeader.substring(SIGNATURE_PREFIX.length()));
            return MessageDigest.isEqual(expected, provided);
        } catch (IllegalArgumentException exception) {
            log.warn("Webhook rejected: signature header is not valid hexadecimal");
            return false;
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            log.error("HMAC verification failed to initialise", exception);
            return false;
        }
    }

    /** True when a secret is configured and verification is therefore meaningful. */
    public boolean isVerificationEnabled() {
        return config.hasWebhookSecret();
    }

    private byte[] computeHmac(String body, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
        return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
    }
}
