package com.shop.tire.order;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(UUID id, String orderNumber, OrderStatus status, Instant createdAt,
                            Instant reservationExpiresAt, String cancellationReason, OrderSnapshot snapshot, String trackingNumber) {
    static OrderResponse from(OrderRepository.Entry order, Instant now) {
        boolean expired=order.status()==OrderStatus.PENDING_PAYMENT && !now.isBefore(order.expiresAt());
        return new OrderResponse(order.id(),"TS-"+order.id(),expired?OrderStatus.CANCELLED:order.status(),
                order.createdAt(),order.expiresAt(),expired?"EXPIRED":order.reason(),order.snapshot(),order.trackingNumber());
    }
}
