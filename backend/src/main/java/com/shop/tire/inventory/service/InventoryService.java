package com.shop.tire.inventory.service;

import com.shop.tire.inventory.dto.MovementRequest;
import com.shop.tire.inventory.dto.MovementResponse;
import com.shop.tire.inventory.dto.StockResponse;
import com.shop.tire.inventory.entity.Inventory;
import com.shop.tire.inventory.entity.InventoryChange;
import com.shop.tire.inventory.model.MovementType;
import com.shop.tire.inventory.repository.InventoryChangeRepository;
import com.shop.tire.inventory.repository.InventoryRepository;
import com.shop.tire.shared.api.PageResponse;
import com.shop.tire.shared.error.DomainException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import static com.shop.tire.shared.error.DomainException.Kind.*;

@Service
public class InventoryService {
    private final InventoryRepository inventory;
    private final InventoryChangeRepository changes;
    private final Clock clock;

    public InventoryService(InventoryRepository inventory, InventoryChangeRepository changes, Clock clock) {
        this.inventory = inventory;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void initializeProduct(UUID productId) {
        inventory.saveAndFlush(new Inventory(productId, clock.instant()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(java.util.Map<UUID, Integer> quantities) {
        for (UUID id : quantities.keySet().stream().sorted(java.util.Comparator.comparing(UUID::toString)).toList()) {
            var stock = inventory.findLockedByProductId(id).orElseThrow(InventoryService::notFound);
            int quantity = quantities.get(id);
            stock.reserve(quantity, clock.instant());
        }
        inventory.flush();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(java.util.Map<UUID, Integer> quantities) {
        for (UUID id : quantities.keySet().stream().sorted(java.util.Comparator.comparing(UUID::toString)).toList()) {
            var stock = inventory.findLockedByProductId(id).orElseThrow(InventoryService::notFound);
            int quantity = quantities.get(id);
            stock.release(quantity, clock.instant());
        }
        inventory.flush();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public java.time.Instant sellReserved(UUID orderId, java.util.Map<UUID,Integer> quantities, java.time.Instant deadline) {
        var stocks = quantities.keySet().stream().sorted(java.util.Comparator.comparing(UUID::toString))
                .map(id -> inventory.findLockedByProductId(id).orElseThrow(InventoryService::notFound)).toList();
        // Decide only after ALL stock locks: waiting for a correction cannot permit a late payment.
        var now = clock.instant();
        if (!now.isBefore(deadline)) return null;
        now = now.truncatedTo(ChronoUnit.MICROS);
        for (var stock : stocks) {
            int quantity = quantities.get(stock.productId());
            stock.sellReserved(quantity, now);
            changes.save(InventoryChange.sale(stock,orderId,quantity,now));
        }
        inventory.flush();
        return now;
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(readOnly = true)
    public StockResponse get(UUID productId) {
        return inventory.findById(productId).orElseThrow(InventoryService::notFound).response();
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional
    public MovementResponse change(UUID productId, MovementRequest input, UUID actorId) {
        if (input.type() == MovementType.SALE || input.quantityDelta() == 0 || (input.type() == MovementType.RECEIPT && input.quantityDelta() < 0)) {
            throw new DomainException(INVALID_INPUT, "INVALID_STOCK_CHANGE",
                    "Dostawa wymaga dodatniej liczby sztuk, a korekta zmiany różnej od zera.");
        }
        // Serialize changes of this product; read the operation after taking the same lock on retries.
        var stock = inventory.findLockedByProductId(productId).orElseThrow(InventoryService::notFound);
        var previous = changes.findById(input.operationId());
        if (previous.isPresent()) {
            if (!previous.get().matches(productId, input, actorId)) {
                throw new DomainException(CONFLICT, "OPERATION_ID_REUSED", "Identyfikator operacji został już użyty z innymi danymi.");
            }
            return previous.get().response();
        }
        // PostgreSQL timestamps preserve microseconds; retries must return the same instant.
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        stock.adjust(input.quantityDelta(), now);
        // persist, not merge: an operation ID must never overwrite an audit entry for another product.
        var change = new InventoryChange(stock, input, actorId, now);
        changes.saveAndFlush(change);
        return change.response();
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> history(UUID productId, int page, int size) {
        if (!inventory.existsById(productId)) throw notFound();
        var result = changes.findByProductId(productId, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
        return PageResponse.of(result.getContent().stream().map(InventoryChange::response).toList(), page, size, result.getTotalElements());
    }

    private static DomainException notFound() {
        return new DomainException(NOT_FOUND, "PRODUCT_NOT_FOUND", "Nie znaleziono produktu.");
    }
}
