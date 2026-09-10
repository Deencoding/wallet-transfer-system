package com.wallettransfer.idempotency.controller;

import com.wallettransfer.idempotency.service.IdempotencyService;
import com.wallettransfer.transfers.dto.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
public class IdempotentTransferController {
    private final IdempotencyService idempotency;

    public IdempotentTransferController(IdempotencyService idempotency) {
        this.idempotency = idempotency;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreateTransferRequest request) {
        var response = idempotency.executeTransfer(UUID.fromString(jwt.getSubject()), key, request);
        return ResponseEntity.created(URI.create("/api/v1/transfers/" + response.reference()))
                .body(response);
    }
}
