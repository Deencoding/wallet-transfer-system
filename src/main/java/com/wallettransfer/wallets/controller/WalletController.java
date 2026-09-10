package com.wallettransfer.wallets.controller;

import com.wallettransfer.wallets.dto.WalletResponse;
import com.wallettransfer.wallets.service.WalletService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {
    private final WalletService wallets;

    public WalletController(WalletService wallets) {
        this.wallets = wallets;
    }

    @GetMapping("/me")
    WalletResponse me(@AuthenticationPrincipal Jwt jwt) {
        return wallets.getMyWallet(UUID.fromString(jwt.getSubject()));
    }
}
