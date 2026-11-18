package com.shop.tire.payment.service;

import com.shop.tire.order.event.OrderCancelled;
import com.shop.tire.order.model.OrderStatus;
import com.shop.tire.order.service.OrderService;
import com.shop.tire.payment.dto.PaymentRequests;
import com.shop.tire.payment.dto.PaymentResponse;
import com.shop.tire.payment.model.PaymentStatus;
import com.shop.tire.payment.repository.PaymentRepository;
import com.shop.tire.shared.api.PageResponse;
import com.shop.tire.shared.error.DomainException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import static com.shop.tire.shared.error.DomainException.Kind.*;

@Service
@Transactional
public class PaymentService {
    // A business rejection is returned, not thrown, so expiry and cancellation can commit before HTTP 409.
    public record Operation(PaymentResponse payment, String errorCode) {}
    private final PaymentRepository payments;
    private final OrderService orders;
    private final Clock clock;
    public PaymentService(PaymentRepository payments, OrderService orders, Clock clock) { this.payments=payments; this.orders=orders; this.clock=clock; }

    public Operation create(UUID account, String token, UUID orderId, UUID requestKey) {
        var order=orders.paymentOrder(account,token,orderId);
        expire(order);
        var previous=payments.byKey(orderId,requestKey);
        if (previous.isPresent()) return ok(previous.get());
        if (!payable(order)) return rejected("ORDER_NOT_PAYABLE");
        if (payments.pending(orderId)) return rejected("PAYMENT_PENDING");
        var payment=new PaymentResponse(UUID.randomUUID(),orderId,order.amountGross(),order.currency(),PaymentStatus.PENDING,now(),null,null,true);
        payments.create(payment,requestKey);
        return ok(payment);
    }
    public PaymentResponse get(UUID account, String token, UUID orderId, UUID id) {
        orders.paymentOrder(account,token,orderId);
        return require(orderId,id);
    }
    public PageResponse<PaymentResponse> list(UUID account, String token, UUID orderId, int page, int size) {
        orders.paymentOrder(account,token,orderId);
        return PageResponse.of(payments.list(orderId,page,size),page,size,payments.count(orderId));
    }
    public Operation result(UUID account, String token, UUID orderId, UUID id, PaymentRequests.Outcome outcome) {
        // Every mutation, including the synchronous cancellation listener, holds this same order lock first.
        var order=orders.paymentOrder(account,token,orderId);
        var payment=require(orderId,id);
        var desired=PaymentStatus.valueOf(outcome.name());
        if (payment.status()!=PaymentStatus.PENDING) {
            return payment.status()==desired ? ok(payment) : rejected("PAYMENT_RESULT_CONFLICT");
        }
        if (payment.amountGross().compareTo(order.amountGross())!=0 || !payment.currency().equals(order.currency())) return rejected("PAYMENT_AMOUNT_MISMATCH");
        expire(order);
        if (!payable(order)) return rejected("ORDER_NOT_PAYABLE");
        if (desired==PaymentStatus.SUCCEEDED && !orders.confirmPayment(orderId)) return rejected("PAYMENT_EXPIRED");
        payments.finish(id,desired,"SIMULATED_"+desired.name(),now());
        return ok(require(orderId,id));
    }
    @EventListener
    @Transactional(propagation=Propagation.MANDATORY)
    public void orderCancelled(OrderCancelled event) {
        payments.cancelPending(event.orderId(),event.reason().equals("EXPIRED")?"EXPIRED":"ORDER_CANCELLED",event.occurredAt());
    }
    private void expire(OrderService.PaymentOrder order) {
        if (order.status()==OrderStatus.PENDING_PAYMENT && !clock.instant().isBefore(order.expiresAt())) orders.expire(order.id());
    }
    private boolean payable(OrderService.PaymentOrder order) { return order.status()==OrderStatus.PENDING_PAYMENT && clock.instant().isBefore(order.expiresAt()); }
    private PaymentResponse require(UUID order, UUID id) {
        return payments.get(order,id).orElseThrow(()->new DomainException(NOT_FOUND,"PAYMENT_NOT_FOUND","Nie znaleziono próby płatności."));
    }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private Operation ok(PaymentResponse payment) { return new Operation(payment,null); }
    private Operation rejected(String code) { return new Operation(null,code); }
}
