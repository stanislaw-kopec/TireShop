package com.shop.tire.security;

import com.shop.tire.account.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    private final AccountActionService actions;
    private final AuthenticationManager authentication;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contexts;
    private final Clock clock;

    public AuthController(AccountService accounts, AccountActionService actions, AuthenticationManager authentication,
            SessionAuthenticationStrategy sessionStrategy, SecurityContextRepository contexts, Clock clock) {
        this.accounts = accounts;
        this.actions = actions;
        this.authentication = authentication;
        this.sessionStrategy = sessionStrategy;
        this.contexts = contexts;
        this.clock = clock;
    }

    public record Login(@NotBlank @Email @Size(max = 254) String email,
                        @NotBlank @Size(max = 128) String password) {
        @Override public String toString() { return "Login[redacted]"; }
    }
    public record CsrfResponse(String headerName) {}

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrf) {
        csrf.getToken(); // Materialize the cookie. Angular sends its raw value in X-XSRF-TOKEN.
        return new CsrfResponse(csrf.getHeaderName());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse register(@Valid @RequestBody AccountRequests.Registration input) {
        return accounts.register(input);
    }

    @PostMapping("/login")
    public AccountResponse login(@Valid @RequestBody Login input, HttpServletRequest request, HttpServletResponse response) {
        var auth = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(input.email(), input.password()));
        sessionStrategy.onAuthentication(auth, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        request.getSession().setAttribute(SessionValidityFilter.LOGIN_TIME, clock.instant());
        contexts.saveContext(context, request, response);
        return accounts.get(((AccountPrincipal) auth.getPrincipal()).accountId());
    }

    @PostMapping("/password-actions/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completePassword(@Valid @RequestBody AccountRequests.PasswordCompletion input) { actions.complete(input); }
}
