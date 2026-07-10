package com.leo.erp.report.inventory.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.report.inventory.domain.InventoryStatusPolicy;
import com.leo.erp.report.inventory.web.dto.InventoryReportItemResponse;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<InventoryReportItemResponse>> ITEM_LIST_TYPE = new TypeReference<>() {
    };

    private static final String INVENTORY_CTE = """
            WITH stock_movement AS (
                SELECT
                    item.material_code,
                    item.brand,
                    item.material,
                    item.category,
                    item.spec,
                    item.length,
                    COALESCE(NULLIF(item.warehouse_name, ''), inbound.warehouse_name) AS warehouse_name,
                    item.batch_no,
                    NULL AS outbound_no,
                    NULL AS outbound_date,
                    item.quantity_unit,
                    item.unit,
                    item.quantity AS on_hand_quantity_delta,
                    0 AS reserved_quantity_delta,
                    item.weight_ton AS on_hand_weight_delta,
                    CAST(0 AS NUMERIC(18, 8)) AS reserved_weight_delta
                FROM po_purchase_inbound inbound
                JOIN po_purchase_inbound_item item ON item.inbound_id = inbound.id
                WHERE inbound.deleted_flag = FALSE
                  AND inbound.status IN (:effectiveInboundStatuses)
                %s
                UNION ALL
                SELECT
                    item.material_code,
                    item.brand,
                    item.material,
                    item.category,
                    item.spec,
                    item.length,
                    COALESCE(NULLIF(item.warehouse_name, ''), outbound.warehouse_name) AS warehouse_name,
                    item.batch_no,
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
                %s
                UNION ALL
                SELECT
                    item.material_code,
                    item.brand,
                    item.material,
                    item.category,
                    item.spec,
                    item.length,
                    COALESCE(NULLIF(item.warehouse_name, ''), outbound.warehouse_name) AS warehouse_name,
                    item.batch_no,
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
                %s
            ),
            stock_line AS (
                SELECT
                    movement.material_code,
                    movement.brand,
                    movement.material,
                    movement.category,
                    movement.spec,
                    movement.length,
                    movement.warehouse_name,
                    movement.batch_no,
                    STRING_AGG(DISTINCT NULLIF(movement.outbound_no, ''), '、') AS outbound_no,
                    STRING_AGG(DISTINCT NULLIF(movement.outbound_date, ''), '、') AS outbound_date,
                    movement.quantity_unit,
                    movement.unit,
                    SUM(movement.on_hand_quantity_delta) AS on_hand_quantity,
                    SUM(movement.reserved_quantity_delta) AS reserved_quantity,
                    SUM(movement.on_hand_weight_delta) AS on_hand_weight_ton,
                    SUM(movement.reserved_weight_delta) AS reserved_weight_ton
                FROM stock_movement movement
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
            ),
            inventory_report AS (
                SELECT
                    stock.material_code,
                    stock.brand,
                    stock.material,
                    stock.category,
                    stock.spec,
                    stock.length,
                    STRING_AGG(DISTINCT NULLIF(stock.warehouse_name, ''), '、') AS warehouse_name,
                    STRING_AGG(DISTINCT NULLIF(stock.batch_no, ''), '、') AS batch_no,
                    SUM(stock.on_hand_quantity) AS on_hand_quantity,
                    SUM(stock.reserved_quantity) AS reserved_quantity,
                    SUM(stock.on_hand_quantity) - SUM(stock.reserved_quantity) AS available_quantity,
                    stock.quantity_unit,
                    SUM(stock.on_hand_weight_ton) AS on_hand_weight_ton,
                    SUM(stock.reserved_weight_ton) AS reserved_weight_ton,
                    SUM(stock.on_hand_weight_ton) - SUM(stock.reserved_weight_ton) AS available_weight_ton,
                    stock.unit,
                    material.piece_weight_ton,
                    JSONB_AGG(
                        JSONB_BUILD_OBJECT(
                            'id', CONCAT_WS('|',
                                stock.material_code,
                                stock.brand,
                                stock.material,
                                stock.category,
                                stock.spec,
                                stock.length,
                                stock.warehouse_name,
                                stock.batch_no
                            ),
                            'materialCode', stock.material_code,
                            'brand', stock.brand,
                            'material', stock.material,
                            'category', stock.category,
                            'spec', stock.spec,
                            'length', stock.length,
                            'warehouseName', stock.warehouse_name,
                            'batchNo', stock.batch_no,
                            'outboundNo', stock.outbound_no,
                            'outboundDate', stock.outbound_date,
                            'quantity', stock.on_hand_quantity,
                            'quantityUnit', stock.quantity_unit,
                            'weightTon', stock.on_hand_weight_ton,
                            'unit', stock.unit,
                            'pieceWeightTon', material.piece_weight_ton
                        )
                        ORDER BY stock.warehouse_name, stock.batch_no
                    ) AS items_json
                FROM stock_line stock
                LEFT JOIN md_material material ON material.material_code = stock.material_code
                    AND material.deleted_flag = FALSE
                %s
                GROUP BY
                    stock.material_code,
                    stock.brand,
                    stock.material,
                    stock.category,
                    stock.spec,
                    stock.length,
                    stock.quantity_unit,
                    stock.unit,
                    material.piece_weight_ton
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

    public Page<InventoryReportResponse> page(PageQuery query, String keyword, String warehouseName, String category,
                                              boolean includeOutbound) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", query.size())
                .addValue("offset", (long) query.page() * query.size());
        addStatusParameters(params);
        String inventoryCte = INVENTORY_CTE.formatted(
                dataScopeClause(params, "inbound"),
                dataScopeClause(params, "outbound"),
                dataScopeClause(params, "outbound"),
                buildStockWhereClause(params, keyword, warehouseName, category)
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
                ) paged
                ORDER BY %s
                LIMIT :limit OFFSET :offset
                """).formatted(
                orderExpression,
                reportWhereClause,
                sortExpression("paged", query.sortBy(), query.direction())
        );

        List<InventoryReportResponse> rows = jdbcTemplate.query(dataSql, params, ROW_MAPPER);
        return new PageImpl<>(rows, PageRequest.of(query.page(), query.size()), total);
    }

    public List<InventoryReportResponse> list(PageQuery query, String keyword, String warehouseName, String category,
                                              boolean includeOutbound) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        addStatusParameters(params);
        String inventoryCte = INVENTORY_CTE.formatted(
                dataScopeClause(params, "inbound"),
                dataScopeClause(params, "outbound"),
                dataScopeClause(params, "outbound"),
                buildStockWhereClause(params, keyword, warehouseName, category)
        );
        String reportWhereClause = buildReportWhereClause(includeOutbound);
        String orderExpression = sortExpression("report", query.sortBy(), query.direction());
        String dataSql = (inventoryCte + """
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
                orderExpression,
                reportWhereClause,
                orderExpression
        );

        return jdbcTemplate.query(dataSql, params, ROW_MAPPER);
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

    private String buildStockWhereClause(MapSqlParameterSource params, String keyword, String warehouseName, String category) {
        List<String> clauses = new ArrayList<>();
        if (keyword != null) {
            params.addValue("keyword", "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%");
            clauses.add("""
                    (
                        LOWER(COALESCE(stock.material_code, '')) LIKE :keyword
                        OR LOWER(COALESCE(stock.brand, '')) LIKE :keyword
                        OR LOWER(COALESCE(stock.spec, '')) LIKE :keyword
                        OR LOWER(COALESCE(stock.material, '')) LIKE :keyword
                    )
                    """.stripIndent().trim());
        }
        if (warehouseName != null) {
            params.addValue("warehouseName", warehouseName);
            clauses.add("stock.warehouse_name = :warehouseName");
        }
        if (category != null) {
            params.addValue("category", category);
            clauses.add("stock.category = :category");
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
                    + ", LOWER(COALESCE(" + alias + ".material_code, '')) ASC";
            case "category" -> "LOWER(COALESCE(" + alias + ".category, '')) " + sortDirection
                    + ", LOWER(COALESCE(" + alias + ".material_code, '')) ASC";
            case "warehouseName" -> "LOWER(COALESCE(" + alias + ".warehouse_name, '')) " + sortDirection
                    + ", LOWER(COALESCE(" + alias + ".material_code, '')) ASC";
            case "quantity" -> alias + ".on_hand_quantity " + sortDirection
                    + ", LOWER(COALESCE(" + alias + ".material_code, '')) ASC";
            case "weightTon" -> alias + ".on_hand_weight_ton " + sortDirection
                    + ", LOWER(COALESCE(" + alias + ".material_code, '')) ASC";
            default -> "LOWER(COALESCE(" + alias + ".material_code, '')) " + sortDirection
                    + ", LOWER(COALESCE(" + alias + ".warehouse_name, '')) ASC";
        };
    }
}
