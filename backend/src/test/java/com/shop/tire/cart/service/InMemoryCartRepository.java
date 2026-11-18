package com.shop.tire.cart.service;

import com.shop.tire.cart.repository.CartRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Per-test storage. Reads return copies, just like the JDBC repository. */
final class InMemoryCartRepository extends CartRepository {
    private final Map<String, Cart> guests = new HashMap<>();
    private final Map<UUID, UUID> accounts = new HashMap<>();
    private final Map<UUID, Map<UUID, Integer>> contents = new HashMap<>();

    InMemoryCartRepository() { super(null); }

    @Override
    public void createGuest(UUID id, String hash, Instant now, Instant expires) {
        guests.put(hash, new Cart(id, expires, null));
    }

    @Override
    public Optional<Cart> guest(String hash) { return Optional.ofNullable(guests.get(hash)); }

    @Override
    public Optional<UUID> account(UUID accountId, boolean create, Instant now) {
        if (create) accounts.computeIfAbsent(accountId, ignored -> UUID.randomUUID());
        return Optional.ofNullable(accounts.get(accountId));
    }

    @Override
    public Map<UUID, Integer> items(UUID cartId) {
        return new LinkedHashMap<>(contents.getOrDefault(cartId, Map.of()));
    }

    @Override
    public void put(UUID cartId, UUID productId, int quantity) {
        contents.computeIfAbsent(cartId, ignored -> new LinkedHashMap<>()).put(productId, quantity);
    }

    @Override
    public void delete(UUID cartId, UUID productId) {
        var items = contents.get(cartId);
        if (items != null) items.remove(productId);
    }

    @Override
    public void merged(UUID guestId, UUID accountId) {
        contents.remove(guestId);
        guests.replaceAll((hash, cart) -> cart.id().equals(guestId)
                ? new Cart(guestId, cart.expiresAt(), accountId) : cart);
    }
}
