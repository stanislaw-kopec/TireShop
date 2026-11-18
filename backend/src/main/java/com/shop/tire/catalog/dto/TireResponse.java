package com.shop.tire.catalog.dto;

import com.shop.tire.catalog.model.Season;
import java.math.BigDecimal;
import java.util.UUID;

public record TireResponse(UUID id, UUID manufacturerId, String manufacturer, String model,
                           int widthMm, int aspectRatio, int rimDiameterIn, Season season,
                           String imageUrl, BigDecimal priceGross, String currency, int availableQuantity,
                           boolean onSale) {}
