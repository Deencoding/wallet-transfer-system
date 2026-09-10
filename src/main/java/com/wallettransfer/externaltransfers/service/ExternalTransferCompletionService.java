package com.wallettransfer.externaltransfers.service;

import com.wallettransfer.externaltransfers.model.*;
import com.wallettransfer.externaltransfers.repository.*;
import com.wallettransfer.ledger.service.LedgerService;
import com.wallettransfer.wallets.service.WalletService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalTransferCompletionService {
    private final ExternalTransferRepository transfers;
    private final ExternalTransferReservationRepository reservations;
    private final WalletService wallets;
    private final LedgerService ledger;
    private final Clock clock;

    public ExternalTransferCompletionService(
            ExternalTransferRepository transfers,
            ExternalTransferReservationRepository reservations,
            WalletService wallets,
            LedgerService ledger,
            Clock clock) {
        this.transfers = transfers;
        this.reservations = reservations;
        this.wallets = wallets;
        this.ledger = ledger;
        this.clock = clock;
    }

    @Transactional
    public void start(UUID id) {
        var transfer = transfers.findByIdForUpdate(id).orElseThrow();
        if (transfer.getStatus() == ExternalTransferStatus.PENDING) {
            transfer.processing(clock.instant());
        }
    }

    @Transactional
    public void successful(UUID id, String providerReference) {
        var transfer = transfers.findByIdForUpdate(id).orElseThrow();
        if (transfer.getStatus() == ExternalTransferStatus.SUCCESSFUL) {
            return;
        }
        if (transfer.getStatus() == ExternalTransferStatus.FAILED)
            throw new IllegalStateException("Failed transfer cannot succeed");
        var reservation = reservations.findByTransferId(id).orElseThrow();
        wallets.settleExternal(transfer.getSenderWalletId(), transfer.getAmount());
        ledger.postExternalTransfer(
                transfer.getReference(),
                transfer.getSenderWalletId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getDescription());
        reservation.settle(clock.instant());
        transfer.succeed(providerReference, clock.instant());
    }

    @Transactional
    public void failed(UUID id, String reason) {
        var transfer = transfers.findByIdForUpdate(id).orElseThrow();
        if (transfer.getStatus() == ExternalTransferStatus.FAILED) {
            return;
        }
        if (transfer.getStatus() == ExternalTransferStatus.SUCCESSFUL)
            throw new IllegalStateException("Successful transfer cannot fail");
        var reservation = reservations.findByTransferId(id).orElseThrow();
        wallets.releaseExternal(transfer.getSenderWalletId(), transfer.getAmount());
        reservation.release(clock.instant());
        transfer.fail(reason, clock.instant());
    }

    @Transactional
    public void uncertain(UUID id) {
        uncertain(id, "Provider outcome is uncertain");
    }

    @Transactional
    public void uncertain(UUID id, String error) {
        var transfer = transfers.findByIdForUpdate(id).orElseThrow();
        if (transfer.getStatus() == ExternalTransferStatus.PROCESSING
                || transfer.getStatus() == ExternalTransferStatus.PENDING_PROVIDER_CONFIRMATION)
            transfer.uncertain(clock.instant(), error);
    }
}
