package com.shop.tire.order.repository;

import com.shop.tire.order.dto.FulfillmentResponse;
import com.shop.tire.order.dto.OrderSummary;
import com.shop.tire.order.model.OrderStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FulfillmentRepository {
    public record Operation(UUID id, UUID orderId, UUID actor, String kind, OrderStatus from, OrderStatus to,
                     String requested, String expected, String resulting, Instant createdAt) {
        public FulfillmentResponse response() { return new FulfillmentResponse(id,orderId,to,resulting,createdAt); }
        public boolean matches(UUID order, UUID actorId, String type, OrderStatus target, String tracking, String expectedTracking) {
            return orderId.equals(order) && actor.equals(actorId) && kind.equals(type) && to==target
                    && Objects.equals(requested,tracking) && Objects.equals(expected,expectedTracking);
        }
    }
    private static final String VISIBLE="""
            WITH visible AS (
                SELECT id,created_at,total_gross,currency,tracking_number,
                CASE WHEN status='PENDING_PAYMENT' AND reservation_expires_at<=? THEN 'CANCELLED' ELSE status END AS visible_status
                FROM customer_order)
            """;
    private static final String FILTER=" WHERE (?::varchar IS NULL OR visible_status=?) ";
    private final JdbcTemplate jdbc;
    public FulfillmentRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public long count(OrderStatus status, Instant now) {
        String filter=status==null?null:status.name();
        return jdbc.queryForObject(VISIBLE+"SELECT count(*) FROM visible"+FILTER,Long.class,Timestamp.from(now),filter,filter);
    }
    public List<OrderSummary> list(OrderStatus status, int page, int size, Instant now) {
        String filter=status==null?null:status.name();
        return jdbc.query(VISIBLE+"SELECT * FROM visible"+FILTER+"ORDER BY created_at DESC,id LIMIT ? OFFSET ?",
                (rs,row)->new OrderSummary(rs.getObject("id",UUID.class),"TS-"+rs.getObject("id",UUID.class),
                        OrderStatus.valueOf(rs.getString("visible_status")),rs.getTimestamp("created_at").toInstant(),
                        rs.getBigDecimal("total_gross"),rs.getString("currency"),rs.getString("tracking_number")),
                Timestamp.from(now),filter,filter,size,(long)page*size);
    }
    public Optional<Operation> operation(UUID id) {
        return jdbc.query("SELECT * FROM order_fulfillment_operation WHERE id=?",(rs,row)->new Operation(id,
                rs.getObject("order_id",UUID.class),rs.getObject("actor_account_id",UUID.class),rs.getString("kind"),
                OrderStatus.valueOf(rs.getString("from_status")),OrderStatus.valueOf(rs.getString("to_status")),
                rs.getString("requested_tracking_number"),rs.getString("expected_tracking_number"),
                rs.getString("resulting_tracking_number"),rs.getTimestamp("created_at").toInstant()),id).stream().findFirst();
    }
    public void apply(Operation op) {
        jdbc.update("UPDATE customer_order SET status=?,tracking_number=? WHERE id=?",op.to().name(),op.resulting(),op.orderId());
        jdbc.update("""
                INSERT INTO order_fulfillment_operation(id,order_id,actor_account_id,kind,from_status,to_status,
                    requested_tracking_number,expected_tracking_number,resulting_tracking_number,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """,op.id(),op.orderId(),op.actor(),op.kind(),op.from().name(),op.to().name(),
                op.requested(),op.expected(),op.resulting(),Timestamp.from(op.createdAt()));
    }
}
