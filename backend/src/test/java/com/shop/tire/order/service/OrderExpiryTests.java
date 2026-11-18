package com.shop.tire.order.service;

import com.shop.tire.account.service.AccountService;
import com.shop.tire.cart.service.CartService;
import com.shop.tire.catalog.model.Season;
import com.shop.tire.catalog.service.CatalogService;
import com.shop.tire.inventory.service.InventoryService;
import com.shop.tire.order.model.OrderSnapshot;
import com.shop.tire.order.model.OrderStatus;
import com.shop.tire.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.mockito.Mockito.*;

class OrderExpiryTests {
    @ParameterizedTest
    @CsvSource({"-1,0", "0,1", "1,1"})
    void reservationExpiresAtExactDeadline(long offsetNanos, int releases) {
        var repository = mock(OrderRepository.class);
        var inventory = mock(InventoryService.class);
        var deadline = Instant.parse("2026-11-05T12:00:00Z");
        var clock = Clock.fixed(deadline.plusNanos(offsetNanos),ZoneOffset.UTC);
        var service = new OrderService(repository,mock(CartService.class),mock(CatalogService.class),inventory,mock(AccountService.class),clock,mock(org.springframework.context.ApplicationEventPublisher.class));
        UUID id = UUID.randomUUID(); UUID productId = UUID.randomUUID();
        var product = new OrderSnapshot.Product(productId,"Brand","Model",205,55,16,Season.WINTER,null);
        var snapshot = new OrderSnapshot(List.of(new OrderSnapshot.Line(product,1,new BigDecimal("10.00"),new BigDecimal("10.00"))),
                null,new BigDecimal("10.00"),new BigDecimal("20.00"),new BigDecimal("30.00"),"PLN");
        when(repository.get(id,true)).thenReturn(Optional.of(new OrderRepository.Entry(id,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                null,null,OrderStatus.PENDING_PAYMENT,deadline.minusSeconds(900),deadline,null,snapshot,null)));
        service.expire(id);
        verify(inventory,times(releases)).release(Map.of(productId,1));
        verify(repository,times(releases)).cancel(id,"EXPIRED");
    }
}
