package com.wallettransfer.providersimulator.service;

import com.wallettransfer.providersimulator.dto.*;
import com.wallettransfer.providersimulator.exception.SimulatedProviderTimeoutException;
import com.wallettransfer.providersimulator.model.SimulatedProviderTransfer;
import com.wallettransfer.providersimulator.repository.SimulatedProviderTransferRepository;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderSimulatorService {
    private final SimulatedProviderTransferRepository repository;
    private final Clock clock;

    public ProviderSimulatorService(SimulatedProviderTransferRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public SimulatorTransferResponse submit(SimulatorTransferRequest request) {
        var existing = repository.findByRequestReference(request.requestReference());
        if (existing.isPresent()) {
            return response(existing.get(), null);
        }
        String status = request.beneficiaryToken().equals("SIM-FAIL")
                ? "FAILED"
                : (request.beneficiaryToken().equals("SIM-PENDING")
                                || request.beneficiaryToken().equals("SIM-TIMEOUT"))
                        ? "PENDING"
                        : "SUCCESSFUL";
        String provider = "SIM-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        var transfer = repository.saveAndFlush(new SimulatedProviderTransfer(
                UUID.randomUUID(),
                request.requestReference(),
                provider,
                request.beneficiaryToken(),
                request.amount(),
                request.currency(),
                status,
                clock.instant()));
        if (request.beneficiaryToken().equals("SIM-TIMEOUT")
                || request.beneficiaryToken().equals("SIM-TIMEOUT-SUCCESS")) {
            throw new SimulatedProviderTimeoutException();
        }
        return response(transfer, status.equals("FAILED") ? "PROVIDER_REJECTED" : null);
    }

    @Transactional(readOnly = true)
    public SimulatorTransferResponse status(String providerReference) {
        return repository
                .findByProviderReference(providerReference)
                .map(value -> response(value, null))
                .orElseThrow();
    }

    @Transactional(readOnly = true)
    public SimulatorTransferResponse statusByRequest(String requestReference) {
        return repository
                .findByRequestReference(requestReference)
                .map(value -> response(value, null))
                .orElseThrow();
    }

    private SimulatorTransferResponse response(SimulatedProviderTransfer value, String reason) {
        return new SimulatorTransferResponse(
                value.getRequestReference(), value.getProviderReference(), value.getStatus(), reason);
    }
}
