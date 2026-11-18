package com.shop.tire.audit;

import com.shop.tire.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-log")
@SecurityRequirement(name = "bearerAuth")
class AdminAuditController {
    private final AuditService service;

    AdminAuditController(AuditService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Dziennik zdarzeń, od najnowszych; strony od 0",
            responses = {@ApiResponse(responseCode = "200", description = "Strona dziennika"),
                    @ApiResponse(responseCode = "400", description = "Nieprawidłowy filtr lub paginacja"),
                    @ApiResponse(responseCode = "401", description = "Wymagany token Bearer"),
                    @ApiResponse(responseCode = "403", description = "Wymagana rola ADMIN")})
    PageResponse<AuditEntry> list(@RequestParam(required = false) AuditEventType eventType,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(eventType, page, size);
    }
}
