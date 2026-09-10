package com.wallettransfer.providers.client;

import com.wallettransfer.providers.dto.ProviderTransferResult;
import com.wallettransfer.providersimulator.dto.SimulatorTransferRequest;
import com.wallettransfer.providersimulator.service.ProviderSimulatorService;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class SimulatorBankTransferProvider implements BankTransferProvider {
    private final ProviderSimulatorService simulator;

    public SimulatorBankTransferProvider(ProviderSimulatorService simulator) {
        this.simulator = simulator;
    }

    public ProviderTransferResult create(String request, String token, BigDecimal amount, String currency) {
        var result = simulator.submit(new SimulatorTransferRequest(request, token, amount, currency));
        return map(result);
    }

    public ProviderTransferResult queryByRequestReference(String request) {
        return map(simulator.statusByRequest(request));
    }

    private ProviderTransferResult map(com.wallettransfer.providersimulator.dto.SimulatorTransferResponse value) {
        return new ProviderTransferResult(
                value.requestReference(), value.providerReference(), value.status(), value.failureReason());
    }
}
