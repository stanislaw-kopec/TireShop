package com.shop.tire.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, @NotBlank String issuer, @NotBlank String audience,
                            @NotNull Duration accessTokenTtl) {
    SecretKey signingKey() {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret == null ? "" : secret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT_SECRET must be Base64 containing at least 32 random bytes.");
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be Base64 containing at least 32 random bytes.");
        }
        if (accessTokenTtl == null || accessTokenTtl.compareTo(Duration.ofSeconds(1)) < 0
                || accessTokenTtl.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalStateException("JWT_ACCESS_TOKEN_TTL must be between 1s and 15m.");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Override public String toString() { return "JwtProperties[redacted]"; }
}
