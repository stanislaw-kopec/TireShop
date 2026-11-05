package com.shop.tire.payment;

import com.shop.tire.account.AccountPrincipal;
import com.shop.tire.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/guest/orders/{orderId}/payments")
class GuestPaymentController {
    private final PaymentService payments;
    GuestPaymentController(PaymentService payments) { this.payments=payments; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<PaymentResponse> create(@RequestHeader(value="X-Purchase-Token",required=false) String token, @PathVariable UUID orderId,
                                           @Valid @RequestBody PaymentRequests.Create input) {
        var payment=PaymentHttpResult.unwrap(payments.create(null,token,orderId,input.requestKey()));
        return ResponseEntity.created(URI.create("/api/guest/orders/"+orderId+"/payments/"+payment.id())).body(payment);
    }

    @GetMapping("/{id}")
    PaymentResponse get(@RequestHeader(value="X-Purchase-Token",required=false) String token, @PathVariable UUID orderId, @PathVariable UUID id) {
        return payments.get(null,token,orderId,id);
    }

    @GetMapping
    PageResponse<PaymentResponse> list(@RequestHeader(value="X-Purchase-Token",required=false) String token, @PathVariable UUID orderId,
            @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return payments.list(null,token,orderId,page,size);
    }

    @PostMapping("/{id}/simulation-result")
    PaymentResponse result(@RequestHeader(value="X-Purchase-Token",required=false) String token, @PathVariable UUID orderId, @PathVariable UUID id,
                            @Valid @RequestBody PaymentRequests.Result input) {
        return PaymentHttpResult.unwrap(payments.result(null,token,orderId,id,input.outcome()));
    }
}

