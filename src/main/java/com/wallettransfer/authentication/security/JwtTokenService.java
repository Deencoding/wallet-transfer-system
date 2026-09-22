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
        List<String> audience = List.of(properties.accessAudience());
        List<String> roleNames = roles.stream().map(Enum::name).sorted().toList();
        JwtClaimsSet claims = base(userId, id, issued, expires)
                .audience(audience)
                .claim("token_type", "access")
                .claim("roles", roleNames)
                .build();
        String token = encode(claims);
        return new IssuedToken(token, id, null, issued, expires);
    }

    public IssuedToken issueRefreshToken(UUID userId, UUID familyId) {
        Instant issued = clock.instant();
        Instant expires = issued.plus(properties.refreshTokenTtl());
        UUID id = UUID.randomUUID();
        List<String> audience = List.of(properties.refreshAudience());
        JwtClaimsSet claims = base(userId, id, issued, expires)
                .audience(audience)
                .claim("token_type", "refresh")
                .claim("family_id", familyId.toString())
                .build();
        String token = encode(claims);
        return new IssuedToken(token, id, familyId, issued, expires);
    }

    public RefreshTokenClaims decodeRefresh(String token) {
        try {
            Jwt jwt = refreshDecoder.decode(token);
            UUID userId = UUID.fromString(jwt.getSubject());
            UUID jwtId = UUID.fromString(jwt.getId());
            UUID familyId = UUID.fromString(jwt.getClaimAsString("family_id"));
            return new RefreshTokenClaims(userId, jwtId, familyId, jwt.getExpiresAt());
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
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build();
        JwtEncoderParameters parameters = JwtEncoderParameters.from(header, claims);
        Jwt token = encoder.encode(parameters);
        return token.getTokenValue();
    }
}
