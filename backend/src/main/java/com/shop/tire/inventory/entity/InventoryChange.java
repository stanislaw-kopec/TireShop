package com.shop.tire.inventory.entity;

import com.shop.tire.inventory.dto.MovementRequest;
import com.shop.tire.inventory.dto.MovementResponse;
import com.shop.tire.inventory.model.MovementType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "inventory_change")
@org.hibernate.annotations.Immutable
public class InventoryChange implements Persistable<UUID> {
    @Id private UUID id;
    private UUID productId;
    private UUID actorAccountId;
    private UUID orderId;
    @Enumerated(EnumType.STRING) @Column(length = 20) private MovementType type;
    private int quantityDelta;
    @Column(length = 500) private String reason;
    private int onHandAfter;
    private int reservedAfter;
    private Instant createdAt;

    protected InventoryChange() {}
    @Override public UUID getId() { return id; }
    // Entries are insert-only, including when the operation ID comes from a retried request.
    @Override @Transient public boolean isNew() { return true; }
    public InventoryChange(Inventory inventory, MovementRequest request, UUID actor, Instant now) {
        id = request.operationId();
        productId = inventory.productId();
        actorAccountId = actor;
        type = request.type();
        quantityDelta = request.quantityDelta();
        reason = request.reason();
        onHandAfter = inventory.onHand();
        reservedAfter = inventory.reserved();
        createdAt = now;
    }

    public boolean matches(UUID product, MovementRequest request, UUID actor) {
        return productId.equals(product) && java.util.Objects.equals(actorAccountId,actor) && type == request.type()
                && quantityDelta == request.quantityDelta() && reason.equals(request.reason());
    }

    public static InventoryChange sale(Inventory stock, UUID order, int quantity, Instant now) {
        var change = new InventoryChange(stock,new MovementRequest(UUID.randomUUID(),MovementType.SALE,-quantity,"Wydanie po symulowanej płatności"),null,now);
        change.orderId = order;
        return change;
    }

    public MovementResponse response() {
        return new MovementResponse(id, productId, actorAccountId, orderId, type, quantityDelta, reason,
                onHandAfter, reservedAfter, createdAt);
    }
}
