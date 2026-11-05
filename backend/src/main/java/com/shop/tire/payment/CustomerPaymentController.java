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
@RequestMapping("/api/orders/{orderId}/payments")
class CustomerPaymentController {
    private final PaymentService payments;
    CustomerPaymentController(PaymentService payments) { this.payments=payments; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<PaymentResponse> create(@AuthenticationPrincipal AccountPrincipal user, @PathVariable UUID orderId,
                                           @Valid @RequestBody PaymentRequests.Create input) {
        var payment=PaymentHttpResult.unwrap(payments.create(user.accountId(),null,orderId,input.requestKey()));
        return ResponseEntity.created(URI.create("/api/orders/"+orderId+"/payments/"+payment.id())).body(payment);
    }

    @GetMapping("/{id}")
    PaymentResponse get(@AuthenticationPrincipal AccountPrincipal user, @PathVariable UUID orderId, @PathVariable UUID id) {
        return payments.get(user.accountId(),null,orderId,id);
    }

    @GetMapping
    PageResponse<PaymentResponse> list(@AuthenticationPrincipal AccountPrincipal user, @PathVariable UUID orderId,
            @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return payments.list(user.accountId(),null,orderId,page,size);
    }

    @PostMapping("/{id}/simulation-result")
    PaymentResponse result(@AuthenticationPrincipal AccountPrincipal user, @PathVariable UUID orderId, @PathVariable UUID id,
                            @Valid @RequestBody PaymentRequests.Result input) {
        return PaymentHttpResult.unwrap(payments.result(user.accountId(),null,orderId,id,input.outcome()));
    }
}

