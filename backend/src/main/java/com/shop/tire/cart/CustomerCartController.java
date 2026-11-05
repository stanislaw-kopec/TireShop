package com.shop.tire.cart;

import com.shop.tire.account.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
class CustomerCartController {
    private final CartService carts;
    CustomerCartController(CartService carts) { this.carts = carts; }
    @GetMapping
    CartResponse get(@AuthenticationPrincipal AccountPrincipal user) { return carts.account(user.accountId()); }
    @PutMapping("/items/{productId}")
    CartResponse put(@AuthenticationPrincipal AccountPrincipal user, @PathVariable UUID productId,
                     @Valid @RequestBody CartRequests.Quantity input) { return carts.accountPut(user.accountId(), productId, input.quantity()); }
    @DeleteMapping("/items/{productId}")
    CartResponse delete(@AuthenticationPrincipal AccountPrincipal user, @PathVariable UUID productId) { return carts.accountDelete(user.accountId(), productId); }
    @PostMapping("/merge")
    CartResponse merge(@AuthenticationPrincipal AccountPrincipal user,
                       @RequestHeader(value = "X-Cart-Token", required = false) String token) { return carts.merge(user.accountId(), token); }
}
