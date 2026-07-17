package com.leo.erp.report.inventory.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.report.inventory.domain.InventoryStatusPolicy;
import com.leo.erp.report.inventory.web.dto.InventoryReportItemResponse;
import com.leo.erp.report.inventory.web.dto.InventoryReportResponse;
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

@Repository
public class InventoryReportQueryRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<InventoryReportItemResponse>> ITEM_LIST_TYPE = new TypeReference<>() {
    };

    private static final String INVENTORY_CTE = """
            WITH stock_movement AS (
                SELECT
                    item.id,
                    item.material_id,
                    COALESCE(item.warehouse_id, inbound.warehouse_id) AS warehouse_id,
                    item.material_code,
                    item.brand,
                    item.material,
                    item.category,
                    item.spec,
                    item.length,
                    COALESCE(NULLIF(item.warehouse_name, ''), inbound.warehouse_name) AS warehouse_name,
                    item.batch_no,
                    item.batch_no_normalized,
                    NULL AS outbound_no,
                    NULL AS outbound_date,
                    item.quantity_unit,
                    item.unit,
                    item.quantity AS on_hand_quantity_delta,
                    0 AS reserved_quantity_delta,
                    COALESCE(item.weigh_weight_ton, item.weight_ton) AS on_hand_weight_delta,
                    CAST(0 AS NUMERIC(18, 8)) AS reserved_weight_delta
                FROM po_purchase_inbound inbound
                JOIN po_purchase_inbound_item item ON item.inbound_id = inbound.id
                WHERE inbound.deleted_flag = FALSE
                  AND inbound.status IN (:effectiveInboundStatuses)
                UNION ALL
                SELECT
                    item.id,
                    item.material_id,
                    COALESCE(item.warehouse_id, outbound.warehouse_id) AS warehouse_id,
                    item.material_code,
                    item.brand,
                    item.material,
                    item.category,
                    item.spec,
                    item.length,
                    COALESCE(NULLIF(item.warehouse_name, ''), outbound.warehouse_name) AS warehouse_name,
                    item.batch_no,
                    item.batch_no_normalized,
                    outbound.outbound_no AS outbound_no,
                    TO_CHAR(outbound.outbound_date, 'YYYY-MM-DD') AS outbound_date,
                    item.quantity_unit,
                    item.unit,
                    -item.quantity AS on_hand_quantity_delta,
                    0 AS reserved_quantity_delta,
                    -item.weight_ton AS on_hand_weight_delta,
                    CAST(0 AS NUMERIC(18, 8)) AS reserved_weight_delta
                FROM so_sales_outbound outbound
                JOIN so_sales_outbound_item item ON item.outbound_id = outbound.id
                WHERE outbound.deleted_flag = FALSE
                  AND outbound.status IN (:effectiveOutboundStatuses)
                UNION ALL
                SELECT
                    item.id,
                    item.material_id,
                    COALESCE(item.warehouse_id, outbound.warehouse_id) AS warehouse_id,
                    item.material_code,
                    item.brand,
                    item.material,
                    item.category,
                    item.spec,
                    item.length,
                    COALESCE(NULLIF(item.warehouse_name, ''), outbound.warehouse_name) AS warehouse_name,
                    item.batch_no,
                    item.batch_no_normalized,
                    NULL AS outbound_no,
                    NULL AS outbound_date,
                    item.quantity_unit,
                    item.unit,
                    0 AS on_hand_quantity_delta,
                    item.quantity AS reserved_quantity_delta,
                    CAST(0 AS NUMERIC(18, 8)) AS on_hand_weight_delta,
                    item.weight_ton AS reserved_weight_delta
                FROM so_sales_outbound outbound
                JOIN so_sales_outbound_item item ON item.outbound_id = outbound.id
                WHERE outbound.deleted_flag = FALSE
                  AND outbound.status IN (:reservedOutboundStatuses)
            ),
            stock_filtered AS (
                SELECT movement.*
                FROM stock_movement movement
                JOIN md_material material ON material.id = movement.material_id
                JOIN md_warehouse warehouse ON warehouse.id = movement.warehouse_id
                %s
            ),
            stock_dimension AS (
                SELECT
                    movement.material_id,
                    movement.warehouse_id,
                    movement.batch_no_normalized,
                    MIN(movement.batch_no) AS batch_no,
                    SUM(movement.on_hand_quantity_delta) AS on_hand_quantity,
                    SUM(movement.reserved_quantity_delta) AS reserved_quantity,
                    SUM(movement.on_hand_weight_delta) AS on_hand_weight_ton,
                    SUM(movement.reserved_weight_delta) AS reserved_weight_ton
                FROM stock_filtered movement
                GROUP BY
                    movement.material_id,
                    movement.warehouse_id,
                    movement.batch_no_normalized
            ),
            inventory_items AS (
                SELECT
                    stock.material_id,
                    JSONB_AGG(
                        JSONB_BUILD_OBJECT(
                            'id', stock.id,
                            'materialId', stock.material_id,
                            'warehouseId', stock.warehouse_id,
                            'materialCode', material.material_code,
                            'brand', material.brand,
                            'material', material.material,
                            'category', material.category,
                            'spec', material.spec,
                            'length', material.length,
                            'warehouseName', warehouse.warehouse_name,
                            'batchNo', stock.batch_no,
                            'outboundNo', stock.outbound_no,
                            'outboundDate', stock.outbound_date,
                            'quantity', stock.on_hand_quantity_delta,
                            'quantityUnit', material.quantity_unit,
                            'weightTon', stock.on_hand_weight_delta,
                            'unit', material.unit,
                            'pieceWeightTon', material.piece_weight_ton
                        )
                        ORDER BY stock.id
                    ) AS items_json
                FROM stock_filtered stock
                JOIN md_material material ON material.id = stock.material_id
                JOIN md_warehouse warehouse ON warehouse.id = stock.warehouse_id
                GROUP BY stock.material_id
            ),
            inventory_report AS (
                SELECT
                    stock.material_id,
                    material.material_code,
                    material.brand,
                    material.material,
                    material.category,
                    material.spec,
                    material.length,
                    STRING_AGG(DISTINCT NULLIF(warehouse.warehouse_name, ''), '、') AS warehouse_name,
                    STRING_AGG(DISTINCT NULLIF(stock.batch_no, ''), '、') AS batch_no,
                    SUM(stock.on_hand_quantity) AS on_hand_quantity,
                    SUM(stock.reserved_quantity) AS reserved_quantity,
                    SUM(stock.on_hand_quantity) - SUM(stock.reserved_quantity) AS available_quantity,
                    material.quantity_unit,
                    SUM(stock.on_hand_weight_ton) AS on_hand_weight_ton,
                    SUM(stock.reserved_weight_ton) AS reserved_weight_ton,
                    SUM(stock.on_hand_weight_ton) - SUM(stock.reserved_weight_ton) AS available_weight_ton,
                    material.unit,
                    material.piece_weight_ton,
                    items.items_json
                FROM stock_dimension stock
                JOIN md_material material ON material.id = stock.material_id
                JOIN md_warehouse warehouse ON warehouse.id = stock.warehouse_id
                JOIN inventory_items items ON items.material_id = stock.material_id
                GROUP BY
                    stock.material_id,
                    material.material_code,
                    material.brand,
                    material.material,
                    material.category,
                    material.spec,
                    material.length,
                    material.quantity_unit,
                    material.unit,
                    material.piece_weight_ton,
                    items.items_json
            )
            """;

    private static final RowMapper<InventoryReportResponse> ROW_MAPPER = (rs, rowNum) -> new InventoryReportResponse(
            rs.getLong("id"),
            rs.getLong("material_id"),
            rs.getString("material_code"),
            rs.getString("brand"),
            rs.getString("material"),
            rs.getString("category"),
            rs.getString("spec"),
            rs.getString("length"),
            rs.getString("warehouse_name"),
            rs.getString("batch_no"),
            rs.getInt("on_hand_quantity"),
            rs.getInt("reserved_quantity"),
            rs.getInt("available_quantity"),
            rs.getString("quantity_unit"),
            rs.getBigDecimal("on_hand_weight_ton"),
            rs.getBigDecimal("reserved_weight_ton"),
            rs.getBigDecimal("available_weight_ton"),
            rs.getString("unit"),
            rs.getBigDecimal("piece_weight_ton"),
            parseItems(rs.getObject("items_json"))
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InventoryReportQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<InventoryReportResponse> page(PageQuery query, String keyword, Long warehouseId, String category,
                                              boolean includeOutbound) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", query.size())
                .addValue("offset", (long) query.page() * query.size());
        addStatusParameters(params);
        String inventoryCte = INVENTORY_CTE.formatted(
                buildStockWhereClause(params, keyword, warehouseId, category)
        );
        String reportWhereClause = buildReportWhereClause(includeOutbound);

        Number totalNumber = jdbcTemplate.queryForObject(
                inventoryCte + "SELECT COUNT(1) FROM inventory_report report" + reportWhereClause,
                params,
                Number.class
        );
        long total = totalNumber == null ? 0L : totalNumber.longValue();
        if (total == 0L) {
            return new PageImpl<>(List.of(), PageRequest.of(query.page(), query.size()), 0);
        }

        String orderExpression = sortExpression("report", query.sortBy(), query.direction());
        String dataSql = (inventoryCte + """
                SELECT
                    report.material_id AS id,
                    report.material_id,
                    report.material_code,
                    report.brand,
                    report.material,
                    report.category,
                    report.spec,
                    report.length,
                    report.warehouse_name,
                    report.batch_no,
                    report.on_hand_quantity AS quantity,
                    report.on_hand_quantity,
                    report.reserved_quantity,
                    report.available_quantity,
                    report.quantity_unit,
                    report.on_hand_weight_ton AS weight_ton,
                    report.on_hand_weight_ton,
                    report.reserved_weight_ton,
                    report.available_weight_ton,
                    report.unit,
                    report.piece_weight_ton,
                    report.items_json
                FROM inventory_report report
                %s
                ORDER BY %s
                LIMIT :limit OFFSET :offset
                """).formatted(
                reportWhereClause,
                orderExpression
        );

        List<InventoryReportResponse> rows = jdbcTemplate.query(dataSql, params, ROW_MAPPER);
        return new PageImpl<>(rows, PageRequest.of(query.page(), query.size()), total);
    }

    public List<InventoryReportResponse> list(PageQuery query, String keyword, Long warehouseId, String category,
                                              boolean includeOutbound) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        addStatusParameters(params);
        String inventoryCte = INVENTORY_CTE.formatted(
                buildStockWhereClause(params, keyword, warehouseId, category)
        );
        String reportWhereClause = buildReportWhereClause(includeOutbound);
        String orderExpression = sortExpression("report", query.sortBy(), query.direction());
        String dataSql = (inventoryCte + """
                SELECT
                    report.material_id AS id,
                    report.material_id,
                    report.material_code,
                    report.brand,
                    report.material,
                    report.category,
                    report.spec,
                    report.length,
                    report.warehouse_name,
                    report.batch_no,
                    report.on_hand_quantity AS quantity,
                    report.on_hand_quantity,
                    report.reserved_quantity,
                    report.available_quantity,
                    report.quantity_unit,
                    report.on_hand_weight_ton AS weight_ton,
                    report.on_hand_weight_ton,
                    report.reserved_weight_ton,
                    report.available_weight_ton,
                    report.unit,
                    report.piece_weight_ton,
                    report.items_json
                FROM inventory_report report
                %s
                ORDER BY %s
                """).formatted(
                reportWhereClause,
                orderExpression
        );

        return jdbcTemplate.query(dataSql, params, ROW_MAPPER);
    }

    private String buildStockWhereClause(MapSqlParameterSource params, String keyword, Long warehouseId, String category) {
        List<String> clauses = new ArrayList<>();
        if (keyword != null) {
            params.addValue("keyword", "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%");
            clauses.add("""
                    (
                        LOWER(COALESCE(material.material_code, '')) LIKE :keyword
                        OR LOWER(COALESCE(material.brand, '')) LIKE :keyword
                        OR LOWER(COALESCE(material.spec, '')) LIKE :keyword
                        OR LOWER(COALESCE(material.material, '')) LIKE :keyword
                    )
                    """.stripIndent().trim());
        }
        if (warehouseId != null) {
            params.addValue("warehouseId", warehouseId);
            clauses.add("movement.warehouse_id = :warehouseId");
        }
        if (category != null) {
            params.addValue("category", category);
            clauses.add("material.category = :category");
        }
        if (clauses.isEmpty()) {
            return "";
        }
        return "\nWHERE " + String.join("\n  AND ", clauses) + "\n";
    }

    private String buildReportWhereClause(boolean includeOutbound) {
        if (includeOutbound) {
            return "";
        }
        return """

                WHERE (
                    report.on_hand_quantity <> 0
                    OR report.on_hand_weight_ton <> 0
                    OR report.reserved_quantity <> 0
                    OR report.reserved_weight_ton <> 0
                )
                """;
    }

    private void addStatusParameters(MapSqlParameterSource params) {
        params.addValue("effectiveInboundStatuses", InventoryStatusPolicy.effectiveInboundStatuses());
        params.addValue("effectiveOutboundStatuses", InventoryStatusPolicy.effectiveOutboundStatuses());
        params.addValue("reservedOutboundStatuses", InventoryStatusPolicy.reservedOutboundStatuses());
    }

    private static List<InventoryReportItemResponse> parseItems(Object value) {
        if (value == null) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(value.toString(), ITEM_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse inventory report items", ex);
        }
    }

    private String sortExpression(String alias, String sortBy, String direction) {
        String sortDirection = "asc".equalsIgnoreCase(direction) ? "ASC" : "DESC";
        return switch (sortBy == null ? "" : sortBy.trim()) {
            case "brand" -> "LOWER(COALESCE(" + alias + ".brand, '')) " + sortDirection
                    + ", " + alias + ".material_id ASC";
            case "category" -> "LOWER(COALESCE(" + alias + ".category, '')) " + sortDirection
                    + ", " + alias + ".material_id ASC";
            case "warehouseName" -> "LOWER(COALESCE(" + alias + ".warehouse_name, '')) " + sortDirection
                    + ", " + alias + ".material_id ASC";
            case "quantity" -> alias + ".on_hand_quantity " + sortDirection
                    + ", " + alias + ".material_id ASC";
            case "weightTon" -> alias + ".on_hand_weight_ton " + sortDirection
                    + ", " + alias + ".material_id ASC";
            default -> "LOWER(COALESCE(" + alias + ".material_code, '')) " + sortDirection
                    + ", " + alias + ".material_id ASC";
        };
    }
}
