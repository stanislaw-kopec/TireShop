package com.shop.tire.order.config;

import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.*;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

@Configuration
class TrackingApiDocumentation {
    @Bean
    OpenApiCustomizer trackingDocumentation() {
        return api -> {
            api.getComponents().addSecuritySchemes("trackingToken",new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER).name("X-Tracking-Token"));
            var read=api.getPaths().get("/api/order-tracking").getGet();
            read.setSecurity(List.of(new SecurityRequirement().addList("trackingToken")));
            read.setDescription("Tylko numer zamówienia, status i numer przesyłki. Token z fragmentu linku przesyłaj w nagłówku; ważność 90 dni.");
            read.getResponses().addApiResponse("404",error("Link nieprawidłowy lub wygasły"));
            var renew=api.getPaths().get("/api/order-tracking/link-requests").getPost();
            renew.setSecurity(List.of());
            renew.setDescription("Zawsze 202 dla poprawnego formatu danych, również po przekroczeniu limitu lub dla nieznanego zamówienia. Bez tokenu w odpowiedzi. Limit 10 żądań/15 min na adres sieciowy i 1/15 min na zamówienie. Stare linki zachowują ważność.");
            renew.getResponses().addApiResponse("400",error("Nieprawidłowe dane"));
            var confirmation=api.getPaths().get("/api/guest/orders/{id}/tracking-link").getGet();
            confirmation.setDescription("Pierwszy link po zapłacie; wymaga tokenu zakupu ważnego 24 h. Nie przedłuża 90 dni od zapłaty. Cache-Control: no-store.");
            confirmation.getResponses().addApiResponse("409",error("Zamówienie nieopłacone"));
        };
    }
    private ApiResponse error(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/problem+json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))));
    }
}
