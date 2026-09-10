package com.wallettransfer.providersimulator.controller;

import com.wallettransfer.providersimulator.dto.*;
import com.wallettransfer.providersimulator.service.ProviderSimulatorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/simulator/v1/transfers")
@ConditionalOnProperty(name = "application.provider.simulator-enabled", havingValue = "true", matchIfMissing = true)
public class ProviderSimulatorController {
    private final ProviderSimulatorService service;

    public ProviderSimulatorController(ProviderSimulatorService service) {
        this.service = service;
    }

    @PostMapping
    public SimulatorTransferResponse submit(@RequestBody SimulatorTransferRequest request) {
        return service.submit(request);
    }

    @GetMapping("/{reference}")
    public SimulatorTransferResponse status(@PathVariable String reference) {
        return service.status(reference);
    }
}
