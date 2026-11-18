package com.shop.tire.security;

import com.shop.tire.account.model.AccountPrincipal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
        @Override public String toString() { return "TokenResponse[redacted]"; }
    }

    public TokenResponse issue(AccountPrincipal principal) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer()).audience(List.of(properties.audience()))
                .subject(principal.accountId().toString()).issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl())).id(UUID.randomUUID().toString())
                .claim("role", principal.role()).claim("authVersion", principal.authVersion()).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        var token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.accessTokenTtl().toSeconds());
    }
}
