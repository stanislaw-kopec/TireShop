package com.shop.tire.account.controller;

import com.shop.tire.account.dto.AccountRequests;
import com.shop.tire.account.dto.AccountResponse;
import com.shop.tire.account.service.AccountActionService;
import com.shop.tire.account.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
class AdminAccountController {
    private final AccountActionService actions;
    private final AccountService accounts;

    AdminAccountController(AccountActionService actions, AccountService accounts) {
        this.actions = actions;
        this.accounts = accounts;
    }

    @GetMapping("/accounts")
    AccountResponse find(@RequestParam @NotBlank @Email @Size(max = 254) String email) {
        return accounts.findByEmail(email);
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse invite(@Valid @RequestBody AccountRequests.EmployeeInvitation input) { return actions.invite(input); }

    @PostMapping("/accounts/{accountId}/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@PathVariable UUID accountId) { actions.reset(accountId); }
}
