package com.wallettransfer.providers.security;

import com.wallettransfer.providers.configuration.ProviderProperties;
import com.wallettransfer.providers.exception.InvalidWebhookSignatureException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class WebhookSignatureVerifier {

    private final ProviderProperties properties;
    private final Clock clock;

    public WebhookSignatureVerifier(ProviderProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void verify(String timestamp, String body, String signature) {
        try {
            if (signature == null || !signature.matches("[0-9a-fA-F]{64}")) {
                throw new InvalidWebhookSignatureException();
            }
            Instant sent = Instant.ofEpochSecond(Long.parseLong(timestamp));
            if (Duration.between(sent, clock.instant()).abs().compareTo(properties.webhookTolerance()) > 0) {
                throw new InvalidWebhookSignatureException();
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            byte[] actual = HexFormat.of().parseHex(signature);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new InvalidWebhookSignatureException();
            }
        } catch (InvalidWebhookSignatureException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidWebhookSignatureException();
        }
    }
}
