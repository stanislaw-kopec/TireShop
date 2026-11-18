package com.shop.tire.account.entity;

import com.shop.tire.account.model.AccountStatus;
import com.shop.tire.account.model.Role;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "app_account")
public class Account {
    @Id private UUID id;
    @Column(nullable = false, length = 254) private String email;
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(length = 20) private Role role;
    @Enumerated(EnumType.STRING) @Column(length = 20) private AccountStatus status;
    @Column(length = 100) private String firstName;
    @Column(length = 100) private String lastName;
    @Column(length = 30) private String phone;
    private long authVersion;
    @Version private long version;
    private Instant createdAt;
    private Instant updatedAt;

    protected Account() {}

    public Account(String email, String hash, String firstName, String lastName, Role role, Instant now) {
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

    public static String normalizeEmail(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    public void updateProfile(String firstName, String lastName, String phone, Instant now) {
        this.firstName = firstName.strip();
        this.lastName = lastName.strip();
        this.phone = phone == null ? null : phone.strip();
        this.updatedAt = now;
    }

    public void completePasswordChange(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        status = AccountStatus.ACTIVE;
        invalidateTokens();
        updatedAt = now;
    }

    public void invalidateTokens() { authVersion++; }

    public UUID id() { return id; }
    public String email() { return email; }
    public String passwordHash() { return passwordHash; }
    public Role role() { return role; }
    public AccountStatus status() { return status; }
    public String firstName() { return firstName; }
    public String lastName() { return lastName; }
    public String phone() { return phone; }
    public long authVersion() { return authVersion; }
}
