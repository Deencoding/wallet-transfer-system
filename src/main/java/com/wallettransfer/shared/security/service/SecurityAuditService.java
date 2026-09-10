package com.wallettransfer.shared.security.service;

import com.wallettransfer.shared.correlation.CorrelationIdFilter;
import com.wallettransfer.shared.security.model.SecurityEvent;
import com.wallettransfer.shared.security.repository.SecurityEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityAuditService {

    private static final int MINIMUM_PEPPER_LENGTH = 32;

    private final SecurityEventRepository events;
    private final Clock clock;
    private final byte[] pepper;

    public SecurityAuditService(
            SecurityEventRepository events, Clock clock, @Value("${application.security.audit-pepper}") String pepper) {
        if (pepper == null || pepper.length() < MINIMUM_PEPPER_LENGTH) {
            throw new IllegalArgumentException("Security audit pepper must contain at least 32 characters");
        }
        this.events = events;
        this.clock = clock;
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void authentication(String email, UUID actorId, boolean successful, String reason) {
        append(
                "AUTHENTICATION",
                successful ? "SUCCESS" : "FAILURE",
                actorId,
                fingerprint(email == null ? "" : email.trim().toLowerCase(Locale.ROOT)),
                null,
                reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejected(String type, String principal, String clientAddress, String reason) {
        append(type, "REJECTED", null, fingerprint(principal), fingerprint(clientAddress), reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void actorEvent(String type, UUID actorId, String outcome, String reason) {
        append(type, outcome, actorId, null, null, reason);
    }

    private void append(
            String type, String outcome, UUID actorId, String principalHash, String addressHash, String reason) {
        events.saveAndFlush(new SecurityEvent(
                UUID.randomUUID(),
                type,
                outcome,
                actorId,
                principalHash,
                addressHash,
                null,
                null,
                reason,
                MDC.get(CorrelationIdFilter.MDC_KEY),
                clock.instant(),
                "{}"));
    }

    public String fingerprint(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Could not fingerprint security principal", failure);
        }
    }
}
