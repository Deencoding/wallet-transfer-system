package com.wallettransfer.externaltransfers.repository;

import com.wallettransfer.externaltransfers.model.ExternalTransferReservation;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalTransferReservationRepository extends JpaRepository<ExternalTransferReservation, UUID> {
    Optional<ExternalTransferReservation> findByTransferId(UUID transferId);
}
