package com.shop.tire.order.model;

import com.shop.tire.account.dto.AccountRequests;
import com.shop.tire.cart.dto.CartResponse;
import com.shop.tire.catalog.model.Season;
import java.math.BigDecimal;
import java.util.*;

public record OrderSnapshot(List<Line> items, Delivery delivery, BigDecimal itemsTotalGross,
                            BigDecimal shippingGross, BigDecimal totalGross, String currency) {
    public record Delivery(String email, String phone, AccountRequests.AddressUpdate address) {
        @Override public String toString() { return "Delivery[redacted]"; }
    }
    public record Product(UUID id, String manufacturer, String model, int widthMm, int aspectRatio,
                          int rimDiameterIn, Season season, String imageUrl) {}
    public record Line(Product product, int quantity, BigDecimal unitPriceGross, BigDecimal lineTotalGross) {}
    public static OrderSnapshot from(CartResponse cart, Delivery delivery) {
        var lines = cart.items().stream().map(item -> {
            var p = item.product();
            return new Line(new Product(p.id(),p.manufacturer(),p.model(),p.widthMm(),p.aspectRatio(),p.rimDiameterIn(),p.season(),p.imageUrl()),
                    item.quantity(),p.priceGross(),item.lineTotalGross());
        }).toList();
        return new OrderSnapshot(lines, delivery, cart.itemsTotalGross(),cart.shippingGross(),cart.totalGross(),cart.currency());
    }
    public Map<UUID,Integer> quantities() {
        Map<UUID,Integer> result = new LinkedHashMap<>();
        items.forEach(line -> result.put(line.product().id(),line.quantity()));
        return result;
    }
}
