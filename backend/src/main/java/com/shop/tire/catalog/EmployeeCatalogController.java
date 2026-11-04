package com.shop.tire.catalog;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employee/tires")
@ApiResponse(responseCode = "400", description = "Nieprawidłowe dane", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "409", description = "Wariant opony już istnieje lub wystąpił konflikt edycji", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class EmployeeCatalogController {
    private final CatalogService catalog;
    EmployeeCatalogController(CatalogService catalog) { this.catalog = catalog; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Utwórz oponę z zerowym stanem, bez ceny i poza sprzedażą")
    ResponseEntity<TireResponse> create(@Valid @RequestBody TireRequests.Details input) {
        var product = catalog.create(input);
        return ResponseEntity.created(URI.create("/api/staff/tires/" + product.id())).body(product);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Zmień parametry i zdjęcie opony")
    @ApiResponse(responseCode = "404", description = "Nie znaleziono produktu", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    TireResponse update(@PathVariable UUID id, @Valid @RequestBody TireRequests.Details input) {
        return catalog.updateDetails(id, input);
    }
}
