package com.shop.tire.order;

import java.time.Instant;
import java.util.UUID;

public record OrderCreated(UUID orderId, UUID accountId, Instant occurredAt) {}
