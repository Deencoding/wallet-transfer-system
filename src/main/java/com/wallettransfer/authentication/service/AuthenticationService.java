package com.wallettransfer.authentication.service;

import com.wallettransfer.authentication.dto.TokenResponse;
import com.wallettransfer.authentication.exception.AuthenticationFailedException;
import com.wallettransfer.authentication.exception.InvalidCredentialsException;
import com.wallettransfer.authentication.exception.RefreshTokenRevokedException;
import com.wallettransfer.authentication.model.RefreshToken;
import com.wallettransfer.authentication.model.RefreshTokenRevocationReason;
import com.wallettransfer.authentication.model.RefreshTokenStatus;
import com.wallettransfer.authentication.repository.RefreshTokenRepository;
import com.wallettransfer.authentication.security.IssuedToken;
import com.wallettransfer.authentication.security.JwtTokenService;
import com.wallettransfer.authentication.security.PasswordService;
import com.wallettransfer.authentication.security.RefreshTokenClaims;
import com.wallettransfer.shared.security.service.SecurityAuditService;
import com.wallettransfer.users.dto.UserProfileResponse;
import com.wallettransfer.users.model.User;
import com.wallettransfer.users.service.UserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {
    private static final String DUMMY_HASH = "$2a$12$wD8fH1OZGjysfjnUtUN0jeA3gS5zlpwiKNSI2nX2fFpNNh.KuHNDq";
    private final UserService users;
    private final PasswordService passwords;
    private final JwtTokenService tokens;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;
    private final CustomerRegistrationService registration;
    private final SecurityAuditService securityAudit;

    public AuthenticationService(
            UserService users,
            PasswordService passwords,
            JwtTokenService tokens,
            RefreshTokenRepository refreshTokens,
            Clock clock,
            CustomerRegistrationService registration,
            SecurityAuditService securityAudit) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
        this.registration = registration;
        this.securityAudit = securityAudit;
    }

    public UserProfileResponse register(String email, String password) {
        String passwordHash = passwords.hash(password);
        return registration.register(email, passwordHash);
    }

    @Transactional
    public TokenResponse login(String email, String password) {
        User user = users.findForAuthentication(email).orElse(null);
        boolean matches = passwords.matches(password, user == null ? DUMMY_HASH : user.getPasswordHash());
        if (!matches || user == null || !user.canAuthenticate()) {
            securityAudit.authentication(email, user == null ? null : user.getId(), false, "INVALID_CREDENTIALS");
            throw new InvalidCredentialsException();
        }
        users.recordSuccessfulLogin(user.getId());
        securityAudit.authentication(email, user.getId(), true, null);
        return issuePair(user, UUID.randomUUID());
    }

    @Transactional(noRollbackFor = {RefreshTokenRevokedException.class, AuthenticationFailedException.class})
    public TokenResponse refresh(String rawToken) {
        RefreshTokenClaims claims = tokens.decodeRefresh(rawToken);
        RefreshToken current =
                refreshTokens.findByJwtIdForUpdate(claims.jwtId()).orElseThrow(RefreshTokenRevokedException::new);
        Instant now = clock.instant();
        if (!sameToken(current, claims, rawToken)) {
            throw new RefreshTokenRevokedException();
        }
        if (current.getStatus() != RefreshTokenStatus.ACTIVE
                || !current.getExpiresAt().isAfter(now)) {
            refreshTokens.revokeActiveFamily(current.getFamilyId(), RefreshTokenRevocationReason.REUSE_DETECTED, now);
            securityAudit.actorEvent("REFRESH_TOKEN_REUSE", current.getUserId(), "REJECTED", "REUSE_DETECTED");
            throw new RefreshTokenRevokedException();
        }
        User user = users.getById(current.getUserId());
        if (!user.canAuthenticate()) {
            refreshTokens.revokeActiveFamily(current.getFamilyId(), RefreshTokenRevocationReason.USER_DISABLED, now);
            throw new AuthenticationFailedException();
        }
        IssuedToken access = tokens.issueAccessToken(user.getId(), user.getRoleNames());
        IssuedToken refresh = tokens.issueRefreshToken(user.getId(), current.getFamilyId());
        RefreshToken replacement = persistRefresh(user.getId(), refresh);
        current.revoke(RefreshTokenRevocationReason.ROTATED, replacement.getId(), now);
        return response(access, refresh);
    }

    @Transactional
    public void logout(UUID authenticatedUserId, String rawToken) {
        RefreshTokenClaims claims = tokens.decodeRefresh(rawToken);
        RefreshToken current =
                refreshTokens.findByJwtIdForUpdate(claims.jwtId()).orElseThrow(RefreshTokenRevokedException::new);
        if (!authenticatedUserId.equals(claims.userId()) || !sameToken(current, claims, rawToken)) {
            throw new RefreshTokenRevokedException();
        }
        refreshTokens.revokeActiveFamily(current.getFamilyId(), RefreshTokenRevocationReason.LOGOUT, clock.instant());
        securityAudit.actorEvent("LOGOUT", authenticatedUserId, "SUCCESS", null);
    }

    private boolean sameToken(RefreshToken current, RefreshTokenClaims claims, String rawToken) {
        if (!current.getUserId().equals(claims.userId())
                || !current.getFamilyId().equals(claims.familyId())) {
            return false;
        }
        byte[] storedFingerprint = current.getFingerprint().getBytes(StandardCharsets.US_ASCII);
        byte[] suppliedFingerprint = fingerprint(rawToken).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(storedFingerprint, suppliedFingerprint);
    }

    private TokenResponse issuePair(User user, UUID family) {
        IssuedToken access = tokens.issueAccessToken(user.getId(), user.getRoleNames());
        IssuedToken refresh = tokens.issueRefreshToken(user.getId(), family);
        persistRefresh(user.getId(), refresh);
        return response(access, refresh);
    }

    private RefreshToken persistRefresh(UUID userId, IssuedToken token) {
        UUID tokenId = UUID.randomUUID();
        String tokenFingerprint = fingerprint(token.value());
        RefreshToken refreshToken = new RefreshToken(
                tokenId,
                userId,
                token.jwtId(),
                token.familyId(),
                tokenFingerprint,
                token.issuedAt(),
                token.expiresAt());
        return refreshTokens.save(refreshToken);
    }

    private TokenResponse response(IssuedToken access, IssuedToken refresh) {
        long accessExpiresIn =
                Duration.between(access.issuedAt(), access.expiresAt()).toSeconds();
        long refreshExpiresIn =
                Duration.between(refresh.issuedAt(), refresh.expiresAt()).toSeconds();
        return new TokenResponse(access.value(), refresh.value(), "Bearer", accessExpiresIn, refreshExpiresIn);
    }

    private String fingerprint(String token) {
        try {
            MessageDigest hasher = MessageDigest.getInstance("SHA-256");
            byte[] input = token.getBytes(StandardCharsets.UTF_8);
            byte[] digest = hasher.digest(input);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
