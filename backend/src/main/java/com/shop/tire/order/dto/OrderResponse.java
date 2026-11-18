package com.shop.tire.order.dto;

import com.shop.tire.order.model.OrderSnapshot;
import com.shop.tire.order.model.OrderStatus;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(UUID id, String orderNumber, OrderStatus status, Instant createdAt,
                            Instant reservationExpiresAt, String cancellationReason, OrderSnapshot snapshot, String trackingNumber) {}
