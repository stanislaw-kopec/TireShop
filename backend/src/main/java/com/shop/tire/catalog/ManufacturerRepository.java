package com.shop.tire.catalog;

import java.util.UUID;
import org.springframework.data.jpa.repository.*;

interface ManufacturerRepository extends JpaRepository<Manufacturer, UUID> {
    Manufacturer findByNormalizedName(String normalizedName);

    @Modifying
    @Query(value = "INSERT INTO manufacturer(id, name, normalized_name) VALUES (:id, :name, :normalized) "
            + "ON CONFLICT (normalized_name) DO NOTHING", nativeQuery = true)
    void insertIfAbsent(UUID id, String name, String normalized);
}
