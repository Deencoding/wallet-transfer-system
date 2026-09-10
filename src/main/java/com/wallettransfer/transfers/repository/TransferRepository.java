package com.wallettransfer.transfers.repository;

import com.wallettransfer.transfers.model.Transfer;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Transfer t where t.reference=:reference")
    Optional<Transfer> findByReferenceForUpdate(
            @org.springframework.data.repository.query.Param("reference") String reference);

    Optional<Transfer> findByReferenceAndSenderWalletIdOrReferenceAndReceiverWalletId(
            String senderReference, UUID senderWalletId, String receiverReference, UUID receiverWalletId);

    Page<Transfer> findBySenderWalletIdOrReceiverWalletId(
            UUID senderWalletId, UUID receiverWalletId, Pageable pageable);
}
