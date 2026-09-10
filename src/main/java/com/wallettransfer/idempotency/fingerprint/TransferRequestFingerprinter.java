package com.wallettransfer.idempotency.fingerprint;

import com.wallettransfer.transfers.dto.CreateTransferRequest;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class TransferRequestFingerprinter {
    public String fingerprint(CreateTransferRequest request) {
        String description = request.description();
        String canonical = "receiver=" + request.receiverWalletId() + "\namount="
                + request.amount().setScale(request.currency().scale()) + "\ncurrency=" + request.currency()
                + "\ndescription="
                + (description == null ? "null" : description.length() + ":" + description);
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
