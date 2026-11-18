package com.shop.tire.payment.repository;

import com.shop.tire.payment.dto.PaymentResponse;
import com.shop.tire.payment.model.PaymentStatus;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {
    private final JdbcTemplate jdbc;
    public PaymentRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private PaymentResponse map(ResultSet rs) throws SQLException {
        var finished=rs.getTimestamp("finished_at");
        return new PaymentResponse(rs.getObject("id",UUID.class),rs.getObject("order_id",UUID.class),rs.getBigDecimal("amount_gross"),rs.getString("currency"),
                PaymentStatus.valueOf(rs.getString("status")),rs.getTimestamp("created_at").toInstant(),finished==null?null:finished.toInstant(),rs.getString("reason"),true);
    }
    public Optional<PaymentResponse> byKey(UUID order, UUID key) {
        return jdbc.query("SELECT * FROM payment_attempt WHERE order_id=? AND request_key=?",(rs,row)->map(rs),order,key).stream().findFirst();
    }
    public Optional<PaymentResponse> get(UUID order, UUID id) {
        return jdbc.query("SELECT * FROM payment_attempt WHERE order_id=? AND id=?",(rs,row)->map(rs),order,id).stream().findFirst();
    }
    public boolean pending(UUID order) { return jdbc.queryForObject("SELECT count(*) FROM payment_attempt WHERE order_id=? AND status='PENDING'",Integer.class,order)>0; }
    public void create(PaymentResponse p, UUID key) {
        jdbc.update("INSERT INTO payment_attempt(id,order_id,request_key,amount_gross,currency,status,created_at) VALUES (?,?,?,?,?,'PENDING',?)",
                p.id(),p.orderId(),key,p.amountGross(),p.currency(),Timestamp.from(p.createdAt()));
    }
    public void finish(UUID id, PaymentStatus status, String reason, Instant at) {
        jdbc.update("UPDATE payment_attempt SET status=?,reason=?,finished_at=? WHERE id=? AND status='PENDING'",status.name(),reason,Timestamp.from(at),id);
    }
    public void cancelPending(UUID order, String reason, Instant at) {
        jdbc.update("UPDATE payment_attempt SET status='CANCELLED',reason=?,finished_at=? WHERE order_id=? AND status='PENDING'",reason,Timestamp.from(at),order);
    }
    public long count(UUID order) { return jdbc.queryForObject("SELECT count(*) FROM payment_attempt WHERE order_id=?",Long.class,order); }
    public List<PaymentResponse> list(UUID order, int page, int size) {
        return jdbc.query("SELECT * FROM payment_attempt WHERE order_id=? ORDER BY created_at DESC,id LIMIT ? OFFSET ?",(rs,row)->map(rs),order,size,(long)page*size);
    }
}
