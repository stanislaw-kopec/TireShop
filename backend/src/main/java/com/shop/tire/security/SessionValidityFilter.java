package com.shop.tire.security;

import com.shop.tire.account.AccountAuthenticationService;
import com.shop.tire.account.AccountPrincipal;
import com.shop.tire.shared.error.ApiProblems;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

class SessionValidityFilter extends OncePerRequestFilter {
    static final String LOGIN_TIME = "tireshop.loginTime";
    private final AccountAuthenticationService accounts;
    private final ApiProblems problems;
    private final Clock clock;
    private final Duration absoluteTimeout;

    SessionValidityFilter(AccountAuthenticationService accounts, ApiProblems problems,
                          Clock clock, Duration absoluteTimeout) {
        this.accounts = accounts;
        this.problems = problems;
        this.clock = clock;
        this.absoluteTimeout = absoluteTimeout;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccountPrincipal principal) {
            var session = request.getSession(false);
            var loginTime = session == null ? null : session.getAttribute(LOGIN_TIME);
            boolean expired = !(loginTime instanceof Instant started)
                    || !clock.instant().isBefore(started.plus(absoluteTimeout));
            if (expired || !accounts.isCurrent(principal)) {
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                problems.write(request, response, HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED",
                        "Sesja wygasła. Zaloguj się ponownie.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
