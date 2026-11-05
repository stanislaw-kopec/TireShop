package com.shop.tire.cart;

import com.shop.tire.catalog.TireResponse;
import java.math.BigDecimal;
import java.util.*;

final class CartTotals {
    private CartTotals() {}

    static CartResponse calculate(Map<UUID, Integer> quantities, List<TireResponse> products) {
        List<CartResponse.Item> items = products.stream().map(product -> {
            int quantity = quantities.get(product.id());
            var issue = !product.onSale() || product.priceGross() == null ? CartResponse.Issue.NOT_FOR_SALE
                    : quantity > product.availableQuantity() ? CartResponse.Issue.INSUFFICIENT_STOCK : CartResponse.Issue.NONE;
            var amount = product.priceGross() == null ? null : product.priceGross().multiply(BigDecimal.valueOf(quantity));
            return new CartResponse.Item(product, quantity, amount, issue);
        }).toList();
        long count = quantities.values().stream().mapToLong(Integer::longValue).sum();
        var zero = new BigDecimal("0.00");
        BigDecimal subtotal = items.stream().anyMatch(item -> item.lineTotalGross() == null) ? null
                : items.stream().map(CartResponse.Item::lineTotalGross).reduce(zero, BigDecimal::add);
        var shipping = count > 0 && count < 4 ? new BigDecimal("20.00") : zero;
        boolean ready = !items.isEmpty() && items.size() == quantities.size()
                && items.stream().allMatch(item -> item.issue() == CartResponse.Issue.NONE);
        return new CartResponse(items, count, subtotal, shipping, subtotal == null ? null : subtotal.add(shipping), "PLN", ready);
    }
}
