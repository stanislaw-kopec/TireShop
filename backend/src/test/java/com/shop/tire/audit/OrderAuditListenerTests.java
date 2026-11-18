package com.shop.tire.audit;

import com.shop.tire.order.event.OrderCancelled;
import com.shop.tire.order.event.OrderCreated;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class OrderAuditListenerTests {
    private final AuditService audit = mock(AuditService.class);
    private final OrderAuditListener listener = new OrderAuditListener(audit);
    private final Instant now = Instant.parse("2026-11-18T12:00:00Z");

    @Test
    void recordsCreationWithOnlyOrderAndActorIds() {
        var event = new OrderCreated(UUID.randomUUID(), UUID.randomUUID(), now);
        listener.created(event);
        var entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).append(entry.capture());
        assertThat(entry.getValue().eventType()).isEqualTo(AuditEventType.ORDER_CREATED);
        assertThat(entry.getValue().occurredAt()).isEqualTo(now);
        assertThat(entry.getValue().payload()).isEqualTo(new AuditEntry.Payload(event.orderId(), event.accountId(), null));
    }

    @Test
    void recordsExpiryWithSystemActor() {
        var event = new OrderCancelled(UUID.randomUUID(), "EXPIRED", now, null);
        listener.cancelled(event);
        var entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).append(entry.capture());
        assertThat(entry.getValue().eventType()).isEqualTo(AuditEventType.ORDER_CANCELLED);
        assertThat(entry.getValue().payload()).isEqualTo(new AuditEntry.Payload(event.orderId(), null, "EXPIRED"));
    }

    @Test
    void failedAuditIsIsolatedAndLogOmitsExceptionDetails(CapturedOutput output) {
        doThrow(new DataAccessResourceFailureException("sensitive SQL payload"))
                .when(audit).append(any());
        var event = new OrderCreated(UUID.randomUUID(), null, now);

        assertThatCode(() -> listener.created(event)).doesNotThrowAnyException();
        assertThatCode(() -> listener.cancelled(new OrderCancelled(event.orderId(), "CUSTOMER", now, null)))
                .doesNotThrowAnyException();
        assertThat(output.getOut()).contains("Audit write failed", event.orderId().toString())
                .doesNotContain("sensitive SQL payload");
    }
}
