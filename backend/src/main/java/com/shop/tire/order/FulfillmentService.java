package com.shop.tire.order;

import com.shop.tire.shared.api.PageResponse;
import com.shop.tire.shared.error.DomainException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.shop.tire.shared.error.DomainException.Kind.*;

@Service
@PreAuthorize("hasRole('EMPLOYEE')")
@Transactional
public class FulfillmentService {
    private static final Map<OrderStatus,OrderStatus> NEXT=Map.of(
            OrderStatus.PAID,OrderStatus.PROCESSING,
            OrderStatus.PROCESSING,OrderStatus.SHIPPED,
            OrderStatus.SHIPPED,OrderStatus.DELIVERED);
    private final OrderRepository orders;
    private final FulfillmentRepository operations;
    private final Clock clock;
    FulfillmentService(OrderRepository orders, FulfillmentRepository operations, Clock clock) {
        this.orders=orders; this.operations=operations; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public PageResponse<OrderSummary> list(OrderStatus status, int page, int size) {
        var now=clock.instant();
        return PageResponse.of(operations.list(status,page,size,now),page,size,operations.count(status,now));
    }
    @Transactional(readOnly=true)
    public OrderResponse get(UUID id) { return OrderResponse.from(order(id,false),clock.instant()); }

    @PreAuthorize("hasRole('EMPLOYEE') and #actor == authentication.principal.accountId()")
    public FulfillmentResponse changeStatus(UUID id, UUID actor, FulfillmentRequests.StatusChange input) {
        var order=order(id,true);
        var target=OrderStatus.valueOf(input.status().name());
        var previous=retry(input.operationId(),id,actor,"STATUS",target,input.trackingNumber(),null);
        if (previous!=null) return previous;
        if ((target==OrderStatus.SHIPPED)!=(input.trackingNumber()!=null)) {
            throw new DomainException(INVALID_INPUT,"TRACKING_NUMBER_REQUIRED","Numer przesyłki podaj wyłącznie przy przejściu do SHIPPED.");
        }
        if (NEXT.get(order.status())!=target) throw conflict("ORDER_TRANSITION_NOT_ALLOWED","Niedozwolona zmiana statusu zamówienia.");
        return apply(order,actor,input.operationId(),"STATUS",target,input.trackingNumber(),null,
                target==OrderStatus.SHIPPED?input.trackingNumber():order.trackingNumber());
    }
    @PreAuthorize("hasRole('EMPLOYEE') and #actor == authentication.principal.accountId()")
    public FulfillmentResponse shipment(UUID id, UUID actor, FulfillmentRequests.Shipment input) {
        var order=order(id,true);
        var previous=retry(input.operationId(),id,actor,"SHIPMENT",OrderStatus.SHIPPED,input.trackingNumber(),input.expectedTrackingNumber());
        if (previous!=null) return previous;
        if (order.status()!=OrderStatus.SHIPPED) throw conflict("SHIPMENT_NOT_EDITABLE","Numer można poprawić wyłącznie przed doręczeniem wysłanego zamówienia.");
        if (!Objects.equals(order.trackingNumber(),input.expectedTrackingNumber())) throw conflict("SHIPMENT_CHANGED","Numer przesyłki został zmieniony. Odśwież zamówienie.");
        return apply(order,actor,input.operationId(),"SHIPMENT",order.status(),input.trackingNumber(),input.expectedTrackingNumber(),input.trackingNumber());
    }
    private FulfillmentResponse retry(UUID operation, UUID order, UUID actor, String kind, OrderStatus target, String requested, String expected) {
        var previous=operations.operation(operation);
        if (previous.isEmpty()) return null;
        if (!previous.get().matches(order,actor,kind,target,requested,expected))
            throw conflict("FULFILLMENT_OPERATION_REUSED","Identyfikator operacji został już użyty z innymi danymi.");
        return previous.get().response();
    }
    private FulfillmentResponse apply(OrderRepository.Entry order, UUID actor, UUID id, String kind, OrderStatus target,
                                      String requested, String expected, String result) {
        var operation=new FulfillmentRepository.Operation(id,order.id(),actor,kind,order.status(),target,requested,expected,result,
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        operations.apply(operation);
        return operation.response();
    }
    private OrderRepository.Entry order(UUID id, boolean lock) {
        return orders.get(id,lock).orElseThrow(()->new DomainException(NOT_FOUND,"ORDER_NOT_FOUND","Nie znaleziono zamówienia."));
    }
    private static DomainException conflict(String code, String detail) { return new DomainException(CONFLICT,code,detail); }
}
