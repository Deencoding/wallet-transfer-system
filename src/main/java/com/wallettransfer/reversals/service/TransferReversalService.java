package com.wallettransfer.reversals.service;

import com.wallettransfer.audit.service.ReversalAuditService;
import com.wallettransfer.ledger.service.LedgerService;
import com.wallettransfer.outbox.service.OutboxService;
import com.wallettransfer.reversals.dto.*;
import com.wallettransfer.reversals.event.TransferReversedEvent;
import com.wallettransfer.reversals.exception.*;
import com.wallettransfer.reversals.model.TransferReversal;
import com.wallettransfer.reversals.repository.TransferReversalRepository;
import com.wallettransfer.shared.money.Money;
import com.wallettransfer.transfers.model.TransferStatus;
import com.wallettransfer.transfers.service.TransferService;
import com.wallettransfer.wallets.service.WalletService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferReversalService {
    private final TransferReversalRepository reversals;
    private final TransferService transfers;
    private final WalletService wallets;
    private final LedgerService ledger;
    private final ReversalAuditService audit;
    private final OutboxService outbox;
    private final Clock clock;

    public TransferReversalService(
            TransferReversalRepository reversals,
            TransferService transfers,
            WalletService wallets,
            LedgerService ledger,
            ReversalAuditService audit,
            OutboxService outbox,
            Clock clock) {
        this.reversals = reversals;
        this.transfers = transfers;
        this.wallets = wallets;
        this.ledger = ledger;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public ReversalResponse reverse(UUID actorId, String transferReference, String key, CreateReversalRequest request) {
        validateKey(key);
        var transfer = transfers.lockForReversal(transferReference);
        String fingerprint = fingerprint(transferReference, request.reason());
        var replay = reversals.findByRequestedByAndIdempotencyKey(actorId, key);
        if (replay.isPresent()) {
            if (!replay.get().getRequestFingerprint().equals(fingerprint))
                throw new ReversalIdempotencyConflictException();
            return ReversalResponse.from(replay.get());
        }
        if (reversals.findByOriginalTransferId(transfer.getId()).isPresent())
            throw new ReversalAlreadyExistsException();
        if (transfer.getStatus() != TransferStatus.SUCCESSFUL) {
            throw new TransferNotReversibleException();
        }
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        String reference =
                "REV-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        var reversal = new TransferReversal(
                id,
                reference,
                transfer.getId(),
                transfer.getReference(),
                transfer.getAmount(),
                transfer.getCurrency(),
                request.reason(),
                actorId,
                key,
                fingerprint,
                now);
        reversal.start(now);
        reversals.save(reversal);
        var money = new Money(transfer.getAmount(), transfer.getCurrency());
        wallets.reverseInternalTransfer(transfer.getReceiverWalletId(), transfer.getSenderWalletId(), money);
        var posting = ledger.postReversal(
                reference,
                transfer.getReceiverWalletId(),
                transfer.getSenderWalletId(),
                money.amount(),
                money.currency(),
                request.reason());
        reversal.succeed(posting.journalId(), clock.instant());
        transfer.reverse(clock.instant());
        audit.record(id, actorId, transfer.getId(), transfer.getReference(), request.reason());
        outbox.append(
                "TRANSFER",
                transfer.getId(),
                "TransferReversed",
                1,
                new TransferReversedEvent(
                        id,
                        reference,
                        transfer.getId(),
                        transfer.getReference(),
                        money.amount().toPlainString(),
                        money.currency().name(),
                        reversal.getCompletedAt()));
        return ReversalResponse.from(reversals.saveAndFlush(reversal));
    }

    private void validateKey(String key) {
        if (key == null || key.length() < 8 || key.length() > 255 || !key.equals(key.trim()))
            throw new IllegalArgumentException("Invalid Idempotency-Key");
    }

    private String fingerprint(String reference, String reason) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest((reference + "|" + reason).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
