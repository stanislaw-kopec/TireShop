package com.shop.tire.security;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class JwtApiDocumentation {
    @Bean
    OpenApiCustomizer bearerDocumentation() {
        return api -> {
            if (api.getComponents() == null) api.setComponents(new Components());
            api.getComponents().addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
            api.getPaths().forEach((path, item) -> {
                if (path.startsWith("/api/account/") || path.startsWith("/api/admin/") || path.equals("/api/auth/logout")) {
                    item.readOperations().forEach(operation -> {
                        operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                        operation.getResponses().addApiResponse("401", new ApiResponse().description("Brak poprawnego tokenu"));
                        operation.getResponses().addApiResponse("403", new ApiResponse().description("Brak uprawnień"));
                    });
                }
            });
            api.getPaths().get("/api/auth/logout").getPost()
                    .setDescription("Unieważnia wszystkie dotychczasowe tokeny konta. Klient usuwa również własny token z pamięci.");
        };
    }
}
