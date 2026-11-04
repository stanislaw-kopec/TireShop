package com.shop.tire.inventory;

import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface InventoryChangeRepository extends JpaRepository<InventoryChange, UUID> {
    Page<InventoryChange> findByProductId(UUID productId, Pageable pageable);
}
