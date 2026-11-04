package com.shop.tire.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
class JwtConfiguration {
    @Bean
    SecretKey jwtSigningKey(JwtProperties properties) { return properties.signingKey(); }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, JwtProperties properties, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
                new JwtIssuerValidator(properties.issuer()), jwt -> validateClaims(jwt, properties, clock)));
        return decoder;
    }

    private OAuth2TokenValidatorResult validateClaims(Jwt jwt, JwtProperties properties, Clock clock) {
        try {
            UUID.fromString(jwt.getSubject());
            UUID.fromString(jwt.getId());
            var issued = jwt.getIssuedAt();
            var expires = jwt.getExpiresAt();
            Object version = jwt.getClaims().get("authVersion");
            boolean validVersion = (version instanceof Long || version instanceof Integer)
                    && ((Number) version).longValue() >= 0;
            if (issued != null && expires != null && issued.isBefore(expires)
                    && !issued.isAfter(clock.instant()) && clock.instant().isBefore(expires)
                    && Duration.between(issued, expires).compareTo(properties.accessTokenTtl()) <= 0
                    && jwt.getAudience() != null && jwt.getAudience().contains(properties.audience())
                    && jwt.getClaimAsString("role") != null && validVersion) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (IllegalArgumentException | NullPointerException ex) {
            // Malformed claim values are authentication failures, never server errors.
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid token claims", null));
    }
}
