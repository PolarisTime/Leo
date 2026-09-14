package com.leo.erp.inventory.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.inventory.web.dto.InventoryBalanceResponse;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 库存余额查询：按 (material_id, warehouse_id, batch_no) 聚合未删除库存事务。
 * 余额不落表，全部查询时聚合，支持 keyword/warehouseId/materialId 过滤与分页。
 */
@Service
public class InventoryBalanceQueryService {

    private static final String BASE_FROM = " FROM inv_transaction t WHERE t.deleted_flag = false";
    private static final String GROUP_BY =
            " GROUP BY t.material_id, t.material_code, t.warehouse_id, t.warehouse_name, t.batch_no";
    private static final String TIE_BREAKER =
            ", t.warehouse_id ASC NULLS FIRST, t.batch_no ASC NULLS FIRST, t.material_code ASC";
    private static final Map<String, String> SORT_EXPRESSIONS = Map.of(
            "materialId", "t.material_id",
            "materialCode", "t.material_code",
            "warehouseId", "t.warehouse_id",
            "warehouseName", "t.warehouse_name",
            "batchNo", "t.batch_no",
            "quantity", "quantity",
            "amount", "amount",
            "avgUnitCost", "avg_unit_cost"
    );
    private static final RowMapper<InventoryBalanceResponse> ROW_MAPPER =
            InventoryBalanceQueryService::mapRow;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InventoryBalanceQueryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryBalanceResponse> page(PageQuery query,
                                                       String keyword,
                                                       Long warehouseId,
                                                       Long materialId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = buildWhere(keyword, warehouseId, materialId, params);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT 1" + BASE_FROM + where + GROUP_BY + ") grouped",
                params,
                Long.class
        );
        long totalElements = total == null ? 0L : total;
        String dataSql = "SELECT t.material_id, t.material_code, t.warehouse_id, t.warehouse_name, t.batch_no,"
                + " COALESCE(SUM(t.quantity * t.direction), 0) AS quantity,"
                + " COALESCE(SUM(t.amount), 0) AS amount,"
                + " CASE WHEN COALESCE(SUM(t.quantity * t.direction), 0) <> 0"
                + " THEN ROUND(COALESCE(SUM(t.amount), 0) / SUM(t.quantity * t.direction), 2)"
                + " ELSE 0 END AS avg_unit_cost"
                + BASE_FROM + where + GROUP_BY + buildOrderBy(query)
                + " LIMIT :limit OFFSET :offset";
        params.addValue("limit", query.size());
        params.addValue("offset", (long) query.page() * query.size());
        List<InventoryBalanceResponse> content = jdbcTemplate.query(dataSql, params, ROW_MAPPER);
        int totalPages = query.size() <= 0 ? 0 : (int) ((totalElements + query.size() - 1) / query.size());
        boolean hasMore = (long) (query.page() + 1) * query.size() < totalElements;
        return new PageResponse<>(content, totalElements, totalPages, query.page(), query.size(), hasMore);
    }

    private String buildWhere(String keyword, Long warehouseId, Long materialId, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder();
        if (materialId != null) {
            where.append(" AND t.material_id = :materialId");
            params.addValue("materialId", materialId);
        }
        if (warehouseId != null) {
            where.append(" AND t.warehouse_id = :warehouseId");
            params.addValue("warehouseId", warehouseId);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (t.material_code ILIKE :keyword"
                    + " OR t.warehouse_name ILIKE :keyword"
                    + " OR t.batch_no ILIKE :keyword)");
            params.addValue("keyword", likePattern(keyword));
        }
        return where.toString();
    }

    private String buildOrderBy(PageQuery query) {
        String expression = query.sortBy() == null ? null : SORT_EXPRESSIONS.get(query.sortBy());
        String direction = "asc".equalsIgnoreCase(query.direction()) ? "ASC" : "DESC";
        if (expression == null) {
            return " ORDER BY t.material_id ASC" + TIE_BREAKER;
        }
        return " ORDER BY " + expression + " " + direction + TIE_BREAKER;
    }

    private String likePattern(String keyword) {
        String escaped = keyword.trim()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static InventoryBalanceResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new InventoryBalanceResponse(
                nullableLong(rs, "material_id"),
                rs.getString("material_code"),
                nullableLong(rs, "warehouse_id"),
                rs.getString("warehouse_name"),
                rs.getString("batch_no"),
                rs.getLong("quantity"),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("avg_unit_cost")
        );
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
