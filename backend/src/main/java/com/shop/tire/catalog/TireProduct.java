package com.shop.tire.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tire_product")
class TireProduct {
    @Id UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manufacturer_id") Manufacturer manufacturer;
    @Column(length = 120) String model;
    @Column(length = 120) String normalizedModel;
    int widthMm;
    int aspectRatio;
    int rimDiameterIn;
    @Enumerated(EnumType.STRING) @Column(length = 20) Season season;
    @Column(length = 2048) String imageUrl;
    @Column(precision = 12, scale = 2) BigDecimal priceGross;
    boolean onSale;
    @Version long version;
    Instant createdAt;
    Instant updatedAt;

    protected TireProduct() {}

    TireProduct(Manufacturer manufacturer, TireRequests.Details input, Instant now) {
        id = UUID.randomUUID();
        createdAt = now;
        updateDetails(manufacturer, input, now);
    }

    void updateDetails(Manufacturer manufacturer, TireRequests.Details input, Instant now) {
        this.manufacturer = manufacturer;
        model = input.model();
        normalizedModel = CatalogNames.normalized(model);
        widthMm = input.widthMm();
        aspectRatio = input.aspectRatio();
        rimDiameterIn = input.rimDiameterIn();
        season = input.season();
        imageUrl = input.imageUrl();
        updatedAt = now;
    }
}
