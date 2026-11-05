package com.shop.tire.order;

import java.time.Instant;
import java.util.UUID;

public record FulfillmentResponse(UUID operationId, UUID orderId, OrderStatus status, String trackingNumber, Instant changedAt) {}
