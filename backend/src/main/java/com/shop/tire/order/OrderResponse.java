package com.shop.tire.order;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(UUID id, String orderNumber, OrderStatus status, Instant createdAt,
                            Instant reservationExpiresAt, String cancellationReason, OrderSnapshot snapshot) {}
