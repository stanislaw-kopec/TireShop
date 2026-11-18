package com.shop.tire.catalog.service;

import com.shop.tire.catalog.dto.ManufacturerResponse;
import com.shop.tire.catalog.dto.TireRequests;
import com.shop.tire.catalog.dto.TireResponse;
import com.shop.tire.catalog.entity.Manufacturer;
import com.shop.tire.catalog.entity.TireProduct;
import com.shop.tire.catalog.model.CatalogNames;
import com.shop.tire.catalog.repository.CatalogQueryRepository;
import com.shop.tire.catalog.repository.ManufacturerRepository;
import com.shop.tire.catalog.repository.TireRepository;
import com.shop.tire.inventory.service.InventoryService;
import com.shop.tire.shared.api.PageResponse;
import com.shop.tire.shared.error.DomainException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.shop.tire.shared.error.DomainException.Kind.*;

@Service
public class CatalogService {
    private final TireRepository tires;
    private final ManufacturerRepository manufacturers;
    private final CatalogQueryRepository queries;
    private final InventoryService inventory;
    private final Clock clock;

    public CatalogService(TireRepository tires, ManufacturerRepository manufacturers,
                          CatalogQueryRepository queries, InventoryService inventory, Clock clock) {
        this.tires = tires;
        this.manufacturers = manufacturers;
        this.queries = queries;
        this.inventory = inventory;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<TireResponse> list(TireRequests.Filters filter) { return queries.list(filter, true); }

    @Transactional(readOnly = true)
    public TireResponse get(UUID id) { return queries.find(id, true).orElseThrow(CatalogService::notFound); }

    // Internal read projection: carts must also show previously selected, withdrawn products.
    @Transactional(readOnly = true)
    public java.util.List<TireResponse> cartProducts(java.util.Collection<UUID> ids) { return queries.findAll(ids); }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lockForCheckout(java.util.Collection<UUID> ids) {
        // Use the same order in every multi-product operation; UUID string order matches PostgreSQL.
        // SHARE freezes prices without blocking FK checks from concurrent inventory audit inserts.
        ids.stream().sorted(java.util.Comparator.comparing(UUID::toString))
                .forEach(id -> tires.findForCheckoutById(id).orElseThrow(CatalogService::notFound));
    }

    @Transactional(readOnly = true)
    public PageResponse<ManufacturerResponse> manufacturers(int page, int size) { return queries.manufacturers(page, size); }

    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<TireResponse> staffList(TireRequests.Filters filter) { return queries.list(filter, false); }

    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
    @Transactional(readOnly = true)
    public TireResponse staffGet(UUID id) { return readStaff(id); }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional
    public TireResponse create(TireRequests.Details input) {
        var product = new TireProduct(manufacturer(input.manufacturer()), input, clock.instant());
        product = tires.saveAndFlush(product);
        inventory.initializeProduct(product.id());
        return readStaff(product.id());
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional
    public TireResponse updateDetails(UUID id, TireRequests.Details input) {
        var product = locked(id);
        product.updateDetails(manufacturer(input.manufacturer()), input, clock.instant());
        tires.flush();
        return readStaff(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public TireResponse price(UUID id, TireRequests.Price input) {
        var product = locked(id);
        product.updatePrice(input.priceGross().setScale(2), clock.instant());
        tires.flush();
        return readStaff(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public TireResponse sale(UUID id, TireRequests.Sale input) {
        var product = locked(id);
        if (input.onSale() && product.priceGross() == null) {
            throw new DomainException(CONFLICT, "PRICE_REQUIRED", "Przed włączeniem sprzedaży ustaw cenę.");
        }
        product.updateSale(input.onSale(), clock.instant());
        tires.flush();
        return readStaff(id);
    }

    private Manufacturer manufacturer(String name) {
        String normalized = CatalogNames.normalized(name);
        manufacturers.insertIfAbsent(UUID.randomUUID(), name, normalized);
        return manufacturers.findByNormalizedName(normalized);
    }

    private TireProduct locked(UUID id) { return tires.findLockedById(id).orElseThrow(CatalogService::notFound); }
    private TireResponse readStaff(UUID id) { return queries.find(id, false).orElseThrow(CatalogService::notFound); }
    private static DomainException notFound() {
        return new DomainException(NOT_FOUND, "PRODUCT_NOT_FOUND", "Nie znaleziono produktu.");
    }
}
