package com.leo.erp.inventory.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.inventory.repository.InventoryBalanceSnapshotRepository;
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
 * 库存余额查询：直接读取 inv_balance 增量快照，不再对 inv_transaction 做整体聚合。
 *
 * <p>{@link #page} 保留 OFFSET 分页以兼容既有控制器契约；同时提供
 * {@link #keyset} 基于 (material_id, warehouse_id) 复合主键游标分页，
 * 供列表改造成深分页安全的接口时使用。
 */
@Service
public class InventoryBalanceQueryService {

    private static final String BASE_FROM = " FROM inv_balance b"
            + " LEFT JOIN md_material m ON m.id = b.material_id"
            + " WHERE b.deleted_flag = false";
    private static final String SELECT_LIST = "SELECT b.material_id, b.material_code,"
            + " m.brand, m.material, m.spec, m.length, m.unit,"
            + " b.warehouse_id, b.warehouse_name, b.batch_no,"
            + " b.quantity AS quantity, b.amount AS amount,"
            + " CASE WHEN b.quantity <> 0 THEN ROUND(b.amount / b.quantity, 2) ELSE 0 END AS avg_unit_cost";
    private static final String DEFAULT_ORDER = " ORDER BY b.material_id ASC, b.warehouse_id ASC";
    private static final String KEYSET_PREDICATE =
            " AND (b.material_id, b.warehouse_id) > (:afterMaterialId, :afterWarehouseId)";
    private static final Map<String, String> SORT_EXPRESSIONS = Map.of(
            "materialId", "b.material_id",
            "materialCode", "b.material_code",
            "warehouseId", "b.warehouse_id",
            "warehouseName", "b.warehouse_name",
            "batchNo", "b.batch_no",
            "quantity", "b.quantity",
            "amount", "b.amount",
            "avgUnitCost", "avg_unit_cost"
    );
    private static final String TIE_BREAKER = ", b.material_id ASC, b.warehouse_id ASC";
    private static final int MAX_SIZE = 200;
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
                "SELECT COUNT(*) FROM inv_balance b WHERE b.deleted_flag = false" + where,
                params,
                Long.class
        );
        long totalElements = total == null ? 0L : total;
        String dataSql = SELECT_LIST + BASE_FROM + where + buildOrderBy(query)
                + " LIMIT :limit OFFSET :offset";
        params.addValue("limit", query.size());
        params.addValue("offset", (long) query.page() * query.size());
        List<InventoryBalanceResponse> content = jdbcTemplate.query(dataSql, params, ROW_MAPPER);
        int totalPages = query.size() <= 0 ? 0 : (int) ((totalElements + query.size() - 1) / query.size());
        boolean hasMore = (long) (query.page() + 1) * query.size() < totalElements;
        return new PageResponse<>(content, totalElements, totalPages, query.page(), query.size(), hasMore);
    }

    /**
     * 基于 (material_id, warehouse_id) 复合主键的 keyset 游标分页，避免深 OFFSET。
     *
     * @param afterMaterialId 上一页末位 material_id，null 表示从第一页开始
     * @param afterWarehouseId 上一页末位 warehouse_id，仅在 afterMaterialId 非空时参与游标
     * @param size 页大小，收敛到 [1, 200]
     */
    @Transactional(readOnly = true)
    public InventoryBalanceCursorPage keyset(String keyword,
                                             Long warehouseId,
                                             Long materialId,
                                             Long afterMaterialId,
                                             Long afterWarehouseId,
                                             int size) {
        int limit = Math.max(1, Math.min(size, MAX_SIZE));
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = buildWhere(keyword, warehouseId, materialId, params);
        String keysetPredicate = "";
        if (afterMaterialId != null) {
            keysetPredicate = KEYSET_PREDICATE;
            params.addValue("afterMaterialId", afterMaterialId);
            params.addValue("afterWarehouseId", afterWarehouseId == null
                    ? InventoryBalanceSnapshotRepository.NO_WAREHOUSE
                    : afterWarehouseId);
        }
        String dataSql = SELECT_LIST + BASE_FROM + where + keysetPredicate + DEFAULT_ORDER
                + " LIMIT :limit";
        params.addValue("limit", limit + 1);
        List<InventoryBalanceResponse> rows = jdbcTemplate.query(dataSql, params, ROW_MAPPER);
        boolean hasMore = rows.size() > limit;
        List<InventoryBalanceResponse> content = hasMore ? List.copyOf(rows.subList(0, limit)) : rows;
        Long nextMaterialId = null;
        Long nextWarehouseId = null;
        if (!content.isEmpty()) {
            InventoryBalanceResponse last = content.get(content.size() - 1);
            nextMaterialId = last.materialId();
            nextWarehouseId = last.warehouseId();
        }
        return new InventoryBalanceCursorPage(content, hasMore, nextMaterialId, nextWarehouseId);
    }

    private String buildWhere(String keyword, Long warehouseId, Long materialId, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder();
        if (materialId != null) {
            where.append(" AND b.material_id = :materialId");
            params.addValue("materialId", materialId);
        }
        if (warehouseId != null) {
            where.append(" AND b.warehouse_id = :warehouseId");
            params.addValue("warehouseId", warehouseId);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (b.material_code ILIKE :keyword"
                    + " OR b.warehouse_name ILIKE :keyword"
                    + " OR b.batch_no ILIKE :keyword)");
            params.addValue("keyword", likePattern(keyword));
        }
        return where.toString();
    }

    private String buildOrderBy(PageQuery query) {
        String expression = query.sortBy() == null ? null : SORT_EXPRESSIONS.get(query.sortBy());
        String direction = "asc".equalsIgnoreCase(query.direction()) ? "ASC" : "DESC";
        if (expression == null) {
            return DEFAULT_ORDER;
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
                rs.getString("brand"),
                rs.getString("material"),
                rs.getString("spec"),
                rs.getString("length"),
                rs.getString("unit"),
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
