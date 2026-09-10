package com.wallettransfer.reversals.repository;

import com.wallettransfer.reversals.model.TransferReversal;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferReversalRepository extends JpaRepository<TransferReversal, UUID> {
    Optional<TransferReversal> findByRequestedByAndIdempotencyKey(UUID userId, String key);

    Optional<TransferReversal> findByOriginalTransferId(UUID transferId);
}
