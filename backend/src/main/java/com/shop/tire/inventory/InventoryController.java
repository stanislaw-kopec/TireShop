package com.shop.tire.inventory;

import com.shop.tire.account.AccountPrincipal;
import com.shop.tire.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employee/inventory/{productId}")
@ApiResponse(responseCode = "400", description = "Nieprawidłowe dane", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "Nie znaleziono produktu", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class InventoryController {
    private final InventoryService inventory;
    InventoryController(InventoryService inventory) { this.inventory = inventory; }

    @GetMapping
    @Operation(summary = "Stan fizyczny, zarezerwowany i dostępny")
    StockResponse get(@PathVariable UUID productId) { return inventory.get(productId); }

    @PostMapping("/movements")
    @Operation(summary = "Przyjmij dostawę lub zapisz korektę", description = "operationId jest UUID generowanym dla jednej operacji. Ponowienie tych samych danych przez tego samego pracownika zwraca pierwotny wynik (200), bez kolejnej zmiany stanu.")
    @ApiResponse(responseCode = "409", description = "Korekta narusza rezerwacje, przekracza limit ilości lub operationId już wykorzystano z innymi danymi", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    MovementResponse change(@PathVariable UUID productId, @Valid @RequestBody MovementRequest input,
                            @AuthenticationPrincipal AccountPrincipal principal) {
        return inventory.change(productId, input, principal.accountId());
    }

    @GetMapping("/movements")
    @Operation(summary = "Historia zmian stanu", description = "Strony od 0, size 1–100 (domyślnie 20). Najnowsze wpisy najpierw, przy tym samym czasie ID rosnąco.")
    PageResponse<MovementResponse> history(@PathVariable UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return inventory.history(productId, page, size);
    }
}
