package com.shop.tire.account;

import java.io.Serial;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class AccountPrincipal extends User {
    @Serial private static final long serialVersionUID = 1L;
    private final UUID accountId;
    private final long authVersion;
    private final String role;

    public AccountPrincipal(UUID id, String email, String hash, String role, boolean enabled, long authVersion) {
        super(email, hash == null ? "" : hash, enabled, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        this.accountId = id;
        this.authVersion = authVersion;
        this.role = role;
    }

    public UUID accountId() { return accountId; }
    public long authVersion() { return authVersion; }
    public String role() { return role; }
}
