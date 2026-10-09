package com.shop.tire.security;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SessionApiDocumentation {
    @Bean
    OpenApiCustomizer logoutDocumentation() {
        // Logout is handled by Spring Security, so controller scanning cannot discover it.
        return api -> api.path("/api/auth/logout", new PathItem().post(new Operation()
                .operationId("logout")
                .addTagsItem("auth-controller")
                .summary("Wylogowanie i unieważnienie bieżącej sesji")
                .description("Wymaga nagłówka X-XSRF-TOKEN z wartością cookie XSRF-TOKEN. "
                        + "Po wylogowaniu pobierz ponownie GET /api/auth/csrf.")
                .responses(new ApiResponses()
                        .addApiResponse("204", new ApiResponse().description("Wylogowano"))
                        .addApiResponse("403", new ApiResponse().description("Brak poprawnego CSRF")))));
    }
}
