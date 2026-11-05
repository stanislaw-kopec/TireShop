package com.shop.tire.inventory;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentDeadlineTests {
    private final Instant deadline=Instant.parse("2026-11-05T12:00:00Z");

    @ParameterizedTest
    @ValueSource(longs={-1,0,1})
    void successRequiresTimeStrictlyBeforeDeadline(long offset) {
        var repository=mock(InventoryRepository.class);
        var changes=mock(InventoryChangeRepository.class);
        var stock=new Inventory(UUID.randomUUID(),deadline.minusSeconds(900));
        stock.onHand=5;
        stock.reserved=2;
        when(repository.findLockedByProductId(stock.productId)).thenReturn(Optional.of(stock));
        var service=new InventoryService(repository,changes,Clock.fixed(deadline.plusNanos(offset),ZoneOffset.UTC));
        var result=service.sellReserved(UUID.randomUUID(),Map.of(stock.productId,2),deadline);
        if (offset<0) {
            assertThat(result).isBefore(deadline);
            assertThat(stock.onHand).isEqualTo(3);
            assertThat(stock.reserved).isZero();
            verify(changes).save(any(InventoryChange.class));
        } else {
            assertThat(result).isNull();
            assertThat(stock.onHand).isEqualTo(5);
            assertThat(stock.reserved).isEqualTo(2);
            verifyNoInteractions(changes);
        }
    }

    @Test
    void deadlineIsCheckedAfterWaitingForEveryStockLock() {
        var repository=mock(InventoryRepository.class);
        var changes=mock(InventoryChangeRepository.class);
        var clock=mock(Clock.class);
        var first=new Inventory(new UUID(0,1),deadline.minusSeconds(900));
        var second=new Inventory(new UUID(0,2),deadline.minusSeconds(900));
        first.onHand=second.onHand=5;
        first.reserved=second.reserved=1;
        when(clock.instant()).thenReturn(deadline.minusSeconds(1));
        when(repository.findLockedByProductId(first.productId)).thenReturn(Optional.of(first));
        when(repository.findLockedByProductId(second.productId)).thenAnswer(call -> {
            when(clock.instant()).thenReturn(deadline);
            return Optional.of(second);
        });
        var result=new InventoryService(repository,changes,clock).sellReserved(UUID.randomUUID(),
                Map.of(first.productId,1,second.productId,1),deadline);
        assertThat(result).isNull();
        assertThat(first.onHand).isEqualTo(5);
        assertThat(first.reserved).isEqualTo(1);
        assertThat(second.onHand).isEqualTo(5);
        verifyNoInteractions(changes);
    }
}
