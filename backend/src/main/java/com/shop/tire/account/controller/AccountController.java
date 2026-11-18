package com.shop.tire.account.controller;

import com.shop.tire.account.dto.AccountRequests;
import com.shop.tire.account.dto.AccountResponse;
import com.shop.tire.account.model.AccountPrincipal;
import com.shop.tire.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account/me")
class AccountController {
    private final AccountService accounts;
    AccountController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping
    AccountResponse me(@AuthenticationPrincipal AccountPrincipal principal) { return accounts.get(principal.accountId()); }

    @PutMapping
    AccountResponse update(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody AccountRequests.ProfileUpdate input) { return accounts.update(principal.accountId(), input); }

    @GetMapping("/address")
    AccountRequests.AddressUpdate address(@AuthenticationPrincipal AccountPrincipal principal) {
        return accounts.address(principal.accountId());
    }

    @PutMapping("/address")
    AccountRequests.AddressUpdate updateAddress(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody AccountRequests.AddressUpdate input) { return accounts.updateAddress(principal.accountId(), input); }
}
