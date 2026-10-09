package com.shop.tire.account;

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
    public boolean isCurrent(AccountPrincipal principal) {
        return accounts.findById(principal.accountId()).filter(account ->
                account.status == AccountStatus.ACTIVE && account.authVersion == principal.authVersion()).isPresent();
    }
}
