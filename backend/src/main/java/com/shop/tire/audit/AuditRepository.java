package com.shop.tire.audit;

import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
class AuditRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    AuditRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void append(AuditEntry entry) {
        jdbc.update("INSERT INTO audit_log(id,event_type,occurred_at,payload) VALUES (?,?,?,?::jsonb)",
                entry.id(), entry.eventType().name(), Timestamp.from(entry.occurredAt()), json.writeValueAsString(entry.payload()));
    }

    List<AuditEntry> list(AuditEventType type, int page, int size) {
        String filter = type == null ? "" : " WHERE event_type=?";
        Object[] parameters = type == null ? new Object[]{size, (long) page * size}
                : new Object[]{type.name(), size, (long) page * size};
        return jdbc.query("SELECT id,event_type,occurred_at,payload FROM audit_log" + filter
                        + " ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",
                (row, index) -> new AuditEntry(row.getObject("id", java.util.UUID.class),
                        AuditEventType.valueOf(row.getString("event_type")), row.getTimestamp("occurred_at").toInstant(),
                        json.readValue(row.getString("payload"), AuditEntry.Payload.class)), parameters);
    }

    long count(AuditEventType type) {
        if (type == null) return jdbc.queryForObject("SELECT count(*) FROM audit_log", Long.class);
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE event_type=?", Long.class, type.name());
    }
}
