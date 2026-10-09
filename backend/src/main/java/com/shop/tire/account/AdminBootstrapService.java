package com.shop.tire.account;

import jakarta.validation.Validator;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AdminBootstrapService {
    private final AccountRepository accounts;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Validator validator;
    private final Clock clock;

    AdminBootstrapService(AccountRepository accounts, JdbcTemplate jdbc, PasswordEncoder encoder, Validator validator, Clock clock) {
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional
    public void create(String email, String password) {
        jdbc.execute("SELECT pg_advisory_xact_lock(71309421)");
        if (accounts.existsByRole(Role.ADMIN)) return;
        var input = new AccountRequests.Registration(email, password, "Administrator", "TireShop");
        if (!validator.validate(input).isEmpty()) {
            throw new IllegalStateException("Bootstrap requires a valid ADMIN_EMAIL and ADMIN_PASSWORD (12-128 characters).");
        }
        if (accounts.existsByEmail(Account.normalizeEmail(email))) {
            throw new IllegalStateException("Bootstrap cannot promote an existing account.");
        }
        accounts.saveAndFlush(new Account(email, encoder.encode(password), "Administrator", "TireShop", Role.ADMIN, clock.instant()));
    }
}
