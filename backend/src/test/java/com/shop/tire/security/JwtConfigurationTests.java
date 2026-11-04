package com.shop.tire.security;

import com.shop.tire.account.AccountPrincipal;
import java.security.SecureRandom;
import java.time.*;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.*;

class JwtConfigurationTests {
    private final Instant issuedAt = Instant.parse("2026-11-04T12:00:00Z");
    private final JwtProperties properties = new JwtProperties(secret(), "tireshop", "tireshop-api", Duration.ofMinutes(15));
    private final JwtConfiguration configuration = new JwtConfiguration();

    @Test
    void expiresExactlyAtFifteenMinutesWithoutSleep() {
        var key = properties.signingKey();
        var tokens = new JwtTokenService(configuration.jwtEncoder(key), properties, at(issuedAt));
        var principal = new AccountPrincipal(UUID.randomUUID(), "test@example.test", "", "CUSTOMER", true, 0);
        String token = tokens.issue(principal).accessToken();
        var before = configuration.jwtDecoder(key, properties, at(issuedAt.plusSeconds(899)));
        var expired = configuration.jwtDecoder(key, properties, at(issuedAt.plusSeconds(900)));
        assertThat(before.decode(token).getSubject()).isEqualTo(principal.accountId().toString());
        assertThatThrownBy(() -> expired.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void changingTheSigningKeyRejectsExistingTokens() {
        String token = new JwtTokenService(configuration.jwtEncoder(properties.signingKey()), properties, at(issuedAt))
                .issue(new AccountPrincipal(UUID.randomUUID(), "test@example.test", "", "CUSTOMER", true, 0)).accessToken();
        var another = new JwtProperties(secret(), "tireshop", "tireshop-api", Duration.ofMinutes(15));
        var decoder = configuration.jwtDecoder(another.signingKey(), another, at(issuedAt));
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsMissingMalformedAndShortSecretsWithoutPrintingThem() {
        for (String invalid : new String[] {"", "not base64!", Base64.getEncoder().encodeToString(new byte[31])}) {
            var invalidProperties = new JwtProperties(invalid, "tireshop", "tireshop-api", Duration.ofMinutes(15));
            assertThatThrownBy(invalidProperties::signingKey).isInstanceOf(IllegalStateException.class)
                    .hasMessage("JWT_SECRET must be Base64 containing at least 32 random bytes.");
            assertThat(invalidProperties.toString()).isEqualTo("JwtProperties[redacted]");
        }
    }

    @Test
    void rejectsLifetimesOutsideTheAgreedLimit() {
        for (Duration duration : new Duration[] {Duration.ZERO, Duration.ofMillis(500), Duration.ofMinutes(16)}) {
            assertThatThrownBy(() -> new JwtProperties(secret(), "tireshop", "tireshop-api", duration).signingKey())
                    .isInstanceOf(IllegalStateException.class).hasMessage("JWT_ACCESS_TOKEN_TTL must be between 1s and 15m.");
        }
    }

    private Clock at(Instant time) { return Clock.fixed(time, ZoneOffset.UTC); }
    private static String secret() {
        byte[] value = new byte[32];
        new SecureRandom().nextBytes(value);
        return Base64.getEncoder().encodeToString(value);
    }
}
