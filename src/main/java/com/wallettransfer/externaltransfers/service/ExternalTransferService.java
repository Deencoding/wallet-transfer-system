package com.wallettransfer.externaltransfers.service;

import com.wallettransfer.externaltransfers.dto.*;
import com.wallettransfer.externaltransfers.event.ExternalTransferRequestedEvent;
import com.wallettransfer.externaltransfers.exception.*;
import com.wallettransfer.externaltransfers.model.*;
import com.wallettransfer.externaltransfers.repository.*;
import com.wallettransfer.outbox.service.OutboxService;
import com.wallettransfer.shared.money.Money;
import com.wallettransfer.wallets.service.WalletService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalTransferService {
    public static final String TOPIC = "wallet.external-transfer.requests.v1";
    private final ExternalTransferRepository transfers;
    private final ExternalTransferReservationRepository reservations;
    private final WalletService wallets;
    private final OutboxService outbox;
    private final Clock clock;

    public ExternalTransferService(
            ExternalTransferRepository transfers,
            ExternalTransferReservationRepository reservations,
            WalletService wallets,
            OutboxService outbox,
            Clock clock) {
        this.transfers = transfers;
        this.reservations = reservations;
        this.wallets = wallets;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public ExternalTransferResponse create(UUID ownerId, String key, CreateExternalTransferRequest request) {
        validateKey(key);
        var money = new Money(request.amount(), request.currency());
        if (money.amount().signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        String fingerprint = fingerprint(request);
        UUID walletId = wallets.lockExternalWallet(ownerId, money.currency());
        var existing = transfers.findByOwnerIdAndIdempotencyKey(ownerId, key);
        if (existing.isPresent()) {
            if (!existing.get().getRequestFingerprint().equals(fingerprint)) {
                throw new ExternalIdempotencyConflictException();
            }
            return ExternalTransferResponse.from(existing.get());
        }
        wallets.reserveExternal(ownerId, money);
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        String reference = "EXT-" + compact(), providerRequest = "PRQ-" + compact();
        var transfer = new ExternalTransfer(
                id,
                reference,
                ownerId,
                walletId,
                money.amount(),
                money.currency(),
                request.beneficiaryToken(),
                providerRequest,
                request.description(),
                key,
                fingerprint,
                now);
        transfers.save(transfer);
        reservations.save(new ExternalTransferReservation(UUID.randomUUID(), id, walletId, money.amount(), now));
        outbox.appendTo(
                TOPIC,
                "EXTERNAL_TRANSFER",
                id,
                "ExternalTransferRequested",
                1,
                new ExternalTransferRequestedEvent(
                        id,
                        providerRequest,
                        request.beneficiaryToken(),
                        money.amount().toPlainString(),
                        money.currency().name()));
        return ExternalTransferResponse.from(transfer);
    }

    @Transactional(readOnly = true)
    public ExternalTransferResponse get(UUID ownerId, String reference) {
        return ExternalTransferResponse.from(transfers
                .findByOwnerIdAndReference(ownerId, reference)
                .orElseThrow(ExternalTransferNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public Page<ExternalTransferResponse> list(UUID ownerId, int page, int size) {
        return transfers
                .findByOwnerId(
                        ownerId,
                        PageRequest.of(
                                Math.max(0, page),
                                Math.min(100, Math.max(1, size)),
                                Sort.by("createdAt").descending()))
                .map(ExternalTransferResponse::from);
    }

    private void validateKey(String key) {
        if (key == null || key.length() < 8 || key.length() > 255 || !key.equals(key.trim())) {
            throw new IllegalArgumentException("Invalid Idempotency-Key");
        }
    }

    private String fingerprint(CreateExternalTransferRequest request) {
        try {
            String canonical = request.amount().stripTrailingZeros().toPlainString() + "|" + request.currency() + "|"
                    + request.beneficiaryToken() + "|" + Objects.toString(request.description(), "");
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String compact() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
