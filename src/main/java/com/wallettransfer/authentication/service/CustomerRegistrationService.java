package com.wallettransfer.authentication.service;

import com.wallettransfer.ledger.service.LedgerService;
import com.wallettransfer.users.dto.UserProfileResponse;
import com.wallettransfer.users.model.User;
import com.wallettransfer.users.service.UserService;
import com.wallettransfer.wallets.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerRegistrationService {
    private final UserService users;
    private final WalletService wallets;
    private final LedgerService ledger;

    public CustomerRegistrationService(UserService users, WalletService wallets, LedgerService ledger) {
        this.users = users;
        this.wallets = wallets;
        this.ledger = ledger;
    }

    @Transactional
    public UserProfileResponse register(String email, String passwordHash) {
        User user = users.createCustomer(email, passwordHash);
        var wallet = wallets.createInitialWallet(user.getId());
        ledger.createWalletAccount(wallet.id(), wallet.currency());
        return UserProfileResponse.from(user);
    }
}
