package com.shop.tire.inventory;

import com.shop.tire.shared.error.DomainException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

import static com.shop.tire.shared.error.DomainException.Kind.CONFLICT;

@Entity
@Table(name = "inventory")
class Inventory {
    @Id UUID productId;
    int onHand;
    int reserved;
    @Version long version;
    Instant updatedAt;

    protected Inventory() {}
    Inventory(UUID productId, Instant now) { this.productId = productId; updatedAt = now; }

    void adjust(int delta, Instant now) {
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

    StockResponse response() { return new StockResponse(productId, onHand, reserved, onHand - reserved); }
}
