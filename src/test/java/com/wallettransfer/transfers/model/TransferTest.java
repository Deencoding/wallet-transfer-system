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
        var t = new Transfer(
                UUID.randomUUID(),
                "TRF-X",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10.00"),
                Currency.NGN,
                null,
                now);
        assertThatThrownBy(() -> t.succeed(now)).isInstanceOf(InvalidTransferStateException.class);
        t.start(now);
        t.succeed(now.plusSeconds(1));
        assertThat(t.getStatus()).isEqualTo(TransferStatus.SUCCESSFUL);
        assertThat(t.getCompletedAt()).isNotNull();
        assertThatThrownBy(() -> t.start(now)).isInstanceOf(InvalidTransferStateException.class);
    }
}
