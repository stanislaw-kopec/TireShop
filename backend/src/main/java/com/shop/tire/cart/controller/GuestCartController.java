package com.shop.tire.cart.controller;

import com.shop.tire.cart.dto.CartRequests;
import com.shop.tire.cart.dto.CartResponse;
import com.shop.tire.cart.service.CartService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/guest/cart")
class GuestCartController {
    private final CartService carts;
    GuestCartController(CartService carts) { this.carts = carts; }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<CartService.GuestCreated> create() {
        return ResponseEntity.created(URI.create("/api/guest/cart")).cacheControl(CacheControl.noStore()).body(carts.createGuest());
    }
    @GetMapping
    CartResponse get(@RequestHeader(value = "X-Cart-Token", required = false) String token) { return carts.guest(token); }
    @PutMapping("/items/{productId}")
    CartResponse put(@RequestHeader(value = "X-Cart-Token", required = false) String token, @PathVariable UUID productId,
                     @Valid @RequestBody CartRequests.Quantity input) { return carts.guestPut(token, productId, input.quantity()); }
    @DeleteMapping("/items/{productId}")
    CartResponse delete(@RequestHeader(value = "X-Cart-Token", required = false) String token, @PathVariable UUID productId) {
        return carts.guestDelete(token, productId);
    }
}
