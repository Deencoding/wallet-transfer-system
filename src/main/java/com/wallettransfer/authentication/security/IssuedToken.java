package com.wallettransfer.authentication.security;

import java.time.Instant;
import java.util.UUID;

public record IssuedToken(String value, UUID jwtId, UUID familyId, Instant issuedAt, Instant expiresAt) {}
