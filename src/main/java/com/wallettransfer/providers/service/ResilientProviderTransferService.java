package com.wallettransfer.providers.service;

import com.wallettransfer.providers.client.BankTransferProvider;
import com.wallettransfer.providers.dto.ProviderTransferResult;
import com.wallettransfer.providers.exception.UncertainProviderOutcomeException;
import com.wallettransfer.providersimulator.exception.SimulatedProviderTimeoutException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class ResilientProviderTransferService {

    private final BankTransferProvider provider;
    private final CircuitBreaker circuit;
    private final Retry retry;
    private final ProviderMetrics metrics;

    public ResilientProviderTransferService(
            BankTransferProvider provider, CircuitBreaker circuit, Retry retry, ProviderMetrics metrics) {
        this.provider = provider;
        this.circuit = circuit;
        this.retry = retry;
        this.metrics = metrics;
    }

    public ProviderTransferResult create(String request, String token, BigDecimal amount, String currency) {
        Timer.Sample sample = metrics.start();
        try {
            Supplier<ProviderTransferResult> call = () -> provider.create(request, token, amount, currency);
            ProviderTransferResult result = Retry.decorateSupplier(
                            retry, CircuitBreaker.decorateSupplier(circuit, call))
                    .get();
            metrics.record(sample, "create", normalizeOutcome(result.status()));
            return result;
        } catch (SimulatedProviderTimeoutException timeout) {
            metrics.record(sample, "create", "timeout");
            throw new UncertainProviderOutcomeException(timeout);
        } catch (RuntimeException failure) {
            metrics.record(sample, "create", "failed");
            throw failure;
        }
    }

    public ProviderTransferResult query(String request) {
        Timer.Sample sample = metrics.start();
        try {
            ProviderTransferResult result = CircuitBreaker.decorateSupplier(
                            circuit, () -> provider.queryByRequestReference(request))
                    .get();
            metrics.record(sample, "query", normalizeOutcome(result.status()));
            return result;
        } catch (RuntimeException failure) {
            metrics.record(sample, "query", "failed");
            throw failure;
        }
    }

    private String normalizeOutcome(String status) {
        return switch (status) {
            case "SUCCESSFUL" -> "successful";
            case "PENDING" -> "pending";
            default -> "failed";
        };
    }
}
