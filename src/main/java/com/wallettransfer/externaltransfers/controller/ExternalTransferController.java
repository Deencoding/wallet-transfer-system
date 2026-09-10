package com.wallettransfer.externaltransfers.controller;

import com.wallettransfer.externaltransfers.dto.*;
import com.wallettransfer.externaltransfers.service.ExternalTransferService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/external-transfers")
public class ExternalTransferController {
    private final ExternalTransferService service;

    public ExternalTransferController(ExternalTransferService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ExternalTransferResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreateExternalTransferRequest request) {
        var response = service.create(UUID.fromString(jwt.getSubject()), key, request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/external-transfers/" + response.reference()))
                .body(response);
    }

    @GetMapping("/{reference}")
    public ExternalTransferResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String reference) {
        return service.get(UUID.fromString(jwt.getSubject()), reference);
    }

    @GetMapping
    public Page<ExternalTransferResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(UUID.fromString(jwt.getSubject()), page, size);
    }
}
