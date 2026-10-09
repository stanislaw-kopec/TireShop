package com.shop.tire.account;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_action_token")
class AccountActionToken {
    enum Purpose { INVITATION, PASSWORD_RESET }

    @Id UUID id;
    UUID accountId;
    @Enumerated(EnumType.STRING) @Column(length = 30) Purpose purpose;
    @Column(length = 64) String tokenHash;
    Instant createdAt;
    Instant expiresAt;
    Instant usedAt;
    Instant revokedAt;

    protected AccountActionToken() {}

    AccountActionToken(UUID accountId, Purpose purpose, String hash, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.purpose = purpose;
        this.tokenHash = hash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }
}
