package com.shop.tire.order.controller;

import com.shop.tire.account.model.AccountPrincipal;
import com.shop.tire.order.dto.OrderRequests;
import com.shop.tire.order.dto.OrderResponse;
import com.shop.tire.order.dto.OrderSummary;
import com.shop.tire.order.dto.QuoteResponse;
import com.shop.tire.order.service.OrderService;
import com.shop.tire.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
class CustomerOrderController {
    private final OrderService orders;
    CustomerOrderController(OrderService orders) { this.orders=orders; }
    @PostMapping("/preview")
    QuoteResponse preview(@AuthenticationPrincipal AccountPrincipal user, @Valid @RequestBody OrderRequests.Preview input) {
        return orders.accountPreview(user.accountId(),input);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<OrderResponse> create(@AuthenticationPrincipal AccountPrincipal user, @Valid @RequestBody OrderRequests.Checkout input) {
        var order = orders.accountCheckout(user.accountId(),input);
        return ResponseEntity.created(URI.create("/api/orders/"+order.id())).body(order);
    }
    @GetMapping("/{id}")
    OrderResponse get(@AuthenticationPrincipal AccountPrincipal user,@PathVariable UUID id) { return orders.accountGet(user.accountId(),id); }
    @PostMapping("/{id}/cancellation")
    OrderResponse cancel(@AuthenticationPrincipal AccountPrincipal user,@PathVariable UUID id) { return orders.accountCancel(user.accountId(),id); }
    @GetMapping
    PageResponse<OrderSummary> history(@AuthenticationPrincipal AccountPrincipal user,
            @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return orders.history(user.accountId(),page,size); }
}
