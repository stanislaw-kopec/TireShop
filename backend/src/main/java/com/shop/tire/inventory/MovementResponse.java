package com.shop.tire.inventory;

import java.time.Instant;
import java.util.UUID;

public record MovementResponse(UUID operationId, UUID productId, UUID actorAccountId, MovementType type,
                               int quantityDelta, String reason, int onHandAfter, int reservedAfter, Instant createdAt) {}
