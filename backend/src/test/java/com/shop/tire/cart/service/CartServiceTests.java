package com.shop.tire.cart.service;

import com.shop.tire.catalog.dto.TireResponse;
import com.shop.tire.catalog.model.Season;
import com.shop.tire.catalog.service.CatalogService;
import com.shop.tire.shared.error.DomainException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class CartServiceTests {
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private final UUID customer = UUID.randomUUID();
    private final InMemoryCartRepository repository = new InMemoryCartRepository();
    private final Map<UUID, TireResponse> products = new HashMap<>();
    private final CatalogService catalog = mock(CatalogService.class);
    private final CartService service = new CartService(repository, catalog, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void catalogReadsCurrentProducts() {
        when(catalog.cartProducts(anyCollection())).thenAnswer(call -> {
            Collection<UUID> ids = call.getArgument(0);
            return ids.stream().map(products::get).filter(Objects::nonNull).toList();
        });
    }

    @ParameterizedTest
    @CsvSource({"1,20.00", "3,20.00", "4,0.00", "5,0.00"})
    void deliveryDependsOnTotalQuantity(int quantity, String shipping) {
        UUID product = offer("Debica", "Frigo", "109.99", 10, true);

        var cart = service.accountPut(customer, product, quantity);

        assertThat(cart.shippingGross()).isEqualByComparingTo(shipping);
        assertThat(cart.itemsTotalGross()).isEqualByComparingTo(new BigDecimal("109.99").multiply(BigDecimal.valueOf(quantity)));
        assertThat(cart.totalGross()).isEqualByComparingTo(cart.itemsTotalGross().add(new BigDecimal(shipping)));
        assertThat(cart.readyForCheckout()).isTrue();
        assertThat(service.account(customer)).isEqualTo(cart);
    }

    @ParameterizedTest
    @CsvSource({"1,20.00", "2,0.00"})
    void mixedBrandsCountTowardsFreeDelivery(int secondQuantity, String shipping) {
        UUID first = offer("Debica", "Frigo", "100.00", 10, true);
        UUID second = offer("Michelin", "Primacy", "200.00", 10, true);
        service.accountPut(customer, first, 2);

        var cart = service.accountPut(customer, second, secondQuantity);

        assertThat(cart.totalQuantity()).isEqualTo(2 + secondQuantity);
        assertThat(cart.shippingGross()).isEqualByComparingTo(shipping);
    }

    @Test
    void emptyCartCannotBeCheckedOut() {
        var cart = service.account(customer);
        assertThat(cart.readyForCheckout()).isFalse();
        assertThat(cart.totalGross()).isZero();
        assertThatThrownBy(() -> service.accountCheckout(customer))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("EMPTY_CART"));
    }

    @Test
    void exactlyAvailableStockCanBeAdded() {
        UUID product = offer("Debica", "Frigo", "100.00", 3, true);
        assertThat(service.accountPut(customer, product, 3).readyForCheckout()).isTrue();
    }

    @Test
    void exceedingStockPreservesPreviousQuantity() {
        UUID product = offer("Debica", "Frigo", "100.00", 3, true);
        var before = service.accountPut(customer, product, 2);

        assertThatThrownBy(() -> service.accountPut(customer, product, 4))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("CART_PRODUCT_UNAVAILABLE"));
        assertThat(service.account(customer)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void nonPositiveQuantityIsRejected(int quantity) {
        UUID product = offer("Debica", "Frigo", "100.00", 3, true);
        assertThatThrownBy(() -> service.accountPut(customer, product, quantity))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("INVALID_QUANTITY"));
        assertThat(service.account(customer).items()).isEmpty();
    }

    @Test
    void productWithoutPriceCannotBeAdded() {
        UUID product = offer("Debica", "Frigo", null, 10, true);
        assertThatThrownBy(() -> service.accountPut(customer, product, 1))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("CART_PRODUCT_UNAVAILABLE"));
        assertThat(service.account(customer).items()).isEmpty();
    }

    @Test
    void withdrawnProductCannotBeAdded() {
        UUID product = offer("Debica", "Frigo", "100.00", 10, false);
        assertThatThrownBy(() -> service.accountPut(customer, product, 1))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("CART_PRODUCT_UNAVAILABLE"));
        assertThat(service.account(customer).items()).isEmpty();
    }

    @Test
    void missingProductReturnsStableErrorCode() {
        assertThatThrownBy(() -> service.accountPut(customer, UUID.randomUUID(), 1))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("PRODUCT_NOT_FOUND"));
        assertThat(service.account(customer).items()).isEmpty();
    }

    @Test
    void putSetsTargetQuantityAndCanBeRetried() {
        UUID product = offer("Debica", "Frigo", "100.00", 10, true);
        service.accountPut(customer, product, 3);
        service.accountPut(customer, product, 2);
        service.accountPut(customer, product, 2);
        assertThat(service.account(customer).totalQuantity()).isEqualTo(2);
    }

    @Test
    void deletingTwiceLeavesEmptyCart() {
        UUID product = offer("Debica", "Frigo", "100.00", 10, true);
        service.accountPut(customer, product, 1);
        service.accountDelete(customer, product);
        var cart = service.accountDelete(customer, product);
        assertThat(cart.items()).isEmpty();
        assertThat(cart.totalGross()).isZero();
        assertThat(cart.readyForCheckout()).isFalse();
    }

    @Test
    void mergeSumsQuantitiesAndRetryDoesNotDuplicateItems() {
        UUID product = offer("Debica", "Frigo", "100.00", 10, true);
        String token = service.createGuest().token();
        service.guestPut(token, product, 1);
        service.accountPut(customer, product, 3);

        var merged = service.merge(customer, token);
        assertThat(merged.totalQuantity()).isEqualTo(4);
        assertThat(merged.shippingGross()).isZero();
        assertThat(service.merge(customer, token)).isEqualTo(merged);
        assertThat(service.account(customer)).isEqualTo(merged);
        assertThatThrownBy(() -> service.guest(token))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("CART_NOT_FOUND"));
    }

    @Test
    void failedMergePreservesBothCarts() {
        UUID product = offer("Debica", "Frigo", "100.00", 3, true);
        String token = service.createGuest().token();
        var guest = service.guestPut(token, product, 2);
        var account = service.accountPut(customer, product, 2);

        assertThatThrownBy(() -> service.merge(customer, token))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("CART_MERGE_UNAVAILABLE"));
        assertThat(service.guest(token)).isEqualTo(guest);
        assertThat(service.account(customer)).isEqualTo(account);
    }

    @Test
    void mergedTokenCannotBeUsedByAnotherAccount() {
        UUID product = offer("Debica", "Frigo", "100.00", 10, true);
        String token = service.createGuest().token();
        service.guestPut(token, product, 1);
        var merged = service.merge(customer, token);
        UUID otherCustomer = UUID.randomUUID();

        assertThatThrownBy(() -> service.merge(otherCustomer, token))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("CART_NOT_FOUND"));
        assertThat(service.account(customer)).isEqualTo(merged);
        assertThat(service.account(otherCustomer).items()).isEmpty();
    }

    @Test
    void guestCanReadCartJustBeforeExpiry() {
        var guest = service.createGuest();
        var later = new CartService(repository, catalog, Clock.fixed(guest.expiresAt().minusNanos(1), ZoneOffset.UTC));
        assertThat(guest.expiresAt()).isEqualTo(NOW.plusSeconds(86400));
        assertThat(later.guest(guest.token())).isEqualTo(guest.cart());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void guestCannotReadCartAtOrAfterExpiry(long offsetNanos) {
        var guest = service.createGuest();
        var later = new CartService(repository, catalog, Clock.fixed(guest.expiresAt().plusNanos(offsetNanos), ZoneOffset.UTC));
        assertThatThrownBy(() -> later.guest(guest.token()))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("CART_NOT_FOUND"));
    }

    private UUID offer(String manufacturer, String model, String price, int available, boolean onSale) {
        UUID id = UUID.randomUUID();
        products.put(id, new TireResponse(id, UUID.randomUUID(), manufacturer, model, 205, 55, 16,
                Season.WINTER, null, price == null ? null : new BigDecimal(price), "PLN", available, onSale));
        return id;
    }
}
