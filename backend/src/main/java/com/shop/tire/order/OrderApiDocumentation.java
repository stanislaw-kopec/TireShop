package com.shop.tire.order;

import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.*;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

@Configuration
class OrderApiDocumentation {
    @Bean
    OpenApiCustomizer orderDocumentation() {
        return api -> {
            api.getComponents().addSecuritySchemes("purchaseToken",new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER).name("X-Purchase-Token"));
            api.getPaths().forEach((path,item)-> {
                boolean guest = path.startsWith("/api/guest/orders");
                if (!guest && !path.startsWith("/api/orders")) return;
                item.readOperationsMap().forEach((method,operation)-> {
                    operation.setSecurity(List.of(new SecurityRequirement().addList(guest ? (path.endsWith("/preview") ? "guestCartToken" : "purchaseToken") : "bearerAuth")));
                    operation.getResponses().addApiResponse("400",error("Nieprawidłowe dane, UUID lub paginacja"));
                    operation.getResponses().addApiResponse("404",error("Brak dostępu do zakupu, koszyka lub adresu"));
                    if (!guest) {
                        operation.getResponses().addApiResponse("401",error("Brak prawidłowego JWT"));
                        operation.getResponses().addApiResponse("403",error("Wymagana rola CUSTOMER"));
                    }
                    if (method.name().equals("POST")) operation.getResponses().addApiResponse("409",error("Zmiana podsumowania, brak towaru, wykorzystany klucz lub niedozwolony stan"));
                    if (path.contains("/payments")) {
                        operation.setDescription(path.endsWith("/simulation-result")
                                ? "SYMULACJA: wybierz SUCCEEDED, FAILED lub CANCELLED. Wynik jest ostateczny; identyczne ponowienie jest bezpieczne. Sukces zatwierdza sprzedaż tylko przed terminem rezerwacji. Nie przesyłaj danych kart."
                                : "SYMULACJA: kwota pochodzi z zamówienia. POST z requestKey tworzy próbę (201 i Location); ten sam klucz w tym zamówieniu zwraca tę samą próbę. Maksymalnie jedna próba PENDING. GET listy: page od 0, size 1–100, kolejność createdAt malejąco i id rosnąco.");
                        if (method.name().equals("POST")) operation.getResponses().addApiResponse("409",error("Zamówienie nie pozwala na płatność, inna próba oczekuje lub wynik jest sprzeczny. Wygaśnięcie zwalnia rezerwację mimo odpowiedzi 409."));
                    }
                    else if (path.endsWith("/preview")) operation.setDescription("Zapisuje podsumowanie ważne 15 minut, bez zamówienia i bez rezerwacji. Brak adresu/e-maila dla konta używa profilu. Gość musi podać oba i otrzymuje osobny purchaseToken ważny 24 godziny.");
                    else if (path.endsWith("/cancellation")) operation.setDescription("Anuluje wyłącznie nieopłacone zamówienie. Ponowienie nie zwalnia sztuk drugi raz. Po terminie powodem jest EXPIRED.");
                    else if (method.name().equals("POST")) operation.setDescription("Potwierdza quoteId z unikalnym checkoutKey. Rezerwacja trwa 15 minut od utworzenia. Ponowienie identycznego żądania zwraca to samo zamówienie (201 i ten sam Location), bez dodatkowej rezerwacji. Przy zmianie cen/koszyka pobierz i potwierdź nowe podsumowanie.");
                });
            });
        };
    }
    private ApiResponse error(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/problem+json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))));
    }
}
