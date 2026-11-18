package com.shop.tire.catalog.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "manufacturer")
public class Manufacturer {
    @Id private UUID id;
    @Column(length = 100) private String name;
    @Column(length = 100) private String normalizedName;

    protected Manufacturer() {}
}
