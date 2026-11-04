package com.shop.tire.inventory;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record MovementRequest(@NotNull UUID operationId, @NotNull MovementType type,
                              @NotNull Integer quantityDelta, @NotBlank @Size(max = 500) String reason) {
    public MovementRequest { reason = reason == null ? null : reason.strip(); }
}
