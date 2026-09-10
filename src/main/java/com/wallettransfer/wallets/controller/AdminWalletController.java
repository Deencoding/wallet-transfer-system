package com.wallettransfer.wallets.controller;

import com.wallettransfer.wallets.dto.UpdateWalletStatusRequest;
import com.wallettransfer.wallets.dto.WalletResponse;
import com.wallettransfer.wallets.service.WalletService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/wallets")
public class AdminWalletController {
    private final WalletService wallets;

    public AdminWalletController(WalletService wallets) {
        this.wallets = wallets;
    }

    @PatchMapping("/{walletId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    WalletResponse changeStatus(@PathVariable UUID walletId, @Valid @RequestBody UpdateWalletStatusRequest request) {
        return wallets.changeStatus(walletId, request.status());
    }
}
