package com.wallettransfer.authentication.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfiguration {
    @Bean
    JwtEncoder jwtEncoder(KeyPair pair) {
        RSAKey key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
    }

    @Bean
    @Qualifier("accessJwtDecoder") JwtDecoder accessJwtDecoder(KeyPair pair, JwtProperties properties) {
        return decoder((RSAPublicKey) pair.getPublic(), properties, properties.accessAudience(), "access");
    }

    @Bean
    @Qualifier("refreshJwtDecoder") JwtDecoder refreshJwtDecoder(KeyPair pair, JwtProperties properties) {
        return decoder((RSAPublicKey) pair.getPublic(), properties, properties.refreshAudience(), "refresh");
    }

    private JwtDecoder decoder(RSAPublicKey key, JwtProperties properties, String audience, String type) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key)
                .signatureAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> purpose = jwt -> jwt.getAudience().contains(audience)
                        && type.equals(jwt.getClaimAsString("token_type"))
                        && validRequiredClaims(jwt)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid token purpose", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(java.time.Duration.ofSeconds(30)),
                new JwtIssuerValidator(properties.issuer()),
                purpose));
        return decoder;
    }

    private boolean validRequiredClaims(Jwt jwt) {
        try {
            java.util.UUID.fromString(jwt.getSubject());
            java.util.UUID.fromString(jwt.getId());
            return jwt.getIssuedAt() != null
                    && jwt.getExpiresAt() != null
                    && !jwt.getAudience().isEmpty();
        } catch (RuntimeException invalid) {
            return false;
        }
    }
}
