package com.shop.tire.inventory;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

interface InventoryRepository extends JpaRepository<Inventory, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.productId = :productId")
    Optional<Inventory> findLockedByProductId(UUID productId);
}
