package com.shop.tire.order;

import com.shop.tire.account.AccountService;
import com.shop.tire.cart.CartService;
import com.shop.tire.catalog.CatalogService;
import com.shop.tire.catalog.Season;
import com.shop.tire.inventory.InventoryService;
import com.shop.tire.shared.error.DomainException;
import com.shop.tire.shared.security.OpaqueTokens;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderCancellationTests {
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private final UUID customer = UUID.randomUUID();
    private final UUID product = UUID.randomUUID();
    private final MemoryOrders repository = new MemoryOrders();
    private final InventoryService inventory = mock(InventoryService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final OrderService service = new OrderService(repository, mock(CartService.class),
            mock(CatalogService.class), inventory, mock(AccountService.class), Clock.fixed(NOW, ZoneOffset.UTC), events);

    @Test
    void ownerCanCancelUnpaidOrderAndReleaseStockOnce() {
        var order = order(customer, null, OrderStatus.PENDING_PAYMENT, NOW.plusSeconds(900));

        var cancelled = service.accountCancel(customer, order.id());
        var retry = service.accountCancel(customer, order.id());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.cancellationReason()).isEqualTo("CUSTOMER");
        assertThat(retry).isEqualTo(cancelled);
        assertThat(repository.get(order.id(), false).orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventory).release(Map.of(product, 2));
        verify(events).publishEvent(new OrderCancelled(order.id(), "CUSTOMER", NOW));
        verifyNoMoreInteractions(inventory, events);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "PROCESSING", "SHIPPED", "DELIVERED"})
    void paidOrFulfilledOrderCannotBeCancelled(OrderStatus status) {
        var order = order(customer, null, status, NOW.plusSeconds(900));

        assertThatThrownBy(() -> service.accountCancel(customer, order.id()))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("ORDER_NOT_CANCELLABLE"));
        assertThat(repository.get(order.id(), false)).contains(order);
        verifyNoInteractions(inventory, events);
    }

    @Test
    void anotherCustomerCannotCancelOrder() {
        var order = order(customer, null, OrderStatus.PENDING_PAYMENT, NOW.plusSeconds(900));

        assertThatThrownBy(() -> service.accountCancel(UUID.randomUUID(), order.id()))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("ORDER_NOT_FOUND"));
        assertThat(repository.get(order.id(), false)).contains(order);
        verifyNoInteractions(inventory, events);
    }

    @Test
    void guestCanCancelUsingPurchaseToken() {
        String token = OpaqueTokens.create();
        var order = order(null, token, OrderStatus.PENDING_PAYMENT, NOW.plusSeconds(900));

        assertThat(service.guestCancel(token, order.id()).status()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventory).release(Map.of(product, 2));
        verify(events).publishEvent(new OrderCancelled(order.id(), "CUSTOMER", NOW));
    }

    @Test
    void wrongGuestTokenCannotCancelOrder() {
        var order = order(null, OpaqueTokens.create(), OrderStatus.PENDING_PAYMENT, NOW.plusSeconds(900));

        assertThatThrownBy(() -> service.guestCancel(OpaqueTokens.create(), order.id()))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("ORDER_NOT_FOUND"));
        assertThat(repository.get(order.id(), false)).contains(order);
        verifyNoInteractions(inventory, events);
    }

    @Test
    void cancellationAtReservationDeadlineRecordsExpiry() {
        var order = order(customer, null, OrderStatus.PENDING_PAYMENT, NOW);

        assertThat(service.accountCancel(customer, order.id()).cancellationReason()).isEqualTo("EXPIRED");
        verify(inventory).release(Map.of(product, 2));
        verify(events).publishEvent(new OrderCancelled(order.id(), "EXPIRED", NOW));
    }

    @Test
    void expiryJobAndCancellationRetryReleaseStockOnlyOnce() {
        var order = order(customer, null, OrderStatus.PENDING_PAYMENT, NOW);

        service.expire(order.id());
        service.expire(order.id());
        assertThat(service.accountCancel(customer, order.id()).cancellationReason()).isEqualTo("EXPIRED");
        verify(inventory).release(Map.of(product, 2));
        verify(events).publishEvent(new OrderCancelled(order.id(), "EXPIRED", NOW));
        verifyNoMoreInteractions(inventory, events);
    }

    private OrderRepository.Entry order(UUID owner, String guestToken, OrderStatus status, Instant deadline) {
        var tire = new OrderSnapshot.Product(product, "Debica", "Frigo", 205, 55, 16, Season.WINTER, null);
        var snapshot = new OrderSnapshot(List.of(new OrderSnapshot.Line(tire, 2, new BigDecimal("100.00"), new BigDecimal("200.00"))),
                null, new BigDecimal("200.00"), new BigDecimal("20.00"), new BigDecimal("220.00"), "PLN");
        var order = new OrderRepository.Entry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), owner,
                guestToken == null ? null : OpaqueTokens.hash(guestToken), NOW.plusSeconds(86400), status,
                deadline.minusSeconds(900), deadline, null, snapshot, null);
        repository.entries.put(order.id(), order);
        return order;
    }

    /** Stores only the operations exercised by cancellation, without Spring or JDBC. */
    private static final class MemoryOrders extends OrderRepository {
        private final Map<UUID, Entry> entries = new HashMap<>();

        MemoryOrders() { super(null, null); }

        @Override
        Optional<Entry> get(UUID id, boolean lock) { return Optional.ofNullable(entries.get(id)); }

        @Override
        void cancel(UUID id, String reason) {
            var order = entries.get(id);
            entries.put(id, new Entry(id, order.quoteId(), order.checkoutKey(), order.accountId(), order.guestHash(),
                    order.accessExpiresAt(), OrderStatus.CANCELLED, order.createdAt(), order.expiresAt(), reason,
                    order.snapshot(), order.trackingNumber()));
        }
    }
}
