package com.shop.tire.account.service;

import com.shop.tire.account.entity.Account;
import com.shop.tire.account.model.AccountPrincipal;
import com.shop.tire.account.model.AccountStatus;
import com.shop.tire.account.model.Role;
import com.shop.tire.account.repository.AccountRepository;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Startup-only fixture provisioning; absent outside the explicit demo profile. */
@Service
@Profile("demo")
public class DemoAccounts {
    public record Actors(AccountPrincipal admin, AccountPrincipal employee, AccountPrincipal customer) {}
    private final AccountRepository accounts;
    private final AdminBootstrapService bootstrap;
    private final PasswordEncoder encoder;
    private final Clock clock;
    public DemoAccounts(AccountRepository accounts, AdminBootstrapService bootstrap, PasswordEncoder encoder, Clock clock) {
        this.accounts = accounts;
        this.bootstrap = bootstrap;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Actors create(String password) {
        // Reuse the existing bootstrap: never promote or reset an existing account.
        bootstrap.create("admin@demo.test", password);
        var admin = accounts.findFirstByRoleOrderByCreatedAtAscIdAsc(Role.ADMIN).orElseThrow();
        if (admin.status() != AccountStatus.ACTIVE) throw new IllegalStateException("Demo requires an active administrator.");
        return new Actors(principal(admin), create("employee@demo.test", "Pracownik", Role.EMPLOYEE, password),
                create("customer@demo.test", "Klient", Role.CUSTOMER, password));
    }

    /** Called only after the original seed marker has been verified by the demo coordinator. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Actors existing() {
        var admin = accounts.findFirstByRoleOrderByCreatedAtAscIdAsc(Role.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Demo upgrade requires an existing administrator."));
        return new Actors(active(admin, Role.ADMIN), existing("employee@demo.test", Role.EMPLOYEE),
                existing("customer@demo.test", Role.CUSTOMER));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AccountPrincipal createHistoryCustomer(String password) {
        return create("history@demo.test", "Historia", Role.CUSTOMER, password);
    }

    private AccountPrincipal existing(String email, Role role) {
        return active(accounts.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Demo upgrade requires its original accounts.")), role);
    }

    private AccountPrincipal active(Account account, Role role) {
        if (account.role() != role || account.status() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Demo upgrade requires active accounts with their original roles.");
        }
        return principal(account);
    }

    private AccountPrincipal create(String email, String firstName, Role role, String password) {
        if (accounts.existsByEmail(email)) {
            throw new IllegalStateException("Demo account email is already in use; no existing account will be changed.");
        }
        return principal(accounts.saveAndFlush(new Account(email, encoder.encode(password), firstName, "Demo", role, clock.instant())));
    }
    private AccountPrincipal principal(Account account) {
        return new AccountPrincipal(account.id(), account.email(), "", account.role().name(), true, account.authVersion());
    }
}
