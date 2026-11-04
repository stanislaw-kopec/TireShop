package com.shop.tire.catalog;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "manufacturer")
class Manufacturer {
    @Id UUID id;
    @Column(length = 100) String name;
    @Column(length = 100) String normalizedName;

    protected Manufacturer() {}
}
