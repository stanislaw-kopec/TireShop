package com.shop.tire.account.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_action_token")
public class AccountActionToken {
    public enum Purpose { INVITATION, PASSWORD_RESET }

    @Id private UUID id;
    private UUID accountId;
    @Enumerated(EnumType.STRING) @Column(length = 30) private Purpose purpose;
    @Column(length = 64) private String tokenHash;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant revokedAt;

    protected AccountActionToken() {}

    public AccountActionToken(UUID accountId, Purpose purpose, String hash, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.purpose = purpose;
        this.tokenHash = hash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public void markUsed(Instant now) { usedAt = now; }

    public Instant usedAt() { return usedAt; }
    public Instant revokedAt() { return revokedAt; }
    public Instant expiresAt() { return expiresAt; }
    public Purpose purpose() { return purpose; }
}
