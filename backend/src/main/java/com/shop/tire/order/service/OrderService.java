package com.shop.tire.order.service;

import com.shop.tire.account.service.AccountService;
import com.shop.tire.cart.dto.CartResponse;
import com.shop.tire.cart.service.CartService;
import com.shop.tire.catalog.service.CatalogService;
import com.shop.tire.inventory.service.InventoryService;
import com.shop.tire.order.dto.OrderRequests;
import com.shop.tire.order.dto.OrderResponse;
import com.shop.tire.order.dto.OrderSummary;
import com.shop.tire.order.dto.QuoteResponse;
import com.shop.tire.order.event.OrderCancelled;
import com.shop.tire.order.event.OrderCreated;
import com.shop.tire.order.event.OrderPaid;
import com.shop.tire.order.model.OrderSnapshot;
import com.shop.tire.order.model.OrderStatus;
import com.shop.tire.order.repository.OrderRepository;
import com.shop.tire.shared.api.PageResponse;
import com.shop.tire.shared.error.DomainException;
import com.shop.tire.shared.security.OpaqueTokens;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.shop.tire.shared.error.DomainException.Kind.*;

@Service
@Transactional
public class OrderService {
    private final OrderRepository orders;
    private final CartService carts;
    private final CatalogService catalog;
    private final InventoryService inventory;
    private final AccountService accounts;
    private final Clock clock;
    private final org.springframework.context.ApplicationEventPublisher events;
    public OrderService(OrderRepository orders, CartService carts, CatalogService catalog, InventoryService inventory, AccountService accounts, Clock clock,
                        org.springframework.context.ApplicationEventPublisher events) {
        this.orders=orders; this.carts=carts; this.catalog=catalog; this.inventory=inventory; this.accounts=accounts; this.clock=clock;
        this.events=events;
    }
    @PreAuthorize("hasRole('CUSTOMER') and #accountId == authentication.principal.accountId()")
    public QuoteResponse accountPreview(UUID accountId, OrderRequests.Preview input) {
        var address = input.address() == null ? accounts.address(accountId) : input.address();
        String email = input.email() == null ? accounts.get(accountId).email() : input.email();
        return preview(accountId,carts.accountCheckout(accountId),new OrderSnapshot.Delivery(email,input.phone(),address));
    }
    public QuoteResponse guestPreview(String token, OrderRequests.Preview input) {
        if (input.address() == null || input.email() == null) throw new DomainException(INVALID_INPUT,"DELIVERY_REQUIRED","Podaj e-mail oraz adres dostawy.");
        return preview(null,carts.guestCheckout(token),new OrderSnapshot.Delivery(input.email(),input.phone(),input.address()));
    }
    private QuoteResponse preview(UUID account, CartService.CheckoutCart cart, OrderSnapshot.Delivery delivery) {
        requireReady(cart.contents());
        Instant now = now();
        String token = account == null ? OpaqueTokens.create() : null;
        var quote = new OrderRepository.Quote(UUID.randomUUID(),cart.id(),account,token == null ? null : OpaqueTokens.hash(token),
                token == null ? null : now.plus(Duration.ofHours(24)),now.plus(Duration.ofMinutes(15)),OrderSnapshot.from(cart.contents(),delivery));
        orders.saveQuote(quote);
        return new QuoteResponse(quote.id(),quote.expiresAt(),quote.snapshot(),token,quote.accessExpiresAt());
    }
    @PreAuthorize("hasRole('CUSTOMER') and #accountId == authentication.principal.accountId()")
    public OrderResponse accountCheckout(UUID accountId, OrderRequests.Checkout input) { return checkout(accountId,null,input); }
    public OrderResponse guestCheckout(String token, OrderRequests.Checkout input) { return checkout(null,token,input); }
    private OrderResponse checkout(UUID account, String token, OrderRequests.Checkout input) {
        var quote = orders.quote(input.quoteId()).orElseThrow(OrderService::notFound);
        authorize(account,token,quote.accountId(),quote.guestHash(),quote.accessExpiresAt());
        var existing = orders.byQuote(quote.id());
        if (existing.isPresent()) {
            if (!existing.get().checkoutKey().equals(input.checkoutKey())) throw conflict("QUOTE_ALREADY_USED","Podsumowanie ma już zamówienie. Ponów z pierwotnym checkoutKey.");
            return response(existing.get());
        }
        if (orders.keyOwner(input.checkoutKey()).isPresent()) throw conflict("CHECKOUT_KEY_REUSED","Klucz został wykorzystany dla innego podsumowania.");
        if (!now().isBefore(quote.expiresAt())) throw conflict("QUOTE_EXPIRED","Podsumowanie wygasło. Pobierz nowe i potwierdź kwotę.");
        carts.checkoutContents(quote.cartId());
        catalog.lockForCheckout(quote.snapshot().quantities().keySet());
        // Re-read prices after product locks: an administrator cannot change them until commit.
        var current = carts.checkoutContents(quote.cartId());
        requireReady(current);
        var snapshot = OrderSnapshot.from(current,quote.snapshot().delivery());
        if (!snapshot.equals(quote.snapshot())) throw conflict("CHECKOUT_CHANGED","Koszyk, cena lub opis produktu zmieniły się. Pobierz i potwierdź nowe podsumowanie.");
        inventory.reserve(snapshot.quantities());
        Instant now = now();
        if (!now.isBefore(quote.expiresAt())) throw conflict("QUOTE_EXPIRED","Podsumowanie wygasło. Pobierz nowe i potwierdź kwotę.");
        var order = new OrderRepository.Entry(UUID.randomUUID(),quote.id(),input.checkoutKey(),account,quote.guestHash(),quote.accessExpiresAt(),
                OrderStatus.PENDING_PAYMENT,now,now.plus(Duration.ofMinutes(15)),null,snapshot,null);
        orders.create(order);
        carts.completeCheckout(quote.cartId());
        events.publishEvent(new OrderCreated(order.id(), account, now));
        return response(order);
    }
    @PreAuthorize("hasRole('CUSTOMER') and #accountId == authentication.principal.accountId()")
    @Transactional(readOnly=true)
    public OrderResponse accountGet(UUID accountId, UUID id) { return response(owned(accountId,null,id,false)); }
    @Transactional(readOnly=true)
    public OrderResponse guestGet(String token, UUID id) { return response(owned(null,token,id,false)); }
    @PreAuthorize("hasRole('CUSTOMER') and #accountId == authentication.principal.accountId()")
    public OrderResponse accountCancel(UUID accountId, UUID id) { return cancel(owned(accountId,null,id,true)); }
    public OrderResponse guestCancel(String token, UUID id) { return cancel(owned(null,token,id,true)); }
    private OrderResponse cancel(OrderRepository.Entry order) {
        if (order.status() == OrderStatus.CANCELLED) return response(order);
        if (order.status() != OrderStatus.PENDING_PAYMENT) throw conflict("ORDER_NOT_CANCELLABLE","Można anulować wyłącznie nieopłacone zamówienie.");
        release(order,expired(order) ? "EXPIRED" : "CUSTOMER");
        return response(orders.get(order.id(),false).orElseThrow());
    }
    private void release(OrderRepository.Entry order, String reason) {
        inventory.release(order.snapshot().quantities());
        orders.cancel(order.id(),reason);
        events.publishEvent(new OrderCancelled(order.id(),reason,now(),
                "CUSTOMER".equals(reason) ? order.accountId() : null));
    }

    public record PaymentOrder(UUID id, OrderStatus status, java.math.BigDecimal amountGross, String currency, Instant expiresAt) {}
    @PreAuthorize("#accountId == null or (hasRole('CUSTOMER') and #accountId == authentication.principal.accountId())")
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public PaymentOrder paymentOrder(UUID accountId, String token, UUID id) {
        var order = owned(accountId,token,id,true);
        return new PaymentOrder(id,order.status(),order.snapshot().totalGross(),order.snapshot().currency(),order.expiresAt());
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public boolean confirmPayment(UUID id) {
        var order = orders.get(id,true).orElseThrow(OrderService::notFound);
        if (order.status() != OrderStatus.PENDING_PAYMENT) return false;
        var paidAt = inventory.sellReserved(id,order.snapshot().quantities(),order.expiresAt());
        if (paidAt == null) { release(order,"EXPIRED"); return false; }
        orders.paid(id,paidAt);
        events.publishEvent(new OrderPaid(id,paidAt));
        return true;
    }
    public void expire(UUID id) {
        var order = orders.get(id,true);
        if (order.isPresent() && expired(order.get())) release(order.get(),"EXPIRED");
    }
    @Transactional(readOnly=true)
    public List<UUID> expiredIds() { return orders.expired(clock.instant()); }
    @PreAuthorize("hasRole('CUSTOMER') and #accountId == authentication.principal.accountId()")
    @Transactional(readOnly=true)
    public PageResponse<OrderSummary> history(UUID accountId, int page, int size) {
        return PageResponse.of(orders.history(accountId,page,size,clock.instant()),page,size,orders.count(accountId));
    }
    private OrderRepository.Entry owned(UUID account, String token, UUID id, boolean lock) {
        var order = orders.get(id,lock).orElseThrow(OrderService::notFound);
        authorize(account,token,order.accountId(),order.guestHash(),order.accessExpiresAt());
        return order;
    }
    private void authorize(UUID account, String token, UUID owner, String guestHash, Instant expires) {
        if (account != null) { if (!account.equals(owner)) throw notFound(); }
        else if (owner != null || !OpaqueTokens.valid(token) || !clock.instant().isBefore(expires) || !OpaqueTokens.hash(token).equals(guestHash)) throw notFound();
    }
    private boolean expired(OrderRepository.Entry order) { return order.status() == OrderStatus.PENDING_PAYMENT && !clock.instant().isBefore(order.expiresAt()); }
    private OrderResponse response(OrderRepository.Entry order) {
        return response(order,clock.instant());
    }
    static OrderResponse response(OrderRepository.Entry order, Instant now) {
        boolean expired=order.status()==OrderStatus.PENDING_PAYMENT && !now.isBefore(order.expiresAt());
        return new OrderResponse(order.id(),"TS-"+order.id(),expired?OrderStatus.CANCELLED:order.status(),
                order.createdAt(),order.expiresAt(),expired?"EXPIRED":order.reason(),order.snapshot(),order.trackingNumber());
    }
    private void requireReady(CartResponse cart) {
        if (cart.items().isEmpty()) throw conflict("EMPTY_CART","Koszyk jest pusty.");
        if (!cart.readyForCheckout()) throw conflict("CART_PRODUCT_UNAVAILABLE","Popraw niedostępne pozycje koszyka.");
    }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private static DomainException notFound() { return new DomainException(NOT_FOUND,"ORDER_NOT_FOUND","Zakup jest niedostępny."); }
    private static DomainException conflict(String code, String detail) { return new DomainException(CONFLICT,code,detail); }
}
