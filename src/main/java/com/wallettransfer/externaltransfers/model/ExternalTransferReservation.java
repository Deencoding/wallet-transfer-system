package com.wallettransfer.externaltransfers.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_transfer_reservations")
public class ExternalTransferReservation {
    @Id
    private UUID id;

    @Column(name = "external_transfer_id")
    private UUID transferId;

    @Column(name = "wallet_id")
    private UUID walletId;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected ExternalTransferReservation() {}

    public ExternalTransferReservation(UUID id, UUID transferId, UUID walletId, BigDecimal amount, Instant now) {
        this.id = id;
        this.transferId = transferId;
        this.walletId = walletId;
        this.amount = amount;
        status = ReservationStatus.ACTIVE;
        createdAt = now;
    }

    public void settle(Instant now) {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Reservation already resolved");
        }
        status = ReservationStatus.SETTLED;
        resolvedAt = now;
    }

    public void release(Instant now) {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Reservation already resolved");
        }
        status = ReservationStatus.RELEASED;
        resolvedAt = now;
    }

    public ReservationStatus getStatus() {
        return status;
    }
}
