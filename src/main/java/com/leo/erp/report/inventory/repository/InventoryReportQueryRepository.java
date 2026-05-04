package com.leo.erp.report.inventory.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.report.inventory.web.dto.InventoryReportResponse;
import com.leo.erp.security.permission.DataScopeContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Repository
public class InventoryReportQueryRepository {

    private static final String INVENTORY_CTE = """
            WITH inventory AS (
                SELECT
                    movement.material_code,
                    movement.brand,
                    movement.material,
                    movement.category,
                    movement.spec,
                    movement.length,
                    movement.warehouse_name,
                    movement.batch_no,
                    movement.quantity_unit,
                    movement.unit,
                    SUM(movement.quantity_delta) AS quantity,
                    SUM(movement.weight_delta) AS weight_ton
                FROM (
                    SELECT
                        item.material_code,
                        item.brand,
                        item.material,
                        item.category,
                        item.spec,
                        item.length,
                        COALESCE(NULLIF(item.warehouse_name, ''), inbound.warehouse_name) AS warehouse_name,
                        item.batch_no,
                        item.quantity_unit,
                        item.unit,
                        item.quantity AS quantity_delta,
                        item.weight_ton AS weight_delta
                    FROM po_purchase_inbound inbound
                    JOIN po_purchase_inbound_item item ON item.inbound_id = inbound.id
                    WHERE inbound.deleted_flag = FALSE
                    %s
                    UNION ALL
                    SELECT
                        item.material_code,
                        item.brand,
                        item.material,
                        item.category,
                        item.spec,
                        item.length,
                        outbound.warehouse_name AS warehouse_name,
                        item.batch_no,
                        item.quantity_unit,
                        item.unit,
                        -item.quantity AS quantity_delta,
                        -item.weight_ton AS weight_delta
                    FROM so_sales_outbound outbound
                    JOIN so_sales_outbound_item item ON item.outbound_id = outbound.id
                    WHERE outbound.deleted_flag = FALSE
                    %s
                ) movement
                GROUP BY
                    movement.material_code,
                    movement.brand,
                    movement.material,
                    movement.category,
                    movement.spec,
                    movement.length,
                    movement.warehouse_name,
                    movement.batch_no,
                    movement.quantity_unit,
                    movement.unit
            )
            """;

    private static final RowMapper<InventoryReportResponse> ROW_MAPPER = (rs, rowNum) -> new InventoryReportResponse(
            rs.getLong("id"),
            rs.getString("material_code"),
            rs.getString("brand"),
            rs.getString("material"),
            rs.getString("category"),
            rs.getString("spec"),
            rs.getString("length"),
            rs.getString("warehouse_name"),
            rs.getString("batch_no"),
            rs.getInt("quantity"),
            rs.getString("quantity_unit"),
            rs.getBigDecimal("weight_ton"),
            rs.getString("unit"),
            rs.getBigDecimal("piece_weight_ton")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InventoryReportQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<InventoryReportResponse> page(PageQuery query, String keyword, String warehouseName, String category) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", query.size())
                .addValue("offset", (long) query.page() * query.size());
        String inventoryCte = INVENTORY_CTE.formatted(
                dataScopeClause(params, "inbound"),
                dataScopeClause(params, "outbound")
        );
        String whereClause = buildWhereClause(params, keyword, warehouseName, category);

        Number totalNumber = jdbcTemplate.queryForObject(
                inventoryCte + "SELECT COUNT(*) FROM inventory report" + whereClause,
                params,
                Number.class
        );
        long total = totalNumber == null ? 0L : totalNumber.longValue();
        if (total == 0L) {
            return new PageImpl<>(List.of(), PageRequest.of(query.page(), query.size()), 0);
        }

        String orderExpression = sortExpression("report", query.sortBy(), query.direction());
        String dataSql = (inventoryCte + """
                SELECT *
                FROM (
                    SELECT
                        ROW_NUMBER() OVER (ORDER BY %s) AS id,
                        report.material_code,
                        report.brand,
                        report.material,
                        report.category,
                        report.spec,
                        report.length,
                        report.warehouse_name,
                        report.batch_no,
                        report.quantity,
                        report.quantity_unit,
                        report.weight_ton,
                        report.unit,
                        material.piece_weight_ton
                    FROM inventory report
                    LEFT JOIN md_material material ON material.material_code = report.material_code
                        AND material.deleted_flag = FALSE
                    %s
                ) paged
                ORDER BY %s
                LIMIT :limit OFFSET :offset
                """).formatted(
                orderExpression,
                whereClause,
                sortExpression("paged", query.sortBy(), query.direction())
        );

        List<InventoryReportResponse> rows = jdbcTemplate.query(dataSql, params, ROW_MAPPER);
        return new PageImpl<>(rows, PageRequest.of(query.page(), query.size()), total);
    }

    private String dataScopeClause(MapSqlParameterSource params, String alias) {
        Set<Long> ownerUserIds = DataScopeContext.allowedOwnerUserIds();
        if (ownerUserIds == null) {
            return "";
        }
        if (ownerUserIds.isEmpty()) {
            return "                    AND 1 = 0";
        }
        params.addValue("dataScopeOwnerUserIds", ownerUserIds);
        return "                    AND " + alias + ".created_by IN (:dataScopeOwnerUserIds)";
    }

    private String buildWhereClause(MapSqlParameterSource params, String keyword, String warehouseName, String category) {
        List<String> clauses = new ArrayList<>();
        clauses.add("(report.quantity > 0 OR report.weight_ton > 0)");
        if (keyword != null) {
            params.addValue("keyword", "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%");
            clauses.add("""
                    (
                        LOWER(COALESCE(report.material_code, '')) LIKE :keyword
                        OR LOWER(COALESCE(report.brand, '')) LIKE :keyword
                        OR LOWER(COALESCE(report.spec, '')) LIKE :keyword
                        OR LOWER(COALESCE(report.material, '')) LIKE :keyword
                    )
                    """.stripIndent().trim());
        }
        if (warehouseName != null) {
            params.addValue("warehouseName", warehouseName);
            clauses.add("report.warehouse_name = :warehouseName");
        }
        if (category != null) {
            params.addValue("category", category);
            clauses.add("report.category = :category");
        }
        return "\nWHERE " + String.join("\n  AND ", clauses) + "\n";
    }

    private String sortExpression(String alias, String sortBy, String direction) {
        String sortDirection = "asc".equalsIgnoreCase(direction) ? "ASC" : "DESC";
        return switch (sortBy == null ? "" : sortBy.trim()) {
            case "brand" -> "LOWER(COALESCE(" + alias + ".brand, '')) " + sortDirection
                    + ", LOWER(COALESCE(" + alias + ".material_code, '')) ASC";
            case "category" -> "LOWER(COALESCE(" + alias + ".category, '')) " + sortDirection
                    + ", LOWER(COALESCE(" + alias + ".material_code, '')) ASC";
            case "warehouseName" -> "LOWER(COALESCE(" + alias + ".warehouse_name, '')) " + sortDirection
                    + ", LOWER(COALESCE(" + alias + ".material_code, '')) ASC";
            case "quantity" -> alias + ".quantity " + sortDirection
                    + ", LOWER(COALESCE(" + alias + ".material_code, '')) ASC";
            case "weightTon" -> alias + ".weight_ton " + sortDirection
                    + ", LOWER(COALESCE(" + alias + ".material_code, '')) ASC";
            default -> "LOWER(COALESCE(" + alias + ".material_code, '')) " + sortDirection
                    + ", LOWER(COALESCE(" + alias + ".warehouse_name, '')) ASC";
        };
    }
}
