package com.wallettransfer.reversals.controller;

import com.wallettransfer.reversals.dto.*;
import com.wallettransfer.reversals.service.TransferReversalService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers/{reference}/reverse")
public class TransferReversalController {
    private final TransferReversalService service;

    public TransferReversalController(TransferReversalService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReversalResponse> reverse(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String reference,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreateReversalRequest request) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        var response = service.reverse(actorId, reference, key, request);
        URI location = URI.create("/api/v1/transfers/" + reference + "/reverse");
        return ResponseEntity.created(location).body(response);
    }
}
