package com.wallettransfer.authentication.security;

import com.wallettransfer.authentication.exception.InvalidTokenException;
import com.wallettransfer.users.model.RoleName;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final JwtDecoder refreshDecoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenService(
            JwtEncoder encoder,
            @Qualifier("refreshJwtDecoder") JwtDecoder refreshDecoder,
            JwtProperties properties,
            Clock clock) {
        this.encoder = encoder;
        this.refreshDecoder = refreshDecoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issueAccessToken(UUID userId, Set<RoleName> roles) {
        Instant issued = clock.instant();
        Instant expires = issued.plus(properties.accessTokenTtl());
        UUID id = UUID.randomUUID();
        JwtClaimsSet claims = base(userId, id, issued, expires)
                .audience(List.of(properties.accessAudience()))
                .claim("token_type", "access")
                .claim("roles", roles.stream().map(Enum::name).sorted().toList())
                .build();
        return new IssuedToken(encode(claims), id, null, issued, expires);
    }

    public IssuedToken issueRefreshToken(UUID userId, UUID familyId) {
        Instant issued = clock.instant();
        Instant expires = issued.plus(properties.refreshTokenTtl());
        UUID id = UUID.randomUUID();
        JwtClaimsSet claims = base(userId, id, issued, expires)
                .audience(List.of(properties.refreshAudience()))
                .claim("token_type", "refresh")
                .claim("family_id", familyId.toString())
                .build();
        return new IssuedToken(encode(claims), id, familyId, issued, expires);
    }

    public RefreshTokenClaims decodeRefresh(String token) {
        try {
            Jwt jwt = refreshDecoder.decode(token);
            return new RefreshTokenClaims(
                    UUID.fromString(jwt.getSubject()),
                    UUID.fromString(jwt.getId()),
                    UUID.fromString(jwt.getClaimAsString("family_id")),
                    jwt.getExpiresAt());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException();
        }
    }

    private JwtClaimsSet.Builder base(UUID userId, UUID id, Instant issued, Instant expires) {
        return JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .id(id.toString())
                .issuedAt(issued)
                .expiresAt(expires);
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(), claims))
                .getTokenValue();
    }
}
