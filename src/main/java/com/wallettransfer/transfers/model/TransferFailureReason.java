package com.wallettransfer.transfers.model;

public enum TransferFailureReason {
    INSUFFICIENT_FUNDS,
    SENDER_WALLET_UNAVAILABLE,
    RECEIVER_WALLET_UNAVAILABLE,
    CURRENCY_MISMATCH,
    SAME_WALLET,
    INVALID_AMOUNT
}
