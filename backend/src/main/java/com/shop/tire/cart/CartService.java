package com.shop.tire.cart;

import com.shop.tire.catalog.CatalogService;
import com.shop.tire.shared.error.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.shop.tire.shared.error.DomainException.Kind.*;

@Service
@Transactional
public class CartService {
    public record GuestCreated(String token, Instant expiresAt, CartResponse cart) {}
    private final CartRepository carts;
    private final CatalogService catalog;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    public CartService(CartRepository carts, CatalogService catalog, Clock clock) {
        this.carts = carts; this.catalog = catalog; this.clock = clock;
    }
    public GuestCreated createGuest() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant expires = now.plus(Duration.ofHours(24));
        carts.createGuest(UUID.randomUUID(), hash(token), now, expires);
        return new GuestCreated(token, expires, view(Map.of()));
    }
    public CartResponse guest(String token) { return view(carts.items(activeGuest(token).id())); }
    public CartResponse guestPut(String token, UUID product, int quantity) { return put(activeGuest(token).id(), product, quantity); }
    public CartResponse guestDelete(String token, UUID product) { return delete(activeGuest(token).id(), product); }

    @PreAuthorize("hasRole('CUSTOMER') and #accountId == authentication.principal.accountId()")
    public CartResponse account(UUID accountId) {
        return carts.account(accountId, false, clock.instant()).map(id -> view(carts.items(id))).orElseGet(() -> view(Map.of()));
    }
    @PreAuthorize("hasRole('CUSTOMER') and #accountId == authentication.principal.accountId()")
    public CartResponse accountPut(UUID accountId, UUID product, int quantity) {
        return put(carts.account(accountId, true, clock.instant()).orElseThrow(), product, quantity);
    }
    @PreAuthorize("hasRole('CUSTOMER') and #accountId == authentication.principal.accountId()")
    public CartResponse accountDelete(UUID accountId, UUID product) {
        return carts.account(accountId, false, clock.instant()).map(id -> delete(id, product)).orElseGet(() -> view(Map.of()));
    }
    @PreAuthorize("hasRole('CUSTOMER') and #accountId == authentication.principal.accountId()")
    public CartResponse merge(UUID accountId, String token) {
        // Consistent lock order: guest first, then account. All item writes lock their parent cart.
        var guest = guestRecord(token);
        if (guest.mergedAccountId() != null && !guest.mergedAccountId().equals(accountId)) throw notFound();
        UUID target = carts.account(accountId, true, clock.instant()).orElseThrow();
        var merged = carts.items(target);
        if (guest.mergedAccountId() != null) return view(merged);
        carts.items(guest.id()).forEach((product, quantity) -> {
            long total = (long) merged.getOrDefault(product, 0) + quantity;
            if (total > Integer.MAX_VALUE) throw conflict("CART_QUANTITY_LIMIT", "Przekroczono limit ilości.");
            merged.put(product, (int) total);
        });
        checkSize(merged);
        var result = view(merged);
        if (result.items().stream().anyMatch(item -> item.issue() != CartResponse.Issue.NONE)) {
            throw conflict("CART_MERGE_UNAVAILABLE", "Nie można scalić koszyków. Sprawdź dostępność i popraw ilości; oba koszyki pozostały bez zmian.");
        }
        merged.forEach((product, quantity) -> carts.put(target, product, quantity));
        carts.merged(guest.id(), accountId);
        return result;
    }
    private CartResponse put(UUID cart, UUID product, int quantity) {
        if (quantity <= 0) throw new DomainException(INVALID_INPUT, "INVALID_QUANTITY", "Ilość musi być dodatnia.");
        var selected = catalog.cartProducts(List.of(product));
        if (selected.isEmpty()) throw new DomainException(NOT_FOUND, "PRODUCT_NOT_FOUND", "Nie znaleziono produktu.");
        var tire = selected.getFirst();
        if (!tire.onSale() || tire.priceGross() == null || quantity > tire.availableQuantity()) {
            throw conflict("CART_PRODUCT_UNAVAILABLE", "Produkt nie jest dostępny w wybranej ilości.");
        }
        var items = carts.items(cart);
        items.put(product, quantity);
        checkSize(items);
        carts.put(cart, product, quantity);
        return view(items);
    }
    private CartResponse delete(UUID cart, UUID product) {
        carts.delete(cart, product);
        return view(carts.items(cart));
    }
    private CartResponse view(Map<UUID, Integer> items) { return CartTotals.calculate(items, catalog.cartProducts(items.keySet())); }
    private void checkSize(Map<UUID, Integer> items) {
        if (items.size() > 100) throw conflict("CART_SIZE_LIMIT", "Koszyk może zawierać maksymalnie 100 różnych produktów.");
    }
    private CartRepository.Cart activeGuest(String token) {
        var cart = guestRecord(token);
        if (cart.mergedAccountId() != null) throw notFound();
        return cart;
    }
    private CartRepository.Cart guestRecord(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw notFound();
        var cart = carts.guest(hash(token)).orElseThrow(CartService::notFound);
        if (!clock.instant().isBefore(cart.expiresAt())) throw notFound();
        return cart;
    }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
    private static DomainException notFound() { return new DomainException(NOT_FOUND, "CART_NOT_FOUND", "Koszyk jest niedostępny."); }
    private static DomainException conflict(String code, String message) { return new DomainException(CONFLICT, code, message); }
}
