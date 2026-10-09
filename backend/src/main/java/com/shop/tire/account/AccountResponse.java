package com.shop.tire.account;

import java.util.UUID;

public record AccountResponse(UUID id, String email, String firstName, String lastName,
                              String phone, Role role, String status) {
    static AccountResponse from(Account account) {
        return new AccountResponse(account.id, account.email, account.firstName,
                account.lastName, account.phone, account.role, account.status.name());
    }
}
