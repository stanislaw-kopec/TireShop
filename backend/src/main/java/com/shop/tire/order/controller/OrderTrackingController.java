package com.shop.tire.order.controller;

import com.shop.tire.order.dto.TrackingLinkResponse;
import com.shop.tire.order.dto.TrackingRequests;
import com.shop.tire.order.dto.TrackingResponse;
import com.shop.tire.order.service.OrderTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
class OrderTrackingController {
    public record Accepted(String message) {}
    private final OrderTrackingService tracking;
    OrderTrackingController(OrderTrackingService tracking) { this.tracking=tracking; }

    @GetMapping("/api/guest/orders/{id}/tracking-link")
    ResponseEntity<TrackingLinkResponse> confirmation(@PathVariable UUID id,
            @RequestHeader(value="X-Purchase-Token",required=false) String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tracking.confirmation(id,token));
    }
    @GetMapping("/api/order-tracking")
    ResponseEntity<TrackingResponse> track(@RequestHeader(value="X-Tracking-Token",required=false) String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tracking.track(token));
    }
    @PostMapping("/api/order-tracking/link-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ResponseEntity<Accepted> renew(@Valid @RequestBody TrackingRequests.Renewal input, HttpServletRequest request) {
        // Use the actual network peer, never an untrusted X-Forwarded-For supplied by the client.
        tracking.renew(input,request.getRemoteAddr());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(new Accepted("Jeśli dane dotyczą opłaconego zamówienia gościa, wyślemy link na zapisany adres e-mail."));
    }
}
