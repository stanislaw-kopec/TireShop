package com.shop.tire.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEntry(UUID id, AuditEventType eventType, Instant occurredAt, Payload payload) {
    public record Payload(UUID orderId, UUID actorAccountId, String reason) {}
}
