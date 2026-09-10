package com.wallettransfer.authentication.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "jwt_id", nullable = false, unique = true)
    private UUID jwtId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_fingerprint", nullable = false, unique = true, length = 64)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefreshTokenStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_reason", length = 30)
    private RefreshTokenRevocationReason revocationReason;

    @Column(name = "replaced_by_token_id")
    private UUID replacedByTokenId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    private long version;

    protected RefreshToken() {}

    public RefreshToken(
            UUID id, UUID userId, UUID jwtId, UUID familyId, String fingerprint, Instant issuedAt, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.jwtId = jwtId;
        this.familyId = familyId;
        this.fingerprint = fingerprint;
        this.status = RefreshTokenStatus.ACTIVE;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getJwtId() {
        return jwtId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public RefreshTokenStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void revoke(RefreshTokenRevocationReason reason, UUID replacement, Instant now) {
        status = RefreshTokenStatus.REVOKED;
        revocationReason = reason;
        replacedByTokenId = replacement;
        lastUsedAt = now;
        revokedAt = now;
    }
}
