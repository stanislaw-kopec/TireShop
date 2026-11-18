package com.shop.tire.order.dto;

import com.shop.tire.account.dto.AccountRequests;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class OrderRequests {
    private OrderRequests() {}
    public record Preview(@Email @Size(max=254) String email, @NotBlank @Size(max=30) String phone,
                          @Valid AccountRequests.AddressUpdate address) {
        public Preview {
            email = email == null || email.isBlank() ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
            phone = phone == null ? null : phone.strip();
        }
        @Override public String toString() { return "Preview[redacted]"; }
    }
    public record Checkout(@NotNull UUID quoteId, @NotNull UUID checkoutKey) {}
}
