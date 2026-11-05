package com.shop.tire.order;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
class OrderRepository {
    record Quote(UUID id, UUID cartId, UUID accountId, String guestHash, Instant accessExpiresAt,
                 Instant expiresAt, OrderSnapshot snapshot) {}
    record Entry(UUID id, UUID quoteId, UUID checkoutKey, UUID accountId, String guestHash, Instant accessExpiresAt,
                 OrderStatus status, Instant createdAt, Instant expiresAt, String reason, OrderSnapshot snapshot, String trackingNumber) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    OrderRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    private Timestamp stamp(Instant time) { return time == null ? null : Timestamp.from(time); }
    private Instant time(ResultSet rs, String name) throws SQLException { var t = rs.getTimestamp(name); return t == null ? null : t.toInstant(); }
    void saveQuote(Quote q) {
        jdbc.update("INSERT INTO checkout_quote(id,cart_id,account_id,guest_token_hash,access_expires_at,expires_at,snapshot) VALUES (?,?,?,?,?,?,?::jsonb)",
                q.id(),q.cartId(),q.accountId(),q.guestHash(),stamp(q.accessExpiresAt()),stamp(q.expiresAt()),json.writeValueAsString(q.snapshot()));
    }
    Optional<Quote> quote(UUID id) {
        return jdbc.query("SELECT * FROM checkout_quote WHERE id=? FOR UPDATE", (rs,row) -> new Quote(id,
                rs.getObject("cart_id",UUID.class),rs.getObject("account_id",UUID.class),rs.getString("guest_token_hash"),
                time(rs,"access_expires_at"),time(rs,"expires_at"),json.readValue(rs.getString("snapshot"),OrderSnapshot.class)),id).stream().findFirst();
    }
    Optional<Entry> byQuote(UUID quoteId) { return find("quote_id",quoteId,false); }
    Optional<Entry> get(UUID id, boolean lock) { return find("id",id,lock); }
    Optional<UUID> keyOwner(UUID key) {
        return jdbc.query("SELECT quote_id FROM customer_order WHERE checkout_key=?",(rs,row)->rs.getObject(1,UUID.class),key).stream().findFirst();
    }
    private Optional<Entry> find(String trustedColumn, UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM customer_order WHERE " + trustedColumn + "=?" + (lock ? " FOR UPDATE" : ""),
                (rs,row) -> entry(rs),id).stream().findFirst();
    }
    private Entry entry(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id",UUID.class);
        var lines = jdbc.query("SELECT * FROM order_item WHERE order_id=? ORDER BY product_id",(item,row)->
                new OrderSnapshot.Line(json.readValue(item.getString("product_snapshot"),OrderSnapshot.Product.class),
                        item.getInt("quantity"),item.getBigDecimal("unit_price_gross"),item.getBigDecimal("line_total_gross")),id);
        var snapshot = new OrderSnapshot(lines,json.readValue(rs.getString("delivery"),OrderSnapshot.Delivery.class),
                rs.getBigDecimal("items_total_gross"),rs.getBigDecimal("shipping_gross"),rs.getBigDecimal("total_gross"),rs.getString("currency"));
        return new Entry(id,rs.getObject("quote_id",UUID.class),rs.getObject("checkout_key",UUID.class),rs.getObject("account_id",UUID.class),
                rs.getString("guest_token_hash"),time(rs,"access_expires_at"),OrderStatus.valueOf(rs.getString("status")),
                time(rs,"created_at"),time(rs,"reservation_expires_at"),rs.getString("cancellation_reason"),snapshot,rs.getString("tracking_number"));
    }
    void create(Entry order) {
        var s = order.snapshot();
        jdbc.update("""
                INSERT INTO customer_order(id,quote_id,checkout_key,account_id,guest_token_hash,access_expires_at,status,
                    created_at,reservation_expires_at,delivery,items_total_gross,shipping_gross,total_gross,currency)
                VALUES (?,?,?,?,?,?,'PENDING_PAYMENT',?,?,?::jsonb,?,?,?,?)
                """,order.id(),order.quoteId(),order.checkoutKey(),order.accountId(),order.guestHash(),stamp(order.accessExpiresAt()),
                stamp(order.createdAt()),stamp(order.expiresAt()),json.writeValueAsString(s.delivery()),s.itemsTotalGross(),s.shippingGross(),s.totalGross(),s.currency());
        for (var line : s.items()) {
            jdbc.update("INSERT INTO order_item(order_id,product_id,product_snapshot,quantity,unit_price_gross,line_total_gross) VALUES (?,?,?::jsonb,?,?,?)",
                    order.id(),line.product().id(),json.writeValueAsString(line.product()),line.quantity(),line.unitPriceGross(),line.lineTotalGross());
        }
    }
    void cancel(UUID id, String reason) { jdbc.update("UPDATE customer_order SET status='CANCELLED',cancellation_reason=? WHERE id=?",reason,id); }
    void paid(UUID id, Instant at) { jdbc.update("UPDATE customer_order SET status='PAID',paid_at=? WHERE id=?",stamp(at),id); }
    List<UUID> expired(Instant now) {
        return jdbc.query("SELECT id FROM customer_order WHERE status='PENDING_PAYMENT' AND reservation_expires_at<=? ORDER BY reservation_expires_at,id LIMIT 100",
                (rs,row)->rs.getObject(1,UUID.class),stamp(now));
    }
    long count(UUID account) { return jdbc.queryForObject("SELECT count(*) FROM customer_order WHERE account_id=?",Long.class,account); }
    List<OrderSummary> history(UUID account, int page, int size, Instant now) {
        return jdbc.query("SELECT id,status,created_at,reservation_expires_at,total_gross,currency,tracking_number FROM customer_order WHERE account_id=? ORDER BY created_at DESC,id LIMIT ? OFFSET ?",
                (rs,row)-> {
                    var status = OrderStatus.valueOf(rs.getString("status"));
                    if (status == OrderStatus.PENDING_PAYMENT && !now.isBefore(time(rs,"reservation_expires_at"))) status = OrderStatus.CANCELLED;
                    UUID id = rs.getObject("id",UUID.class);
                    return new OrderSummary(id,"TS-" + id,status,time(rs,"created_at"),rs.getBigDecimal("total_gross"),rs.getString("currency"),rs.getString("tracking_number"));
                },account,size,(long)page*size);
    }
}
