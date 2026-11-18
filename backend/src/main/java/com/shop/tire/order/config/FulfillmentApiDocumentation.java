package com.shop.tire.order.config;

import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

@Configuration
class FulfillmentApiDocumentation {
    @Bean
    OpenApiCustomizer fulfillmentDocumentation() {
        return api -> api.getPaths().forEach((path,item)-> {
            if (!path.startsWith("/api/employee/orders")) return;
            item.readOperations().forEach(operation-> {
                operation.getResponses().addApiResponse("400",error("Nieprawidłowe dane, status, numer przesyłki lub paginacja"));
                operation.getResponses().addApiResponse("404",error("Nie znaleziono zamówienia"));
                if (path.endsWith("/status") || path.endsWith("/shipment"))
                    operation.getResponses().addApiResponse("409",error("Niedozwolone przejście, nieaktualny numer lub wykorzystany operationId"));
                operation.setDescription(path.endsWith("/status")
                        ? "Tylko EMPLOYEE. PAID → PROCESSING → SHIPPED → DELIVERED. Numer przesyłki wymagany wyłącznie dla SHIPPED. operationId identyfikuje żądanie; identyczne ponowienie zwraca pierwotny wynik, także po późniejszej zmianie statusu."
                        : path.endsWith("/shipment")
                        ? "Korekta wyłącznie w SHIPPED. expectedTrackingNumber to poprzednio odczytany numer. operationId zapewnia bezpieczne ponowienia."
                        : "Tylko EMPLOYEE, bez dziedziczenia przez ADMIN. Lista: opcjonalny filtr status, page od 0, size 1–100, sortowanie createdAt malejąco i id rosnąco. Szczegóły zawierają kopię danych kontaktu i dostawy.");
            });
        });
    }
    private ApiResponse error(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/problem+json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))));
    }
}
