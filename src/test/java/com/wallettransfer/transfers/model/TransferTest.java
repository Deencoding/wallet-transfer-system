package com.wallettransfer.transfers.model;

import static org.assertj.core.api.Assertions.*;

import com.wallettransfer.shared.money.Currency;
import com.wallettransfer.transfers.exception.InvalidTransferStateException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferTest {
    @Test
    void enforcesSuccessfulStateMachine() {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var completedAt = now.plusSeconds(1);
        BigDecimal transferAmount = new BigDecimal("10.00");
        var t = new Transfer(
                UUID.randomUUID(),
                "TRF-X",
                UUID.randomUUID(),
                UUID.randomUUID(),
                transferAmount,
                Currency.NGN,
                null,
                now);
        assertThatThrownBy(() -> t.succeed(now)).isInstanceOf(InvalidTransferStateException.class);
        t.start(now);
        t.succeed(completedAt);
        assertThat(t.getStatus()).isEqualTo(TransferStatus.SUCCESSFUL);
        assertThat(t.getCompletedAt()).isNotNull();
        assertThatThrownBy(() -> t.start(now)).isInstanceOf(InvalidTransferStateException.class);
    }
}
