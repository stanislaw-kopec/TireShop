package com.shop.tire.catalog.entity;

import com.shop.tire.catalog.dto.TireRequests;
import com.shop.tire.catalog.model.CatalogNames;
import com.shop.tire.catalog.model.Season;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tire_product")
public class TireProduct {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manufacturer_id") private Manufacturer manufacturer;
    @Column(length = 120) private String model;
    @Column(length = 120) private String normalizedModel;
    private int widthMm;
    private int aspectRatio;
    private int rimDiameterIn;
    @Enumerated(EnumType.STRING) @Column(length = 20) private Season season;
    @Column(length = 2048) private String imageUrl;
    @Column(precision = 12, scale = 2) private BigDecimal priceGross;
    private boolean onSale;
    @Version private long version;
    private Instant createdAt;
    private Instant updatedAt;

    protected TireProduct() {}

    public TireProduct(Manufacturer manufacturer, TireRequests.Details input, Instant now) {
        id = UUID.randomUUID();
        createdAt = now;
        updateDetails(manufacturer, input, now);
    }

    public void updateDetails(Manufacturer manufacturer, TireRequests.Details input, Instant now) {
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

    public void updatePrice(BigDecimal priceGross, Instant now) {
        this.priceGross = priceGross;
        updatedAt = now;
    }

    public void updateSale(boolean onSale, Instant now) {
        this.onSale = onSale;
        updatedAt = now;
    }

    public UUID id() { return id; }
    public BigDecimal priceGross() { return priceGross; }
}
