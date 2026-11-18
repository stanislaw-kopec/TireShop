package com.shop.tire.order.repository;

import com.shop.tire.order.dto.TrackingResponse;
import com.shop.tire.order.model.OrderStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TrackingRepository {
    public record Link(UUID id, String hash, byte[] ciphertext, Instant expiresAt, Instant screenExpiresAt) {}
    private final JdbcTemplate jdbc;
    public TrackingRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public Optional<TrackingResponse> track(String hash, Instant now) {
        return jdbc.query("""
                SELECT o.id,o.status,o.tracking_number FROM order_access_token t
                JOIN customer_order o ON o.id=t.order_id
                WHERE t.token_hash=? AND t.expires_at>? AND o.account_id IS NULL AND o.paid_at IS NOT NULL
                """,(rs,row)->new TrackingResponse("TS-"+rs.getObject("id",UUID.class),
                OrderStatus.valueOf(rs.getString("status")),rs.getString("tracking_number")),hash,Timestamp.from(now)).stream().findFirst();
    }
    public Optional<Link> initial(UUID order) {
        return jdbc.query("SELECT * FROM order_access_token WHERE order_id=? AND initial",
                (rs,row)->new Link(rs.getObject("id",UUID.class),rs.getString("token_hash"),rs.getBytes("screen_ciphertext"),
                        rs.getTimestamp("expires_at").toInstant(),rs.getTimestamp("screen_expires_at").toInstant()),order).stream().findFirst();
    }
    public void insert(UUID id, UUID order, String hash, boolean initial, Instant created, Instant expires, byte[] ciphertext, Instant screenExpires) {
        jdbc.update("""
                INSERT INTO order_access_token(id,order_id,token_hash,initial,created_at,expires_at,screen_ciphertext,screen_expires_at)
                VALUES (?,?,?,?,?,?,?,?)
                """,id,order,hash,initial,Timestamp.from(created),Timestamp.from(expires),ciphertext,screenExpires==null?null:Timestamp.from(screenExpires));
    }
    public boolean allowRequest(String hash, Instant now, int limit) {
        var counts=jdbc.query("""
                INSERT INTO tracking_request_limit(key_hash,window_start,requests) VALUES (?,?,1)
                ON CONFLICT(key_hash) DO UPDATE SET
                  requests=CASE WHEN tracking_request_limit.window_start<=? THEN 1 ELSE tracking_request_limit.requests+1 END,
                  window_start=CASE WHEN tracking_request_limit.window_start<=? THEN EXCLUDED.window_start ELSE tracking_request_limit.window_start END
                WHERE tracking_request_limit.window_start<=? OR tracking_request_limit.requests<?
                RETURNING requests
                """,(rs,row)->rs.getInt(1),hash,Timestamp.from(now),Timestamp.from(now.minusSeconds(900)),
                Timestamp.from(now.minusSeconds(900)),Timestamp.from(now.minusSeconds(900)),limit);
        return !counts.isEmpty();
    }
    public void clean(Instant now) {
        jdbc.update("UPDATE order_access_token SET screen_ciphertext=NULL WHERE screen_ciphertext IS NOT NULL AND screen_expires_at<=?",Timestamp.from(now));
        jdbc.update("DELETE FROM tracking_request_limit WHERE window_start<?",Timestamp.from(now.minusSeconds(86400)));
    }
}
