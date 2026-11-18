package com.shop.tire.inventory.repository;

import com.shop.tire.inventory.entity.InventoryChange;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryChangeRepository extends JpaRepository<InventoryChange, UUID> {
    Page<InventoryChange> findByProductId(UUID productId, Pageable pageable);
}
