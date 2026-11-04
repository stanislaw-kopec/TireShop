package com.shop.tire.inventory;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "inventory_change")
@org.hibernate.annotations.Immutable
class InventoryChange implements Persistable<UUID> {
    @Id UUID id;
    UUID productId;
    UUID actorAccountId;
    @Enumerated(EnumType.STRING) @Column(length = 20) MovementType type;
    int quantityDelta;
    @Column(length = 500) String reason;
    int onHandAfter;
    int reservedAfter;
    Instant createdAt;

    protected InventoryChange() {}
    @Override public UUID getId() { return id; }
    // Entries are insert-only, including when the operation ID comes from a retried request.
    @Override @Transient public boolean isNew() { return true; }
    InventoryChange(Inventory inventory, MovementRequest request, UUID actor, Instant now) {
        id = request.operationId();
        productId = inventory.productId;
        actorAccountId = actor;
        type = request.type();
        quantityDelta = request.quantityDelta();
        reason = request.reason();
        onHandAfter = inventory.onHand;
        reservedAfter = inventory.reserved;
        createdAt = now;
    }

    boolean matches(UUID product, MovementRequest request, UUID actor) {
        return productId.equals(product) && actorAccountId.equals(actor) && type == request.type()
                && quantityDelta == request.quantityDelta() && reason.equals(request.reason());
    }

    MovementResponse response() {
        return new MovementResponse(id, productId, actorAccountId, type, quantityDelta, reason,
                onHandAfter, reservedAfter, createdAt);
    }
}
