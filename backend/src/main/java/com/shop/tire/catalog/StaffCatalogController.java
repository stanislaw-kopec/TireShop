package com.shop.tire.catalog;

import com.shop.tire.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/staff/tires")
@ApiResponse(responseCode = "400", description = "Nieprawidłowe parametry", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class StaffCatalogController {
    private final CatalogService catalog;
    StaffCatalogController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping
    @Operation(summary = "Wszystkie opony dla pracownika i administratora", description = "Obejmuje także produkty bez ceny, poza sprzedażą i bez dostępnych sztuk. Filtry i paginacja jak w katalogu publicznym; brak ceny sortowany na końcu.")
    PageResponse<TireResponse> list(@Valid @ModelAttribute @ParameterObject TireRequests.Filters filter) {
        return catalog.staffList(filter);
    }

    @GetMapping("/{id}")
    @ApiResponse(responseCode = "404", description = "Nie znaleziono produktu", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    TireResponse get(@PathVariable UUID id) { return catalog.staffGet(id); }
}
