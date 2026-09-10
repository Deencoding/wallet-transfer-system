package com.wallettransfer.wallets.dto;

import com.wallettransfer.wallets.model.WalletStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateWalletStatusRequest(@NotNull WalletStatus status) {}
