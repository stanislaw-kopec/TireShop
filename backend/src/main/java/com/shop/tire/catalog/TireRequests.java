package com.shop.tire.catalog;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.validator.constraints.URL;

public final class TireRequests {
    private TireRequests() {}

    public record Details(
            @NotBlank @Size(max = 100) String manufacturer,
            @NotBlank @Size(max = 120) String model,
            @NotNull @Min(100) @Max(500) Integer widthMm,
            @NotNull @Min(20) @Max(100) Integer aspectRatio,
            @NotNull @Min(10) @Max(30) Integer rimDiameterIn,
            @NotNull Season season,
            @Size(max = 2048) @URL(protocol = "https") String imageUrl) {
        public Details {
            manufacturer = manufacturer == null ? null : CatalogNames.display(manufacturer);
            model = model == null ? null : CatalogNames.display(model);
            imageUrl = imageUrl == null || imageUrl.isBlank() ? null : imageUrl.strip();
        }
    }

    public record Price(@NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal priceGross) {}
    public record Sale(@NotNull Boolean onSale) {}

    public enum PriceSort { PRICE_ASC, PRICE_DESC }

    public record Filters(
            UUID manufacturerId,
            @Min(100) @Max(500) Integer widthMm,
            @Min(20) @Max(100) Integer aspectRatio,
            @Min(10) @Max(30) Integer rimDiameterIn,
            Season season,
            @Min(0) @Max(1000000) Integer page,
            @Min(1) @Max(100) Integer size,
            PriceSort sort) {
        public Filters {
            page = page == null ? 0 : page;
            size = size == null ? 20 : size;
            sort = sort == null ? PriceSort.PRICE_ASC : sort;
        }
    }
}
