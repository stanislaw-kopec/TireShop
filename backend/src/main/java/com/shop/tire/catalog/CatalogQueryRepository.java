package com.shop.tire.catalog;

import com.shop.tire.shared.api.PageResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class CatalogQueryRepository {
    private static final String FROM = " FROM tire_product p JOIN manufacturer m ON m.id = p.manufacturer_id "
            + "JOIN inventory i ON i.product_id = p.id ";
    private static final String VISIBLE = "p.on_sale AND p.price_gross > 0 AND i.on_hand > i.reserved";
    private static final String COLUMNS = "p.id, m.id AS manufacturer_id, m.name AS manufacturer, p.model, "
            + "p.width_mm, p.aspect_ratio, p.rim_diameter_in, p.season, p.image_url, p.price_gross, p.on_sale, "
            + "i.on_hand - i.reserved AS available_quantity";
    private final NamedParameterJdbcTemplate jdbc;

    CatalogQueryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    PageResponse<TireResponse> list(TireRequests.Filters filter, boolean publicOnly) {
        StringBuilder where = new StringBuilder(" WHERE ").append(publicOnly ? VISIBLE : "TRUE");
        Map<String, Object> params = new HashMap<>();
        add(where, params, "p.manufacturer_id", "manufacturer", filter.manufacturerId());
        add(where, params, "p.width_mm", "width", filter.widthMm());
        add(where, params, "p.aspect_ratio", "profile", filter.aspectRatio());
        add(where, params, "p.rim_diameter_in", "rim", filter.rimDiameterIn());
        add(where, params, "p.season", "season", filter.season() == null ? null : filter.season().name());
        long total = jdbc.queryForObject("SELECT count(*)" + FROM + where, params, Long.class);
        params.put("limit", filter.size());
        params.put("offset", (long) filter.page() * filter.size());
        String direction = filter.sort() == TireRequests.PriceSort.PRICE_DESC ? "DESC" : "ASC";
        var rows = jdbc.query("SELECT " + COLUMNS + FROM + where
                + " ORDER BY p.price_gross " + direction + " NULLS LAST, p.id ASC LIMIT :limit OFFSET :offset",
                params, (rs, row) -> tire(rs));
        return PageResponse.of(rows, filter.page(), filter.size(), total);
    }

    Optional<TireResponse> find(UUID id, boolean publicOnly) {
        return jdbc.query("SELECT " + COLUMNS + FROM + " WHERE p.id = :id"
                + (publicOnly ? " AND " + VISIBLE : ""), Map.of("id", id), (rs, row) -> tire(rs)).stream().findFirst();
    }

    PageResponse<ManufacturerResponse> manufacturers(int page, int size) {
        String from = " FROM manufacturer m WHERE EXISTS (SELECT 1 FROM tire_product p "
                + "JOIN inventory i ON p.id = i.product_id WHERE p.manufacturer_id = m.id AND " + VISIBLE + ")";
        var params = Map.of("limit", size, "offset", (long) page * size);
        long total = jdbc.queryForObject("SELECT count(*)" + from, params, Long.class);
        var rows = jdbc.query("SELECT m.id, m.name" + from
                + " ORDER BY m.normalized_name, m.id LIMIT :limit OFFSET :offset", params,
                (rs, row) -> new ManufacturerResponse(rs.getObject("id", UUID.class), rs.getString("name")));
        return PageResponse.of(rows, page, size, total);
    }

    private void add(StringBuilder sql, Map<String, Object> params, String column, String key, Object value) {
        if (value != null) {
            sql.append(" AND ").append(column).append(" = :").append(key);
            params.put(key, value);
        }
    }

    private TireResponse tire(ResultSet rs) throws SQLException {
        return new TireResponse(rs.getObject("id", UUID.class), rs.getObject("manufacturer_id", UUID.class),
                rs.getString("manufacturer"), rs.getString("model"), rs.getInt("width_mm"),
                rs.getInt("aspect_ratio"), rs.getInt("rim_diameter_in"), Season.valueOf(rs.getString("season")),
                rs.getString("image_url"), rs.getBigDecimal("price_gross"), "PLN",
                rs.getInt("available_quantity"), rs.getBoolean("on_sale"));
    }
}
