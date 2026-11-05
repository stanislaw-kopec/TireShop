package com.shop.tire.security;

import com.shop.tire.account.AccountAuthenticationService;
import com.shop.tire.shared.error.ApiProblems;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.*;

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
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") List<String> origins) {
        var configuration = new CorsConfiguration();
        if (origins.contains("*")) throw new IllegalStateException("CORS requires explicit allowed origins.");
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Cart-Token", "X-Purchase-Token", "X-Tracking-Token"));
        configuration.setAllowCredentials(false);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiProblems problems,
            JwtAccountConverter accounts,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource cors) throws Exception {
        AuthenticationEntryPoint unauthorized = (req, res, ex) -> {
            res.setHeader("WWW-Authenticate", "Bearer");
            problems.write(req, res, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Wymagany jest poprawny, niewygasły token Bearer.");
        };
        AccessDeniedHandler forbidden = (req, res, ex) -> problems.write(req, res, HttpStatus.FORBIDDEN,
                "ACCESS_DENIED", "Brak uprawnień.");
        return http
                // Only an explicit Authorization header authenticates requests; cookies are not credentials.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(config -> config.configurationSource(cors))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tires", "/api/tires/*", "/api/manufacturers").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/password-actions/complete",
                                "/api/hello", "/actuator/health", "/actuator/health/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/auth/logout").authenticated()
                        .requestMatchers("/api/guest/cart", "/api/guest/cart/**").permitAll()
                        .requestMatchers("/api/cart", "/api/cart/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/orders", "/api/orders/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/guest/orders", "/api/guest/orders/**").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/order-tracking").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/order-tracking/link-requests").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/employee/**").hasRole("EMPLOYEE")
                        .requestMatchers("/api/staff/**").hasAnyRole("EMPLOYEE", "ADMIN")
                        .requestMatchers("/api/account/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(accounts))
                        .authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .build();
    }
}
