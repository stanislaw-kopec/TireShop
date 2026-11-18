package com.shop.tire.security;

import com.shop.tire.account.service.AccountAuthenticationService;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

@Component
class JwtAccountConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final AccountAuthenticationService accounts;

    JwtAccountConverter(AccountAuthenticationService accounts) { this.accounts = accounts; }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            var principal = accounts.authenticateToken(UUID.fromString(jwt.getSubject()),
                    ((Number) jwt.getClaims().get("authVersion")).longValue(), jwt.getClaimAsString("role"));
            return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        } catch (AuthenticationException ex) {
            throw new InvalidBearerTokenException("Account access is no longer valid");
        }
    }
}
