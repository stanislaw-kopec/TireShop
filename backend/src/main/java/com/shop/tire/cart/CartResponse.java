package com.shop.tire.cart;

import com.shop.tire.catalog.TireResponse;
import java.math.BigDecimal;
import java.util.List;

public record CartResponse(List<Item> items, long totalQuantity, BigDecimal itemsTotalGross,
                           BigDecimal shippingGross, BigDecimal totalGross, String currency, boolean readyForCheckout) {
    public enum Issue { NONE, NOT_FOR_SALE, INSUFFICIENT_STOCK }
    public record Item(TireResponse product, int quantity, BigDecimal lineTotalGross, Issue issue) {}
}
