package com.wallettransfer.transfers.service;

import com.wallettransfer.ledger.service.LedgerService;
import com.wallettransfer.outbox.service.OutboxService;
import com.wallettransfer.shared.money.Money;
import com.wallettransfer.transfers.dto.CreateTransferRequest;
import com.wallettransfer.transfers.dto.TransferResponse;
import com.wallettransfer.transfers.event.TransferCompletedEvent;
import com.wallettransfer.transfers.exception.TransferNotFoundException;
import com.wallettransfer.transfers.exception.WalletBusyException;
import com.wallettransfer.transfers.model.Transfer;
import com.wallettransfer.transfers.repository.TransferRepository;
import com.wallettransfer.wallets.dto.WalletMovement;
import com.wallettransfer.wallets.dto.WalletResponse;
import com.wallettransfer.wallets.service.WalletService;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final TransferRepository transfers;
    private final WalletService wallets;
    private final LedgerService ledger;
    private final OutboxService outbox;
    private final TransferMetrics metrics;
    private final Clock clock;

    public TransferService(
            TransferRepository transfers,
            WalletService wallets,
            LedgerService ledger,
            OutboxService outbox,
            TransferMetrics metrics,
            Clock clock) {
        this.transfers = transfers;
        this.wallets = wallets;
        this.ledger = ledger;
        this.outbox = outbox;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public TransferResponse create(UUID ownerId, CreateTransferRequest request) {
        return create(ownerId, request, null, null);
    }

    @Transactional
    public TransferResponse create(
            UUID ownerId, CreateTransferRequest request, String idempotencyKey, UUID idempotencyRecordId) {
        Timer.Sample sample = metrics.start();
        try {
            Money money = new Money(request.amount(), request.currency());
            if (money.amount().signum() <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            UUID senderId = wallets.getWalletId(ownerId, money.currency());
            Instant now = clock.instant();
            String reference =
                    "TRF-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
            Transfer transfer = new Transfer(
                    UUID.randomUUID(),
                    reference,
                    senderId,
                    request.receiverWalletId(),
                    money.amount(),
                    money.currency(),
                    request.description(),
                    idempotencyKey,
                    idempotencyRecordId,
                    now);
            transfer.start(now);
            transfers.save(transfer);
            WalletMovement movement = wallets.moveFunds(ownerId, request.receiverWalletId(), money);
            ledger.postTransfer(
                    reference,
                    movement.senderWalletId(),
                    movement.receiverWalletId(),
                    money.amount(),
                    money.currency(),
                    request.description());
            transfer.succeed(clock.instant());
            transfers.saveAndFlush(transfer);
            String eventAmount = money.amount().toPlainString();
            TransferCompletedEvent event = new TransferCompletedEvent(
                    transfer.getId(),
                    reference,
                    movement.senderWalletId(),
                    movement.receiverWalletId(),
                    eventAmount,
                    money.currency().name(),
                    transfer.getCompletedAt());
            outbox.append("TRANSFER", transfer.getId(), "TransferCompleted", 1, event);
            metrics.successfulAfterCommit(sample);
            return TransferResponse.from(transfer);
        } catch (PessimisticLockingFailureException exception) {
            metrics.failed(sample);
            throw new WalletBusyException();
        } catch (RuntimeException exception) {
            metrics.failed(sample);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public TransferResponse get(UUID ownerId, String reference) {
        WalletResponse wallet = wallets.getMyWallet(ownerId);
        Transfer transfer = transfers
                .findByReferenceAndSenderWalletIdOrReferenceAndReceiverWalletId(
                        reference, wallet.id(), reference, wallet.id())
                .orElseThrow(TransferNotFoundException::new);
        return TransferResponse.from(transfer);
    }

    @Transactional(readOnly = true)
    public Page<TransferResponse> list(UUID ownerId, int page, int size) {
        var wallet = wallets.getMyWallet(ownerId);
        int pageNumber = Math.max(page, 0);
        int pageSize = Math.clamp(size, 1, 100);
        Sort.Order createdAtOrder = Sort.Order.desc("createdAt");
        Sort.Order idOrder = Sort.Order.desc("id");
        Sort sort = Sort.by(createdAtOrder, idOrder);
        var pageable = PageRequest.of(pageNumber, pageSize, sort);
        Page<Transfer> transferPage =
                transfers.findBySenderWalletIdOrReceiverWalletId(wallet.id(), wallet.id(), pageable);
        return transferPage.map(TransferResponse::from);
    }

    @Transactional
    public Transfer lockForReversal(String reference) {
        return transfers.findByReferenceForUpdate(reference).orElseThrow(TransferNotFoundException::new);
    }
}
