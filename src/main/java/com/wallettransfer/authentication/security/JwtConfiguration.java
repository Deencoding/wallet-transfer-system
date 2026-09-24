package com.wallettransfer.authentication.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
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
        JWKSet keySet = new JWKSet(key);
        ImmutableJWKSet<SecurityContext> keySource = new ImmutableJWKSet<>(keySet);
        return new NimbusJwtEncoder(keySource);
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
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> purpose = jwt -> {
            if (jwt.getAudience().contains(audience)
                    && type.equals(jwt.getClaimAsString("token_type"))
                    && validRequiredClaims(jwt)) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error("invalid_token", "Invalid token purpose", null);
            return OAuth2TokenValidatorResult.failure(error);
        };
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ofSeconds(30));
        JwtIssuerValidator issuerValidator = new JwtIssuerValidator(properties.issuer());
        DelegatingOAuth2TokenValidator<Jwt> validator =
                new DelegatingOAuth2TokenValidator<>(timestampValidator, issuerValidator, purpose);
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private boolean validRequiredClaims(Jwt jwt) {
        try {
            UUID.fromString(jwt.getSubject());
            UUID.fromString(jwt.getId());
            return jwt.getIssuedAt() != null
                    && jwt.getExpiresAt() != null
                    && !jwt.getAudience().isEmpty();
        } catch (RuntimeException invalid) {
            return false;
        }
    }
}
