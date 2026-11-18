package com.shop.tire.order.dto;

import com.shop.tire.order.model.OrderSnapshot;
import java.time.Instant;
import java.util.UUID;

public record QuoteResponse(UUID id, Instant expiresAt, OrderSnapshot snapshot, String purchaseToken, Instant accessExpiresAt) {
    @Override public String toString() { return "QuoteResponse[redacted]"; }
}
