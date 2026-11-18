package com.shop.tire.catalog.controller;

import com.shop.tire.catalog.dto.ManufacturerResponse;
import com.shop.tire.catalog.dto.TireRequests;
import com.shop.tire.catalog.dto.TireResponse;
import com.shop.tire.catalog.service.CatalogService;
import com.shop.tire.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@ApiResponse(responseCode = "400", description = "Nieprawidłowe parametry", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class PublicCatalogController {
    private final CatalogService catalog;
    PublicCatalogController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/tires")
    @Operation(summary = "Dostępne opony z ceną i zgodą na sprzedaż", description = "Strony od 0, size 1–100 (domyślnie 20). Sortowanie PRICE_ASC lub PRICE_DESC, drugi klucz: ID rosnąco.")
    PageResponse<TireResponse> list(@Valid @ModelAttribute @ParameterObject TireRequests.Filters filter) {
        return catalog.list(filter);
    }

    @GetMapping("/tires/{id}")
    @ApiResponse(responseCode = "404", description = "Brak produktu w publicznej ofercie", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    TireResponse get(@PathVariable UUID id) { return catalog.get(id); }

    @GetMapping("/manufacturers")
    @Operation(summary = "Producenci opon dostępnych w publicznej ofercie")
    PageResponse<ManufacturerResponse> manufacturers(
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return catalog.manufacturers(page, size);
    }
}
