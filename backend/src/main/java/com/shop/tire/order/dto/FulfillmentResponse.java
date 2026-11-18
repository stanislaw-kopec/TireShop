package com.shop.tire.order.dto;

import com.shop.tire.order.model.OrderStatus;
import java.time.Instant;
import java.util.UUID;

public record FulfillmentResponse(UUID operationId, UUID orderId, OrderStatus status, String trackingNumber, Instant changedAt) {}
