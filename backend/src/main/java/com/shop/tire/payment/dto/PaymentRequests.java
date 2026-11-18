package com.shop.tire.payment.dto;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public final class PaymentRequests {
    private PaymentRequests() {}
    public enum Outcome { SUCCEEDED, FAILED, CANCELLED }
    public record Create(@NotNull UUID requestKey) {}
    public record Result(@NotNull Outcome outcome) {}
}
