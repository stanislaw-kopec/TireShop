package com.shop.tire.order.event;
import java.time.Instant;
import java.util.UUID;
public record OrderCancelled(UUID orderId, String reason, Instant occurredAt, UUID actorAccountId) {}
