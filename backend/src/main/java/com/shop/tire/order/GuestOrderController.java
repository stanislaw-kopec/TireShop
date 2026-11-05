package com.shop.tire.order;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/guest/orders")
class GuestOrderController {
    private final OrderService orders;
    GuestOrderController(OrderService orders) { this.orders=orders; }
    @PostMapping("/preview")
    ResponseEntity<QuoteResponse> preview(@RequestHeader(value="X-Cart-Token",required=false) String token,
                                         @Valid @RequestBody OrderRequests.Preview input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(orders.guestPreview(token,input));
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<OrderResponse> create(@RequestHeader(value="X-Purchase-Token",required=false) String token,
                                         @Valid @RequestBody OrderRequests.Checkout input) {
        var order = orders.guestCheckout(token,input);
        return ResponseEntity.created(URI.create("/api/guest/orders/"+order.id())).body(order);
    }
    @GetMapping("/{id}")
    OrderResponse get(@RequestHeader(value="X-Purchase-Token",required=false) String token,@PathVariable UUID id) { return orders.guestGet(token,id); }
    @PostMapping("/{id}/cancellation")
    OrderResponse cancel(@RequestHeader(value="X-Purchase-Token",required=false) String token,@PathVariable UUID id) { return orders.guestCancel(token,id); }
}
