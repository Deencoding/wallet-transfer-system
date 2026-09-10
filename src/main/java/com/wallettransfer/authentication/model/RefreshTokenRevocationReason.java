package com.wallettransfer.authentication.model;

public enum RefreshTokenRevocationReason {
    ROTATED,
    LOGOUT,
    REUSE_DETECTED,
    USER_DISABLED
}
