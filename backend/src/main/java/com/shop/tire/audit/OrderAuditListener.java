package com.shop.tire.audit;

import com.shop.tire.order.OrderCancelled;
import com.shop.tire.order.OrderCreated;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class OrderAuditListener {
    private static final Logger log = LoggerFactory.getLogger(OrderAuditListener.class);
    private final AuditService audit;

    OrderAuditListener(AuditService audit) { this.audit = audit; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void created(OrderCreated event) {
        append(new AuditEntry(UUID.randomUUID(), AuditEventType.ORDER_CREATED, event.occurredAt(),
                new AuditEntry.Payload(event.orderId(), event.accountId(), null)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void cancelled(OrderCancelled event) {
        append(new AuditEntry(UUID.randomUUID(), AuditEventType.ORDER_CANCELLED, event.occurredAt(),
                new AuditEntry.Payload(event.orderId(), event.actorAccountId(), event.reason())));
    }

    private void append(AuditEntry entry) {
        try {
            audit.append(entry);
        } catch (RuntimeException failure) {
            // Database exception messages can contain SQL and payload values.
            log.error("Audit write failed; event {}, type {}, order {}, failure {}", entry.id(), entry.eventType(),
                    entry.payload().orderId(), failure.getClass().getSimpleName());
        }
    }
}
