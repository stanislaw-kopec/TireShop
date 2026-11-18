package com.shop.tire.inventory.entity;

import com.shop.tire.inventory.dto.StockResponse;
import com.shop.tire.shared.error.DomainException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

import static com.shop.tire.shared.error.DomainException.Kind.CONFLICT;

@Entity
@Table(name = "inventory")
public class Inventory {
    @Id private UUID productId;
    private int onHand;
    private int reserved;
    @Version private long version;
    private Instant updatedAt;

    protected Inventory() {}
    public Inventory(UUID productId, Instant now) { this.productId = productId; updatedAt = now; }

    public void adjust(int delta, Instant now) {
        long next = (long) onHand + delta;
        if (next < reserved) {
            throw new DomainException(CONFLICT, "INSUFFICIENT_STOCK",
                    "Korekta nie może naruszyć rezerwacji ani zmniejszyć stanu poniżej zera.");
        }
        if (next > Integer.MAX_VALUE) {
            throw new DomainException(CONFLICT, "STOCK_LIMIT_EXCEEDED", "Przekroczono maksymalny stan magazynowy.");
        }
        onHand = (int) next;
        updatedAt = now;
    }

    public StockResponse response() { return new StockResponse(productId, onHand, reserved, onHand - reserved); }

    public void reserve(int quantity, Instant now) {
        if (quantity <= 0 || onHand - reserved < quantity) {
            throw new DomainException(CONFLICT, "INSUFFICIENT_STOCK", "Brak dostępnych sztuk. Odśwież koszyk.");
        }
        reserved += quantity;
        updatedAt = now;
    }

    public void release(int quantity, Instant now) {
        if (quantity <= 0 || reserved < quantity) throw new IllegalStateException("Reservation invariant violated");
        reserved -= quantity;
        updatedAt = now;
    }

    public void sellReserved(int quantity, Instant now) {
        if (quantity <= 0 || reserved < quantity) throw new IllegalStateException("Reservation invariant violated");
        onHand -= quantity;
        reserved -= quantity;
        updatedAt = now;
    }

    public UUID productId() { return productId; }
    public int onHand() { return onHand; }
    public int reserved() { return reserved; }
}
