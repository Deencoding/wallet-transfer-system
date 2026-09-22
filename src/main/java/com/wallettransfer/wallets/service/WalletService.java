package com.wallettransfer.wallets.service;

import com.wallettransfer.shared.exception.DomainErrorCode;
import com.wallettransfer.shared.money.Currency;
import com.wallettransfer.shared.money.Money;
import com.wallettransfer.wallets.dto.WalletLedgerSnapshot;
import com.wallettransfer.wallets.dto.WalletMovement;
import com.wallettransfer.wallets.dto.WalletResponse;
import com.wallettransfer.wallets.exception.ConcurrentWalletUpdateException;
import com.wallettransfer.wallets.exception.WalletAlreadyExistsException;
import com.wallettransfer.wallets.exception.WalletNotFoundException;
import com.wallettransfer.wallets.exception.WalletTransferRejectedException;
import com.wallettransfer.wallets.model.LockedWalletPair;
import com.wallettransfer.wallets.model.Wallet;
import com.wallettransfer.wallets.model.WalletStatus;
import com.wallettransfer.wallets.repository.WalletRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {
    private final WalletRepository wallets;
    private final Clock clock;

    public WalletService(WalletRepository wallets, Clock clock) {
        this.wallets = wallets;
        this.clock = clock;
    }

    @Transactional
    public WalletResponse createInitialWallet(UUID ownerId) {
        if (wallets.existsByOwnerIdAndCurrency(ownerId, Currency.NGN)) {
            throw new WalletAlreadyExistsException();
        }
        try {
            Wallet wallet = new Wallet(UUID.randomUUID(), ownerId, clock.instant());
            Wallet savedWallet = wallets.saveAndFlush(wallet);
            return WalletResponse.from(savedWallet);
        } catch (DataIntegrityViolationException exception) {
            throw new WalletAlreadyExistsException();
        }
    }

    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(UUID ownerId) {
        Wallet wallet =
                wallets.findByOwnerIdAndCurrency(ownerId, Currency.NGN).orElseThrow(WalletNotFoundException::new);
        return WalletResponse.from(wallet);
    }

    @Transactional(readOnly = true)
    public UUID getWalletId(UUID ownerId, Currency currency) {
        return wallets.findIdByOwnerIdAndCurrency(ownerId, currency).orElseThrow(WalletNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public UUID getOwnerId(UUID walletId) {
        Wallet wallet = wallets.findById(walletId).orElseThrow(WalletNotFoundException::new);
        return wallet.getOwnerId();
    }

    @Transactional(readOnly = true)
    public WalletLedgerSnapshot getLedgerSnapshot(UUID walletId) {
        Wallet wallet = wallets.findById(walletId).orElseThrow(WalletNotFoundException::new);
        return new WalletLedgerSnapshot(wallet.getId(), wallet.getLedgerBalance());
    }

    @Transactional
    public WalletMovement moveFunds(UUID ownerId, UUID receiverId, Money money) {
        UUID senderId =
                wallets.findIdByOwnerIdAndCurrency(ownerId, money.currency()).orElseThrow(WalletNotFoundException::new);
        if (senderId.equals(receiverId))
            throw new WalletTransferRejectedException(
                    DomainErrorCode.SAME_WALLET_TRANSFER, "Sender and receiver wallets must differ");
        LockedWalletPair lockedWallets = lockWalletPair(senderId, receiverId);
        Wallet sender = lockedWallets.source();
        Wallet receiver = validateTransferWalletsAndGetReceiver(money, lockedWallets, sender);
        if (sender.getAvailableBalance().compareTo(money.amount()) < 0)
            throw new WalletTransferRejectedException(
                    DomainErrorCode.INSUFFICIENT_FUNDS, "Insufficient available funds");
        Instant now = clock.instant();
        sender.debit(money.amount(), now);
        receiver.credit(money.amount(), now);
        return new WalletMovement(sender.getId(), receiver.getId(), money.currency());
    }

    private static @NonNull Wallet validateTransferWalletsAndGetReceiver(
            Money money, LockedWalletPair lockedWallets, Wallet sender) {
        Wallet receiver = lockedWallets.destination();
        if (receiver.getCurrency() != money.currency())
            throw new WalletTransferRejectedException(
                    DomainErrorCode.CURRENCY_MISMATCH, "Wallet currencies do not match");
        if (sender.getStatus() != WalletStatus.ACTIVE)
            throw new WalletTransferRejectedException(
                    DomainErrorCode.SENDER_WALLET_UNAVAILABLE, "Sender wallet cannot initiate transfers");
        if (receiver.getStatus() == WalletStatus.CLOSED)
            throw new WalletTransferRejectedException(
                    DomainErrorCode.RECEIVER_WALLET_UNAVAILABLE, "Receiver wallet cannot receive transfers");
        return receiver;
    }

    @Transactional
    public void reverseInternalTransfer(UUID originalReceiverId, UUID originalSenderId, Money money) {
        LockedWalletPair lockedWallets = lockWalletPair(originalReceiverId, originalSenderId);
        Wallet receiver = lockedWallets.source();
        Wallet sender = lockedWallets.destination();
        if (receiver.getCurrency() != money.currency() || sender.getCurrency() != money.currency())
            throw new WalletTransferRejectedException(
                    DomainErrorCode.CURRENCY_MISMATCH, "Wallet currencies do not match");
        receiver.debit(money.amount(), clock.instant());
        sender.credit(money.amount(), clock.instant());
    }

    private LockedWalletPair lockWalletPair(UUID sourceId, UUID destinationId) {
        UUID firstId = sourceId.compareTo(destinationId) < 0 ? sourceId : destinationId;
        UUID secondId = firstId.equals(sourceId) ? destinationId : sourceId;
        Wallet firstLocked = wallets.findByIdForUpdate(firstId).orElseThrow(WalletNotFoundException::new);
        Wallet secondLocked = wallets.findByIdForUpdate(secondId).orElseThrow(WalletNotFoundException::new);
        Wallet source = firstLocked.getId().equals(sourceId) ? firstLocked : secondLocked;
        Wallet destination = firstLocked.getId().equals(destinationId) ? firstLocked : secondLocked;
        return new LockedWalletPair(source, destination);
    }

    @Transactional
    public WalletResponse changeStatus(UUID walletId, WalletStatus status) {
        try {
            Wallet wallet = wallets.findById(walletId).orElseThrow(WalletNotFoundException::new);
            wallet.changeStatus(status, clock.instant());
            Wallet savedWallet = wallets.saveAndFlush(wallet);
            return WalletResponse.from(savedWallet);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ConcurrentWalletUpdateException();
        }
    }
}
