package com.shop.tire.demo;

import com.shop.tire.account.dto.AccountRequests;
import com.shop.tire.account.model.AccountPrincipal;
import com.shop.tire.account.service.AccountService;
import com.shop.tire.account.service.DemoAccounts;
import com.shop.tire.cart.service.CartService;
import com.shop.tire.catalog.dto.TireRequests;
import com.shop.tire.catalog.model.Season;
import com.shop.tire.catalog.service.CatalogService;
import com.shop.tire.inventory.dto.MovementRequest;
import com.shop.tire.inventory.model.MovementType;
import com.shop.tire.inventory.service.InventoryService;
import com.shop.tire.order.dto.FulfillmentRequests;
import com.shop.tire.order.dto.OrderRequests;
import com.shop.tire.order.model.OrderStatus;
import com.shop.tire.order.service.FulfillmentService;
import com.shop.tire.order.service.OrderService;
import com.shop.tire.payment.dto.PaymentRequests;
import com.shop.tire.payment.dto.PaymentResponse;
import com.shop.tire.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("demo")
public class DemoSeedService {
    private final DemoSeedRepository seeds;
    private final DemoProperties properties;
    private final DemoAccounts demoAccounts;
    private final AccountService accounts;
    private final CatalogService catalog;
    private final InventoryService inventory;
    private final CartService carts;
    private final OrderService orders;
    private final PaymentService payments;
    private final FulfillmentService fulfillment;
    private final Clock clock;

    public DemoSeedService(DemoSeedRepository seeds, DemoProperties properties, DemoAccounts demoAccounts,
            AccountService accounts, CatalogService catalog, InventoryService inventory, CartService carts,
            OrderService orders, PaymentService payments, FulfillmentService fulfillment, Clock clock) {
        this.seeds = seeds;
        this.properties = properties;
        this.demoAccounts = demoAccounts;
        this.accounts = accounts;
        this.catalog = catalog;
        this.inventory = inventory;
        this.carts = carts;
        this.orders = orders;
        this.payments = payments;
        this.fulfillment = fulfillment;
        this.clock = clock;
    }

    @Transactional
    public boolean apply() {
        seeds.lock();
        if (seeds.applied("presentation-v2")) return false;
        properties.requirePassword();
        var actors = seeds.applied("presentation-v1") ? demoAccounts.existing() : initialSeed();
        historySeed(actors);
        seeds.record("presentation-v2", clock.instant());
        return true;
    }

    private DemoAccounts.Actors initialSeed() {
        var actors = demoAccounts.create(properties.password());
        UUID customer = actors.customer().accountId();
        var address = new AccountRequests.AddressUpdate("Klient Demo", "Pokazowa", "10", "2", "00-001", "Warszawa", "PL");
        accounts.updateAddress(customer, address);

        // All catalog, inventory, order and payment mutations use their normal business services.
        var products = new ArrayList<UUID>();
        String[] manufacturers = {"Dębica", "Michelin", "Continental", "Goodyear"};
        Season[] seasons = {Season.SUMMER, Season.WINTER, Season.ALL_SEASON};
        for (int i = 0; i < 15; i++) {
            int index = i;
            UUID product = as(actors.employee(), () -> {
                var details = new TireRequests.Details(manufacturers[index % 4], "Demo " + (index + 1),
                        new int[]{195, 205, 225}[index % 3], new int[]{65, 55, 45}[index % 3],
                        new int[]{15, 16, 17}[index % 3], seasons[(index / 3) % 3], null);
                UUID id = catalog.create(details).id();
                if (index != 14) inventory.change(id, new MovementRequest(UUID.randomUUID(), MovementType.RECEIPT,
                        40, "Początkowa dostawa demonstracyjna"), actors.employee().accountId());
                return id;
            });
            as(actors.admin(), () -> {
                if (index != 12) catalog.price(product, new TireRequests.Price(new BigDecimal("199.00").add(BigDecimal.valueOf(25L * index))));
                if (index != 12 && index != 13) catalog.sale(product, new TireRequests.Sale(true));
                return null;
            });
            products.add(product);
        }

        List<OrderStatus> stages = List.of(OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.SHIPPED,
                OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.PENDING_PAYMENT);
        for (int i = 0; i < stages.size(); i++) {
            UUID product = products.get(i);
            int quantity = i % 2 == 0 ? 4 : 3;
            OrderStatus stage = stages.get(i);
            UUID order = as(actors.customer(), () -> {
                carts.accountPut(customer, product, quantity);
                var quote = orders.accountPreview(customer, new OrderRequests.Preview(null, "500600700", null));
                UUID id = orders.accountCheckout(customer, new OrderRequests.Checkout(quote.id(), UUID.randomUUID())).id();
                if (stage == OrderStatus.CANCELLED) orders.accountCancel(customer, id);
                else if (stage != OrderStatus.PENDING_PAYMENT) pay(customer, null, id);
                return id;
            });
            as(actors.employee(), () -> {
                for (var target : FulfillmentRequests.Target.values()) {
                    if (stage == OrderStatus.PAID || stage == OrderStatus.CANCELLED || stage == OrderStatus.PENDING_PAYMENT) break;
                    fulfillment.changeStatus(order, actors.employee().accountId(), new FulfillmentRequests.StatusChange(
                            UUID.randomUUID(), target, target == FulfillmentRequests.Target.SHIPPED ? "DEMO-" + order : null));
                    if (target.name().equals(stage.name())) break;
                }
                return null;
            });
        }

        var guest = carts.createGuest();
        carts.guestPut(guest.token(), products.get(6), 4);
        var quote = orders.guestPreview(guest.token(), new OrderRequests.Preview("guest@demo.test", "500600701", address));
        UUID guestOrder = orders.guestCheckout(quote.purchaseToken(), new OrderRequests.Checkout(quote.id(), UUID.randomUUID())).id();
        pay(null, quote.purchaseToken(), guestOrder); // Normal outbox sends the link after this transaction commits.
        as(actors.customer(), () -> {
            carts.accountPut(customer, products.get(0), 2);
            carts.accountPut(customer, products.get(1), 1);
            return null;
        });
        seeds.record("presentation-v1", clock.instant());
        return actors;
    }

    private void historySeed(DemoAccounts.Actors actors) {
        // A separate customer and new products preserve all existing carts, profiles and offers on upgrade.
        var customer = demoAccounts.createHistoryCustomer(properties.password());
        var address = new AccountRequests.AddressUpdate("Historia Demo", "Przykładowa", "20", null, "30-001", "Kraków", "PL");
        var delivery = new OrderRequests.Preview("history@demo.test", "500600702", address);
        var products = new ArrayList<UUID>();
        for (int i = 0; i < 9; i++) {
            int index = i;
            UUID product = as(actors.employee(), () -> {
                UUID id = catalog.create(new TireRequests.Details("Dębica", "Demo " + (index + 16),
                        205, 55, 16, Season.values()[index % Season.values().length], null)).id();
                inventory.change(id, new MovementRequest(UUID.randomUUID(), MovementType.RECEIPT,
                        40, "Dostawa do prezentacji historii"), actors.employee().accountId());
                return id;
            });
            as(actors.admin(), () -> {
                catalog.price(product, new TireRequests.Price(new BigDecimal("249.00").add(BigDecimal.valueOf(index * 10L))));
                catalog.sale(product, new TireRequests.Sale(true));
                return null;
            });
            products.add(product);
        }
        as(actors.employee(), () -> {
            // More than one page, including positive/negative corrections and later sale entries.
            for (int i = 0; i < 20; i++) {
                inventory.change(products.getFirst(), new MovementRequest(UUID.randomUUID(), MovementType.CORRECTION,
                        i % 2 == 0 ? 1 : -1, "Demonstracyjna korekta inwentaryzacyjna " + (i + 1)), actors.employee().accountId());
            }
            return null;
        });
        for (int i = 0; i < 21; i++) {
            int index = i;
            UUID order = as(customer, () -> {
                UUID account = customer.accountId();
                carts.accountPut(account, products.getFirst(), index < 2 ? 3 : 1);
                if (index == 0) carts.accountPut(account, products.get(1), 1); // Four mixed tires: free shipping.
                var quote = orders.accountPreview(account, delivery);
                UUID id = orders.accountCheckout(account, new OrderRequests.Checkout(quote.id(), UUID.randomUUID())).id();
                if (index == 0) {
                    for (int attempt = 0; attempt < 20; attempt++) {
                        payment(account, null, id, attempt % 2 == 0 ? PaymentRequests.Outcome.FAILED : PaymentRequests.Outcome.CANCELLED);
                    }
                    pay(account, null, id);
                } else if (index == 1) pay(account, null, id);
                else {
                    if (index == 2 || index == 20) pendingPayment(account, null, id);
                    if (index != 20) orders.accountCancel(account, id);
                }
                return id;
            });
            if (index == 1) as(actors.employee(), () -> {
                UUID employee = actors.employee().accountId();
                fulfillment.changeStatus(order, employee, new FulfillmentRequests.StatusChange(UUID.randomUUID(), FulfillmentRequests.Target.PROCESSING, null));
                fulfillment.changeStatus(order, employee, new FulfillmentRequests.StatusChange(UUID.randomUUID(), FulfillmentRequests.Target.SHIPPED, "DEMO-OLD-" + order));
                fulfillment.shipment(order, employee, new FulfillmentRequests.Shipment(UUID.randomUUID(), "DEMO-NEW-" + order, "DEMO-OLD-" + order));
                return null;
            });
        }
        var guest = carts.createGuest();
        carts.guestPut(guest.token(), products.get(2), 4);
        var quote = orders.guestPreview(guest.token(), new OrderRequests.Preview("history-guest@demo.test", "500600703", address));
        UUID guestOrder = orders.guestCheckout(quote.purchaseToken(), new OrderRequests.Checkout(quote.id(), UUID.randomUUID())).id();
        payment(null, quote.purchaseToken(), guestOrder, PaymentRequests.Outcome.FAILED);
        payment(null, quote.purchaseToken(), guestOrder, PaymentRequests.Outcome.CANCELLED);
        pay(null, quote.purchaseToken(), guestOrder);

        carts.guestPut(guest.token(), products.get(2), 1);
        var cancelledQuote = orders.guestPreview(guest.token(), new OrderRequests.Preview("cancelled-guest@demo.test", "500600704", address));
        UUID cancelled = orders.guestCheckout(cancelledQuote.purchaseToken(), new OrderRequests.Checkout(cancelledQuote.id(), UUID.randomUUID())).id();
        pendingPayment(null, cancelledQuote.purchaseToken(), cancelled);
        orders.guestCancel(cancelledQuote.purchaseToken(), cancelled);
    }

    private void pay(UUID account, String token, UUID order) {
        payment(account, token, order, PaymentRequests.Outcome.SUCCEEDED);
    }

    private PaymentResponse pendingPayment(UUID account, String token, UUID order) {
        var attempt = payments.create(account, token, order, UUID.randomUUID());
        if (attempt.errorCode() != null) throw new IllegalStateException("Demo payment could not be created.");
        return attempt.payment();
    }

    private void payment(UUID account, String token, UUID order, PaymentRequests.Outcome outcome) {
        var attempt = pendingPayment(account, token, order);
        var result = payments.result(account, token, order, attempt.id(), outcome);
        if (result.errorCode() != null) throw new IllegalStateException("Demo payment could not be completed.");
    }

    private <T> T as(AccountPrincipal principal, Supplier<T> action) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try { return action.get(); }
        finally { SecurityContextHolder.setContext(previous); }
    }
}
