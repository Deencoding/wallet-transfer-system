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
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(wallet.getAvailableBalance().scale()).isEqualTo(2);
        assertThat(wallet.getLedgerBalance()).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    void supportsFreezeAndReactivation() {
        Wallet wallet = new Wallet(UUID.randomUUID(), UUID.randomUUID(), now);
        wallet.changeStatus(WalletStatus.FROZEN, now.plusSeconds(1));
        wallet.changeStatus(WalletStatus.ACTIVE, now.plusSeconds(2));
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }

    @Test
    void closedWalletCannotBeReopened() {
        Wallet wallet = new Wallet(UUID.randomUUID(), UUID.randomUUID(), now);
        wallet.changeStatus(WalletStatus.CLOSED, now.plusSeconds(1));
        assertThatThrownBy(() -> wallet.changeStatus(WalletStatus.ACTIVE, now.plusSeconds(2)))
                .isInstanceOf(InvalidWalletStateTransitionException.class);
    }

    @Test
    void sameStateTransitionIsRejected() {
        Wallet wallet = new Wallet(UUID.randomUUID(), UUID.randomUUID(), now);
        assertThatThrownBy(() -> wallet.changeStatus(WalletStatus.ACTIVE, now.plusSeconds(1)))
                .isInstanceOf(InvalidWalletStateTransitionException.class);
    }
}
