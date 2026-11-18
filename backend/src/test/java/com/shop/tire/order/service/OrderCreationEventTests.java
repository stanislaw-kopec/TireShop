package com.shop.tire.order.service;

import com.shop.tire.account.service.AccountService;
import com.shop.tire.cart.dto.CartResponse;
import com.shop.tire.cart.service.CartService;
import com.shop.tire.catalog.dto.TireResponse;
import com.shop.tire.catalog.model.Season;
import com.shop.tire.catalog.service.CatalogService;
import com.shop.tire.inventory.service.InventoryService;
import com.shop.tire.order.dto.OrderRequests;
import com.shop.tire.order.event.OrderCreated;
import com.shop.tire.order.model.OrderSnapshot;
import com.shop.tire.order.repository.OrderRepository;
import com.shop.tire.shared.error.DomainException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderCreationEventTests {
    private final Instant now = Instant.parse("2026-11-18T12:00:00Z");
    private final UUID customer = UUID.randomUUID();
    private final OrderRepository orders = mock(OrderRepository.class);
    private final CartService carts = mock(CartService.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final OrderService service = new OrderService(orders, carts, mock(CatalogService.class), inventory,
            mock(AccountService.class), Clock.fixed(now, ZoneOffset.UTC), events);
    private final OrderRequests.Checkout request = new OrderRequests.Checkout(UUID.randomUUID(), UUID.randomUUID());
    private OrderRepository.Quote quote;

    @BeforeEach
    void summaryMatchesCart() {
        var tire = new TireResponse(UUID.randomUUID(), UUID.randomUUID(), "Debica", "Frigo", 205, 55, 16,
                Season.WINTER, null, new BigDecimal("100.00"), "PLN", 10, true);
        var cart = new CartResponse(List.of(new CartResponse.Item(tire, 1, new BigDecimal("100.00"), CartResponse.Issue.NONE)),
                1, new BigDecimal("100.00"), new BigDecimal("20.00"), new BigDecimal("120.00"), "PLN", true);
        quote = new OrderRepository.Quote(request.quoteId(), UUID.randomUUID(), customer, null, null,
                now.plusSeconds(900), OrderSnapshot.from(cart, null));
        when(orders.quote(quote.id())).thenReturn(Optional.of(quote));
        when(carts.checkoutContents(quote.cartId())).thenReturn(cart);
    }

    @Test
    void publishesCreationOnlyOnceWhenCheckoutIsRetried() {
        var result = service.accountCheckout(customer, request);
        var saved = ArgumentCaptor.forClass(OrderRepository.Entry.class);
        verify(orders).create(saved.capture());
        when(orders.byQuote(quote.id())).thenReturn(Optional.of(saved.getValue()));

        assertThat(service.accountCheckout(customer, request)).isEqualTo(result);
        verify(events).publishEvent(new OrderCreated(result.id(), customer, now));
        verifyNoMoreInteractions(events);
        verify(inventory).reserve(quote.snapshot().quantities());
        verify(carts).completeCheckout(quote.cartId());
    }

    @Test
    void insufficientStockDoesNotPublishCreation() {
        doThrow(new DomainException(DomainException.Kind.CONFLICT, "INSUFFICIENT_STOCK", "Unavailable"))
                .when(inventory).reserve(quote.snapshot().quantities());
        assertThatThrownBy(() -> service.accountCheckout(customer, request))
                .isInstanceOfSatisfying(DomainException.class, failure -> assertThat(failure.code()).isEqualTo("INSUFFICIENT_STOCK"));
        verifyNoInteractions(events);
        verify(orders, never()).create(any());
        verify(carts, never()).completeCheckout(any());
    }
}
