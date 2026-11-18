package com.shop.tire.catalog.controller;

import com.shop.tire.catalog.dto.TireRequests;
import com.shop.tire.catalog.dto.TireResponse;
import com.shop.tire.catalog.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/tires/{id}")
@ApiResponse(responseCode = "400", description = "Nieprawidłowe dane", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "Nie znaleziono produktu", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "409", description = "Brak wymaganej ceny lub konflikt edycji", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class AdminCatalogController {
    private final CatalogService catalog;
    AdminCatalogController(CatalogService catalog) { this.catalog = catalog; }

    @PutMapping("/price")
    @Operation(summary = "Ustal cenę brutto jednej sztuki w PLN")
    TireResponse price(@PathVariable UUID id, @Valid @RequestBody TireRequests.Price input) {
        return catalog.price(id, input);
    }

    @PutMapping("/sale")
    @Operation(summary = "Dopuść oponę do sprzedaży albo wycofaj ją z oferty")
    TireResponse sale(@PathVariable UUID id, @Valid @RequestBody TireRequests.Sale input) {
        return catalog.sale(id, input);
    }
}
