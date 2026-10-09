package com.shop.tire.account;

import com.shop.tire.shared.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountActionService {
    private final AccountRepository accounts;
    private final ActionTokenRepository tokens;
    private final AccountMailSender mail;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final Duration invitationTtl;
    private final Duration resetTtl;
    private final SecureRandom random = new SecureRandom();

    public AccountActionService(AccountRepository accounts, ActionTokenRepository tokens,
            AccountMailSender mail, PasswordEncoder encoder, Clock clock,
            @Value("${app.account.invitation-ttl}") Duration invitationTtl,
            @Value("${app.account.password-reset-ttl}") Duration resetTtl) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.mail = mail;
        this.encoder = encoder;
        this.clock = clock;
        this.invitationTtl = invitationTtl;
        this.resetTtl = resetTtl;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AccountResponse invite(AccountRequests.EmployeeInvitation input) {
        var account = new Account(input.email(), null, input.firstName(), input.lastName(), Role.EMPLOYEE, clock.instant());
        account = accounts.saveAndFlush(account);
        issue(account, AccountActionToken.Purpose.INVITATION, invitationTtl);
        return AccountResponse.from(account);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void reset(UUID accountId) {
        var account = accounts.findLockedById(accountId).orElseThrow(AccountService::notFound);
        if (account.role == Role.ADMIN || account.status == AccountStatus.DISABLED) {
            throw new ApiException(HttpStatus.CONFLICT, "RESET_NOT_ALLOWED", "Nie można zresetować tego konta.");
        }
        var invitation = account.status == AccountStatus.INVITED;
        issue(account, invitation ? AccountActionToken.Purpose.INVITATION : AccountActionToken.Purpose.PASSWORD_RESET,
                invitation ? invitationTtl : resetTtl);
    }

    @Transactional
    public void complete(AccountRequests.PasswordCompletion input) {
        String hash = hash(input.token());
        UUID accountId = tokens.findAccountIdByHash(hash).orElseThrow(AccountActionService::invalidToken);
        // Lock the account first for both issuing and consuming tokens; then read current token state.
        var account = accounts.findLockedById(accountId).orElseThrow(AccountActionService::invalidToken);
        var token = tokens.findByTokenHash(hash).orElseThrow(AccountActionService::invalidToken);
        var now = clock.instant();
        if (token.usedAt != null || token.revokedAt != null || !now.isBefore(token.expiresAt)
                || account.status == AccountStatus.DISABLED
                || (token.purpose == AccountActionToken.Purpose.INVITATION && account.status != AccountStatus.INVITED)
                || (token.purpose == AccountActionToken.Purpose.PASSWORD_RESET && account.status != AccountStatus.ACTIVE)) {
            throw invalidToken();
        }
        account.passwordHash = encoder.encode(input.password());
        account.status = AccountStatus.ACTIVE;
        account.authVersion++;
        account.updatedAt = now;
        token.usedAt = now;
        tokens.flush();
        tokens.revokeUnused(accountId, now);
    }

    private void issue(Account account, AccountActionToken.Purpose purpose, Duration ttl) {
        var now = clock.instant();
        tokens.revokeUnused(account.id, now);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.saveAndFlush(new AccountActionToken(account.id, purpose, hash(raw), now, now.plus(ttl)));
        // A delivery failure rolls back issuing the token/account. No external queue in this first stage.
        mail.send(account.email, raw, purpose == AccountActionToken.Purpose.INVITATION);
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JVM", ex);
        }
    }

    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ACTION_TOKEN", "Link jest nieprawidłowy lub wygasł.");
    }
}
