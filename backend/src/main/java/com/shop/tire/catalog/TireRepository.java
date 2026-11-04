package com.shop.tire.catalog;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

interface TireRepository extends JpaRepository<TireProduct, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TireProduct p where p.id = :id")
    Optional<TireProduct> findLockedById(UUID id);
}
