package com.wallettransfer.transfers.controller;

import com.wallettransfer.transfers.dto.*;
import com.wallettransfer.transfers.service.TransferService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {
    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @GetMapping("/{reference}")
    public TransferResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String reference) {
        return transfers.get(UUID.fromString(jwt.getSubject()), reference);
    }

    @GetMapping
    public Page<TransferResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return transfers.list(UUID.fromString(jwt.getSubject()), page, size);
    }
}
