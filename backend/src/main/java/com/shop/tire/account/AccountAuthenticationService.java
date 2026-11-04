package com.shop.tire.account;

import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAuthenticationService implements UserDetailsService {
    private final AccountRepository accounts;

    public AccountAuthenticationService(AccountRepository accounts) { this.accounts = accounts; }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        var account = accounts.findByEmail(Account.normalizeEmail(username))
                .orElseThrow(() -> new UsernameNotFoundException("Account unavailable"));
        return new AccountPrincipal(account.id, account.email, account.passwordHash, account.role.name(),
                account.status == AccountStatus.ACTIVE, account.authVersion);
    }

    @Transactional(readOnly = true)
    public AccountPrincipal authenticateToken(UUID id, long authVersion, String role) {
        var account = accounts.findById(id).filter(value -> value.status == AccountStatus.ACTIVE
                && value.authVersion == authVersion && value.role.name().equals(role))
                .orElseThrow(() -> new BadCredentialsException("Account unavailable"));
        return new AccountPrincipal(account.id, account.email, "", account.role.name(), true, account.authVersion);
    }

    @Transactional
    public void logoutAll(AccountPrincipal principal) {
        var account = accounts.findLockedById(principal.accountId())
                .orElseThrow(() -> new BadCredentialsException("Account unavailable"));
        if (account.authVersion == principal.authVersion()) account.authVersion++;
    }
}
