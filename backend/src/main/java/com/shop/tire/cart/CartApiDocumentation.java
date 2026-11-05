package com.shop.tire.cart;

import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.*;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

@Configuration
class CartApiDocumentation {
    @Bean
    OpenApiCustomizer cartDocumentation() {
        return api -> {
            api.getComponents().addSecuritySchemes("guestCartToken", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER).name("X-Cart-Token"));
            api.getPaths().forEach((path, item) -> {
                if (!(path.equals("/api/cart") || path.startsWith("/api/cart/") || path.startsWith("/api/guest/cart"))) return;
                item.readOperationsMap().forEach((method, operation) -> {
                    boolean create = path.equals("/api/guest/cart") && method.name().equals("POST");
                    if (!create && path.startsWith("/api/guest/cart")) {
                        operation.setSecurity(List.of(new SecurityRequirement().addList("guestCartToken")));
                    }
                    if (path.equals("/api/cart/merge")) {
                        operation.setSecurity(List.of(new SecurityRequirement().addList("bearerAuth").addList("guestCartToken")));
                        operation.setDescription("Po zalogowaniu wywołaj z JWT klienta i tokenem gościa. Konflikt pozostawia oba koszyki bez zmian. Ponowienie udanego scalenia nie dodaje sztuk drugi raz.");
                        operation.getResponses().addApiResponse("401", error("Brak poprawnego JWT"));
                        operation.getResponses().addApiResponse("403", error("Wymagana rola CUSTOMER"));
                    }
                    operation.getResponses().addApiResponse("400", error("Nieprawidłowe dane"));
                    if (!create) operation.getResponses().addApiResponse("404", error("Koszyk lub produkt jest niedostępny"));
                    if (method.name().equals("PUT") || path.endsWith("/merge")) {
                        operation.getResponses().addApiResponse("409", error("Brak dostępności lub przekroczony limit koszyka"));
                    }
                    if (method.name().equals("PUT")) operation.setDescription("Ustaw docelową ilość produktu. Ponowienie tej samej ilości nie dodaje kolejnych sztuk. Nie rezerwuje magazynu.");
                    if (create) operation.setDescription("Tworzy pusty koszyk. Token jest zwracany tylko teraz; przechowuj go w sessionStorage karty, przesyłaj w X-Cart-Token. Backend odrzuca token po 24 godzinach.");
                });
            });
        };
    }
    private ApiResponse error(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/problem+json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))));
    }
}
