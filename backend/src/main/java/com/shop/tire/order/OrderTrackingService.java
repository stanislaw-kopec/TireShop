package com.shop.tire.order;

import com.shop.tire.shared.error.DomainException;
import com.shop.tire.shared.security.OpaqueTokens;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;
import static com.shop.tire.shared.error.DomainException.Kind.*;

@Service
@Transactional
public class OrderTrackingService {
    record MailContent(String email, String orderNumber, String link, Instant expiresAt) {
        @Override public String toString() { return "MailContent[redacted]"; }
    }
    private final OrderRepository orders;
    private final OrderService access;
    private final TrackingRepository links;
    private final OrderMailOutbox outbox;
    private final TrackingSecrets secrets;
    private final TrackingProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    OrderTrackingService(OrderRepository orders, OrderService access, TrackingRepository links, OrderMailOutbox outbox,
                         TrackingSecrets secrets, TrackingProperties properties, ObjectMapper json, Clock clock) {
        this.orders=orders; this.access=access; this.links=links; this.outbox=outbox;
        this.secrets=secrets; this.properties=properties; this.json=json; this.clock=clock;
    }

    @EventListener
    @Transactional(propagation=Propagation.MANDATORY)
    public void paid(OrderPaid event) {
        // The publisher already holds the order lock. The link and mail job commit with the sale.
        var order=orders.get(event.orderId(),true).orElseThrow();
        if (order.accountId()!=null || links.initial(order.id()).isPresent()) return;
        issue(order,true,event.paidAt(),event.paidAt().plus(Duration.ofDays(90)));
    }

    public TrackingLinkResponse confirmation(UUID orderId, String purchaseToken) {
        var order=access.paymentOrder(null,purchaseToken,orderId);
        if (!paidStatus(order.status())) throw new DomainException(CONFLICT,"ORDER_NOT_PAID","Link jest dostępny po zapłacie.");
        var link=links.initial(orderId).orElseThrow(OrderTrackingService::notFound);
        if (link.ciphertext()==null || !clock.instant().isBefore(link.screenExpiresAt()) || !clock.instant().isBefore(link.expiresAt())) throw notFound();
        return new TrackingLinkResponse(url(secrets.decrypt(link.ciphertext())),link.expiresAt());
    }

    @Transactional(readOnly=true)
    public TrackingResponse track(String token) {
        if (!OpaqueTokens.valid(token)) throw notFound();
        return links.track(OpaqueTokens.hash(token),clock.instant()).orElseThrow(OrderTrackingService::notFound);
    }

    public void renew(TrackingRequests.Renewal input, String remoteAddress) {
        Instant now=now();
        // Same response and limits for existing, foreign, unpaid and nonexistent orders.
        if (!links.allowRequest(OpaqueTokens.hash("tracking-client:"+remoteAddress),now,10)) return;
        UUID id=UUID.fromString(input.orderNumber().substring(3));
        if (!links.allowRequest(OpaqueTokens.hash("tracking-order:"+id),now,1)) return;
        var candidate=orders.get(id,true);
        if (candidate.isEmpty()) return;
        var order=candidate.get();
        if (order.accountId()!=null || !paidStatus(order.status())
                || !order.snapshot().delivery().email().equalsIgnoreCase(input.email())) return;
        issue(order,false,now,now.plus(Duration.ofDays(90)));
    }

    private void issue(OrderRepository.Entry order, boolean initial, Instant now, Instant expires) {
        UUID id=UUID.randomUUID();
        String token=OpaqueTokens.create();
        links.insert(id,order.id(),OpaqueTokens.hash(token),initial,now,expires,
                initial?secrets.encrypt(token):null,initial?order.accessExpiresAt():null);
        var content=new MailContent(order.snapshot().delivery().email(),"TS-"+order.id(),url(token),expires);
        outbox.enqueue(id,secrets.encrypt(json.writeValueAsString(content)),now,expires);
    }
    private String url(String token) {
        return UriComponentsBuilder.fromUri(properties.linkUrl()).fragment("token="+token).build().toUriString();
    }
    private boolean paidStatus(OrderStatus status) {
        return EnumSet.of(OrderStatus.PAID,OrderStatus.PROCESSING,OrderStatus.SHIPPED,OrderStatus.DELIVERED).contains(status);
    }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private static DomainException notFound() {
        return new DomainException(NOT_FOUND,"TRACKING_NOT_FOUND","Link jest niedostępny lub wygasł.");
    }
}
