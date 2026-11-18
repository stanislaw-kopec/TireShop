package com.shop.tire.order.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderMailOutbox {
    public record Pending(UUID id, byte[] payload, int attempts, Instant expiresAt) {}
    private final JdbcTemplate jdbc;
    public OrderMailOutbox(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void enqueue(UUID tokenId, byte[] payload, Instant now, Instant expires) {
        jdbc.update("INSERT INTO order_email_outbox(id,payload,status,next_attempt_at,expires_at) VALUES (?,?,'PENDING',?,?)",
                tokenId,payload,Timestamp.from(now),Timestamp.from(expires));
    }
    public Optional<Pending> next(Instant now) {
        return jdbc.query("""
                SELECT * FROM order_email_outbox WHERE status='PENDING' AND next_attempt_at<=?
                ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
                """,(rs,row)->new Pending(rs.getObject("id",UUID.class),rs.getBytes("payload"),rs.getInt("attempts"),rs.getTimestamp("expires_at").toInstant()),
                Timestamp.from(now)).stream().findFirst();
    }
    public void sent(UUID id, Instant now) {
        jdbc.update("UPDATE order_email_outbox SET status='SENT',attempts=attempts+1,payload=NULL,finished_at=? WHERE id=?",Timestamp.from(now),id);
    }
    public void failed(Pending item, Instant now, boolean terminal) {
        int attempts=item.attempts()+1;
        if (terminal || attempts>=5) {
            jdbc.update("UPDATE order_email_outbox SET status='FAILED',attempts=?,payload=NULL,finished_at=? WHERE id=?",attempts,Timestamp.from(now),item.id());
        } else {
            jdbc.update("UPDATE order_email_outbox SET attempts=?,next_attempt_at=? WHERE id=?",
                    attempts,Timestamp.from(now.plusSeconds(60L << (attempts-1))),item.id());
        }
    }
}
