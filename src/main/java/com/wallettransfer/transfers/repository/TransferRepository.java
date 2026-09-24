package com.wallettransfer.transfers.repository;

import com.wallettransfer.transfers.model.Transfer;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transfer t where t.reference=:reference")
    Optional<Transfer> findByReferenceForUpdate(@Param("reference") String reference);

    Optional<Transfer> findByReferenceAndSenderWalletIdOrReferenceAndReceiverWalletId(
            String senderReference, UUID senderWalletId, String receiverReference, UUID receiverWalletId);

    Page<Transfer> findBySenderWalletIdOrReceiverWalletId(
            UUID senderWalletId, UUID receiverWalletId, Pageable pageable);
}
