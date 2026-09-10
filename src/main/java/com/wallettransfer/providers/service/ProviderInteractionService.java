package com.wallettransfer.providers.service;

import com.wallettransfer.providers.model.ProviderInteraction;
import com.wallettransfer.providers.repository.ProviderInteractionRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProviderInteractionService {
    private final ProviderInteractionRepository repository;

    public ProviderInteractionService(ProviderInteractionRepository repository) {
        this.repository = repository;
    }

    public void record(
            UUID transferId,
            String requestReference,
            String token,
            String outcome,
            Integer code,
            String response,
            long duration,
            Instant start,
            Instant end) {
        String masked = token.length() <= 4 ? "****" : token.substring(0, 4) + "********";
        repository.save(new ProviderInteraction(
                UUID.randomUUID(),
                transferId,
                requestReference,
                masked,
                outcome,
                code,
                response,
                duration,
                start,
                end));
    }
}
