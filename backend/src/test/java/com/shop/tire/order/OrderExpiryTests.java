package com.shop.tire.order;

import com.shop.tire.account.AccountService;
import com.shop.tire.cart.CartService;
import com.shop.tire.catalog.*;
import com.shop.tire.inventory.InventoryService;
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
                null,null,OrderStatus.PENDING_PAYMENT,deadline.minusSeconds(900),deadline,null,snapshot)));
        service.expire(id);
        verify(inventory,times(releases)).release(Map.of(productId,1));
        verify(repository,times(releases)).cancel(id,"EXPIRED");
    }
}
