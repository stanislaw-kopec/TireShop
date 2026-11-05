package com.shop.tire.order;

import com.shop.tire.account.AccountPrincipal;
import com.shop.tire.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employee/orders")
class EmployeeOrderController {
    private final FulfillmentService service;
    EmployeeOrderController(FulfillmentService service) { this.service=service; }
    @GetMapping
    PageResponse<OrderSummary> list(@RequestParam(required=false) OrderStatus status,
            @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return service.list(status,page,size); }
    @GetMapping("/{id}")
    OrderResponse get(@PathVariable UUID id) { return service.get(id); }
    @PatchMapping("/{id}/status")
    FulfillmentResponse status(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal actor,
                               @Valid @RequestBody FulfillmentRequests.StatusChange input) {
        return service.changeStatus(id,actor.accountId(),input);
    }
    @PutMapping("/{id}/shipment")
    FulfillmentResponse shipment(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal actor,
                                 @Valid @RequestBody FulfillmentRequests.Shipment input) {
        return service.shipment(id,actor.accountId(),input);
    }
}
