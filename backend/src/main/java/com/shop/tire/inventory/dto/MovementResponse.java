package com.shop.tire.inventory.dto;

import com.shop.tire.inventory.model.MovementType;
import java.time.Instant;
import java.util.UUID;

public record MovementResponse(UUID operationId, UUID productId, UUID actorAccountId, UUID orderId, MovementType type,
                               int quantityDelta, String reason, int onHandAfter, int reservedAfter, Instant createdAt) {}
