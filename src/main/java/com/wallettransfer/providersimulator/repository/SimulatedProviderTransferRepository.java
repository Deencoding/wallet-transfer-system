package com.wallettransfer.providersimulator.repository;

import com.wallettransfer.providersimulator.model.SimulatedProviderTransfer;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulatedProviderTransferRepository extends JpaRepository<SimulatedProviderTransfer, UUID> {
    Optional<SimulatedProviderTransfer> findByRequestReference(String reference);

    Optional<SimulatedProviderTransfer> findByProviderReference(String reference);
}
