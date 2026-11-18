package com.shop.tire.payment.controller;

import com.shop.tire.payment.dto.PaymentResponse;
import com.shop.tire.payment.service.PaymentService;
import com.shop.tire.shared.error.DomainException;

final class PaymentHttpResult {
    private PaymentHttpResult() {}

    // Called by controllers after the service transaction has committed any expired reservation.
    static PaymentResponse unwrap(PaymentService.Operation result) {
        if (result.errorCode()==null) return result.payment();
        String message=switch(result.errorCode()) {
            case "PAYMENT_PENDING" -> "Zamówienie ma już oczekującą próbę płatności.";
            case "PAYMENT_RESULT_CONFLICT" -> "Wynik płatności został już ustalony i nie może być zmieniony.";
            case "PAYMENT_AMOUNT_MISMATCH" -> "Kwota lub waluta płatności nie odpowiada zamówieniu.";
            case "PAYMENT_EXPIRED" -> "Termin rezerwacji upłynął.";
            default -> "Zamówienie nie pozwala na płatność.";
        };
        throw new DomainException(DomainException.Kind.CONFLICT,result.errorCode(),message);
    }
}
