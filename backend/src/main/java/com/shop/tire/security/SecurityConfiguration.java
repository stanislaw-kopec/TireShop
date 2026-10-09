package com.shop.tire.security;

import com.shop.tire.account.AccountAuthenticationService;
import com.shop.tire.shared.error.ApiProblems;
import jakarta.servlet.DispatcherType;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("pbkdf2", Map.of("pbkdf2", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }

    @Bean
    AuthenticationManager authenticationManager(AccountAuthenticationService accounts, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(accounts);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    CookieCsrfTokenRepository csrfTokens(@Value("${app.security.cookie-secure}") boolean secure) {
        var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.secure(secure).sameSite("Lax").path("/"));
        return repository;
    }

    @Bean
    SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean
    SessionAuthenticationStrategy loginSessionStrategy(CookieCsrfTokenRepository csrfTokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokens)));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiProblems problems,
            CookieCsrfTokenRepository csrfTokens, SecurityContextRepository contexts,
            AccountAuthenticationService accounts, Clock clock,
            @Value("${app.security.absolute-session-timeout}") Duration absoluteTimeout) throws Exception {
        return http
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokens))
                .securityContext(context -> context.securityContextRepository(contexts))
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/csrf", "/api/auth/register", "/api/auth/login",
                                "/api/auth/password-actions/complete", "/api/hello", "/actuator/health",
                                "/actuator/health/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/account/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) -> problems.write(req, res, HttpStatus.UNAUTHORIZED,
                                "AUTHENTICATION_REQUIRED", "Zaloguj się, aby uzyskać dostęp."))
                        .accessDeniedHandler((req, res, ex) -> problems.write(req, res, HttpStatus.FORBIDDEN,
                                ex instanceof CsrfException ? "INVALID_CSRF_TOKEN" : "ACCESS_DENIED",
                                ex instanceof CsrfException ? "Brak poprawnego tokenu CSRF." : "Brak uprawnień.")))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
                .addFilterBefore(new SessionValidityFilter(accounts, problems, clock, absoluteTimeout), AuthorizationFilter.class)
                .build();
    }
}
