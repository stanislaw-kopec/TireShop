package com.shop.tire.cart.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CartRepository {
    public record Cart(UUID id, Instant expiresAt, UUID mergedAccountId) {}
    private final JdbcTemplate jdbc;
    public CartRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void createGuest(UUID id, String hash, Instant now, Instant expires) {
        jdbc.update("INSERT INTO shopping_cart(id,guest_token_hash,created_at,expires_at) VALUES (?,?,?,?)",
                id, hash, Timestamp.from(now), Timestamp.from(expires));
    }
    public Optional<Cart> guest(String hash) {
        return jdbc.query("SELECT id,expires_at,merged_account_id FROM shopping_cart WHERE guest_token_hash=? FOR UPDATE",
                (rs, row) -> new Cart(rs.getObject("id", UUID.class), rs.getTimestamp("expires_at").toInstant(),
                        rs.getObject("merged_account_id", UUID.class)), hash).stream().findFirst();
    }
    public Optional<UUID> account(UUID accountId, boolean create, Instant now) {
        if (create) jdbc.update("INSERT INTO shopping_cart(id,account_id,created_at) VALUES (?,?,?) ON CONFLICT(account_id) DO NOTHING",
                UUID.randomUUID(), accountId, Timestamp.from(now));
        return jdbc.query("SELECT id FROM shopping_cart WHERE account_id=? FOR UPDATE",
                (rs, row) -> rs.getObject("id", UUID.class), accountId).stream().findFirst();
    }
    public Map<UUID, Integer> items(UUID cartId) {
        Map<UUID, Integer> result = new LinkedHashMap<>();
        jdbc.query("SELECT product_id,quantity FROM cart_item WHERE cart_id=? ORDER BY product_id", rs -> {
            result.put(rs.getObject("product_id", UUID.class), rs.getInt("quantity"));
        }, cartId);
        return result;
    }
    public void put(UUID cartId, UUID productId, int quantity) {
        jdbc.update("INSERT INTO cart_item(cart_id,product_id,quantity) VALUES (?,?,?) ON CONFLICT(cart_id,product_id) DO UPDATE SET quantity=excluded.quantity",
                cartId, productId, quantity);
    }
    public void delete(UUID cartId, UUID productId) { jdbc.update("DELETE FROM cart_item WHERE cart_id=? AND product_id=?", cartId, productId); }
    public Optional<Cart> lockForCheckout(UUID cartId) {
        return jdbc.query("SELECT id,expires_at,merged_account_id FROM shopping_cart WHERE id=? FOR UPDATE",
                (rs, row) -> new Cart(rs.getObject("id", UUID.class),
                        rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                        rs.getObject("merged_account_id", UUID.class)), cartId).stream().findFirst();
    }
    public void clear(UUID cartId) { jdbc.update("DELETE FROM cart_item WHERE cart_id=?", cartId); }
    public void merged(UUID guestId, UUID accountId) {
        jdbc.update("DELETE FROM cart_item WHERE cart_id=?", guestId);
        jdbc.update("UPDATE shopping_cart SET merged_account_id=? WHERE id=?", accountId, guestId);
    }
}
