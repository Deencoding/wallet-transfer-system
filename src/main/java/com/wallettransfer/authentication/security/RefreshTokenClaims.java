package com.wallettransfer.authentication.security;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenClaims(UUID userId, UUID jwtId, UUID familyId, Instant expiresAt) {}
