package com.shop.tire.security;

import com.shop.tire.account.dto.AccountRequests;
import com.shop.tire.account.dto.AccountResponse;
import com.shop.tire.account.model.AccountPrincipal;
import com.shop.tire.account.service.AccountActionService;
import com.shop.tire.account.service.AccountAuthenticationService;
import com.shop.tire.account.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    private final AccountActionService actions;
    private final AccountAuthenticationService identities;
    private final AuthenticationManager authentication;
    private final JwtTokenService tokens;

    public AuthController(AccountService accounts, AccountActionService actions, AccountAuthenticationService identities,
                          AuthenticationManager authentication, JwtTokenService tokens) {
        this.accounts = accounts;
        this.actions = actions;
        this.identities = identities;
        this.authentication = authentication;
        this.tokens = tokens;
    }

    public record Login(@NotBlank @Email @Size(max = 254) String email,
                        @NotBlank @Size(max = 128) String password) {
        @Override public String toString() { return "Login[redacted]"; }
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse register(@Valid @RequestBody AccountRequests.Registration input) {
        return accounts.register(input);
    }

    @PostMapping("/login")
    public JwtTokenService.TokenResponse login(@Valid @RequestBody Login input) {
        var auth = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(input.email(), input.password()));
        return tokens.issue((AccountPrincipal) auth.getPrincipal());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AccountPrincipal principal) { identities.logoutAll(principal); }

    @PostMapping("/password-actions/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completePassword(@Valid @RequestBody AccountRequests.PasswordCompletion input) { actions.complete(input); }
}
