package com.shop.tire.order;

import jakarta.validation.constraints.*;
import java.util.Locale;

public final class TrackingRequests {
    private TrackingRequests() {}
    public record Renewal(
            @NotBlank @Pattern(regexp="TS-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String orderNumber,
            @NotBlank @Email @Size(max=254) String email) {
        public Renewal {
            orderNumber=orderNumber==null?null:orderNumber.strip();
            email=email==null?null:email.strip().toLowerCase(Locale.ROOT);
        }
        @Override public String toString() { return "Renewal[redacted]"; }
    }
}
