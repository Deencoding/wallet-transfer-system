package com.wallettransfer.authentication.security;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class RsaKeyProvider {
    @Bean
    KeyPair jwtKeyPair(JwtProperties properties) {
        try {
            if (!StringUtils.hasText(properties.publicKey()) || !StringUtils.hasText(properties.privateKey())) {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair generated = generator.generateKeyPair();
                validate(generated);
                return generated;
            }
            KeyFactory factory = KeyFactory.getInstance("RSA");
            KeyPair pair = new KeyPair(
                    (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(decode(properties.publicKey()))),
                    (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(decode(properties.privateKey()))));
            validate(pair);
            return pair;
        } catch (Exception exception) {
            throw new IllegalStateException("JWT RSA keys are invalid", exception);
        }
    }

    private void validate(KeyPair pair) throws Exception {
        RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
        if (publicKey.getModulus().bitLength() < 2048) {
            throw new IllegalArgumentException("RSA key must be at least 2048 bits");
        }
        byte[] challenge = "wallet-jwt-key-validation".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.security.Signature signer = java.security.Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(challenge);
        byte[] signature = signer.sign();
        java.security.Signature verifier = java.security.Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pair.getPublic());
        verifier.update(challenge);
        if (!verifier.verify(signature)) {
            throw new IllegalArgumentException("RSA public and private keys do not match");
        }
    }

    private byte[] decode(String key) {
        String normalized = key.replace("\\n", "\n")
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }
}
