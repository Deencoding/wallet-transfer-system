package com.wallettransfer.wallets.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wallettransfer.shared.money.Currency;
import com.wallettransfer.wallets.exception.InvalidWalletStateTransitionException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void startsAsAnActiveZeroBalanceNgnWallet() {
        Wallet wallet = new Wallet(UUID.randomUUID(), UUID.randomUUID(), now);
        assertThat(wallet.getCurrency()).isEqualTo(Currency.NGN);
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        BigDecimal expectedBalance = new BigDecimal("0.00");
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(expectedBalance);
        assertThat(wallet.getAvailableBalance().scale()).isEqualTo(2);
        assertThat(wallet.getLedgerBalance()).isEqualByComparingTo(expectedBalance);
    }

    @Test
    void supportsFreezeAndReactivation() {
        var frozenAt = now.plusSeconds(1);
        var reactivatedAt = now.plusSeconds(2);
        Wallet wallet = new Wallet(UUID.randomUUID(), UUID.randomUUID(), now);
        wallet.changeStatus(WalletStatus.FROZEN, frozenAt);
        wallet.changeStatus(WalletStatus.ACTIVE, reactivatedAt);
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }

    @Test
    void closedWalletCannotBeReopened() {
        var closedAt = now.plusSeconds(1);
        var reopenAttemptAt = now.plusSeconds(2);
        Wallet wallet = new Wallet(UUID.randomUUID(), UUID.randomUUID(), now);
        wallet.changeStatus(WalletStatus.CLOSED, closedAt);
        assertThatThrownBy(() -> wallet.changeStatus(WalletStatus.ACTIVE, reopenAttemptAt))
                .isInstanceOf(InvalidWalletStateTransitionException.class);
    }

    @Test
    void sameStateTransitionIsRejected() {
        var attemptedAt = now.plusSeconds(1);
        Wallet wallet = new Wallet(UUID.randomUUID(), UUID.randomUUID(), now);
        assertThatThrownBy(() -> wallet.changeStatus(WalletStatus.ACTIVE, attemptedAt))
                .isInstanceOf(InvalidWalletStateTransitionException.class);
    }
}
