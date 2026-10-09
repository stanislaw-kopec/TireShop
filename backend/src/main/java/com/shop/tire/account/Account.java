package com.shop.tire.account;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "app_account")
class Account {
    @Id UUID id;
    @Column(nullable = false, length = 254) String email;
    String passwordHash;
    @Enumerated(EnumType.STRING) @Column(length = 20) Role role;
    @Enumerated(EnumType.STRING) @Column(length = 20) AccountStatus status;
    @Column(length = 100) String firstName;
    @Column(length = 100) String lastName;
    @Column(length = 30) String phone;
    long authVersion;
    @Version long version;
    Instant createdAt;
    Instant updatedAt;

    protected Account() {}

    Account(String email, String hash, String firstName, String lastName, Role role, Instant now) {
        this.id = UUID.randomUUID();
        this.email = normalizeEmail(email);
        this.passwordHash = hash;
        this.firstName = firstName.strip();
        this.lastName = lastName.strip();
        this.role = role;
        this.status = hash == null ? AccountStatus.INVITED : AccountStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    static String normalizeEmail(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
