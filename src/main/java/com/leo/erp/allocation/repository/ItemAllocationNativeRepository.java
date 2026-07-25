package com.leo.erp.allocation.repository;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 共享的明细分配只读查询 Repository，仅依赖 source_*_item_id 列，
 * 不引用任何业务模块实体，消除 purchase ↔ sales 循环依赖。
 */
@Repository
public class ItemAllocationNativeRepository {

    private static final String SALES_BY_PURCHASE_ORDER_ITEMS_SQL = """
            SELECT si.source_purchase_order_item_id AS source_item_id,
                   SUM(si.quantity)                  AS total_quantity,
                   COALESCE(SUM(si.weight_ton), 0)   AS total_weight_ton
              FROM so_sales_order_item si
              JOIN so_sales_order so ON so.id = si.order_id AND so.deleted_flag = FALSE
             WHERE si.source_purchase_order_item_id IN (:ids)
               AND (:exclude_order_id IS NULL OR si.order_id <> :exclude_order_id)
             GROUP BY si.source_purchase_order_item_id
            """;

    private static final String INBOUND_BY_PURCHASE_ORDER_ITEMS_SQL = """
            SELECT pi.source_purchase_order_item_id AS source_item_id,
                   SUM(pi.quantity)                  AS total_quantity,
                   COALESCE(SUM(pi.weight_ton), 0)   AS total_weight_ton
              FROM po_purchase_inbound_item pi
              JOIN po_purchase_inbound inbound ON inbound.id = pi.inbound_id AND inbound.deleted_flag = FALSE
             WHERE pi.source_purchase_order_item_id IN (:ids)
               AND (:exclude_inbound_id IS NULL OR pi.inbound_id <> :exclude_inbound_id)
             GROUP BY pi.source_purchase_order_item_id
            """;

    private static final String SALES_BY_INBOUND_ITEMS_SQL = """
            SELECT si.source_inbound_item_id         AS source_item_id,
                   SUM(si.quantity)                  AS total_quantity,
                   COALESCE(SUM(si.weight_ton), 0)   AS total_weight_ton
              FROM so_sales_order_item si
              JOIN so_sales_order so ON so.id = si.order_id AND so.deleted_flag = FALSE
             WHERE si.source_inbound_item_id IN (:ids)
               AND (:exclude_order_id IS NULL OR si.order_id <> :exclude_order_id)
             GROUP BY si.source_inbound_item_id
            """;

    private static final String WEIGHT_ADJUSTMENT_BY_PURCHASE_ORDER_ITEMS_SQL = """
            SELECT pi.source_purchase_order_item_id AS source_item_id,
                   COALESCE(SUM(pi.weight_adjustment_ton), 0) AS total_weight_ton
              FROM po_purchase_inbound_item pi
              JOIN po_purchase_inbound inbound ON inbound.id = pi.inbound_id AND inbound.deleted_flag = FALSE
             WHERE pi.source_purchase_order_item_id IN (:ids)
             GROUP BY pi.source_purchase_order_item_id
            """;

    private static final RowMapper<AllocationProjection> ALLOCATION_ROW_MAPPER = (resultSet, rowNumber) ->
            new AllocationSummary(
                    resultSet.getObject("source_item_id", Long.class),
                    resultSet.getObject("total_quantity", Long.class),
                    resultSet.getBigDecimal("total_weight_ton")
            );

    private static final RowMapper<AllocationProjection> WEIGHT_ADJUSTMENT_ROW_MAPPER =
            (resultSet, rowNumber) -> new AllocationSummary(
                    resultSet.getObject("source_item_id", Long.class),
                    null,
                    resultSet.getBigDecimal("total_weight_ton")
            );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ItemAllocationNativeRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 查询指定采购订单明细在销售订单中的分配量 */
    public List<AllocationProjection> summarizeSalesByPurchaseOrderItems(
            Collection<Long> ids,
            Long excludeOrderId
    ) {
        return queryAllocations(SALES_BY_PURCHASE_ORDER_ITEMS_SQL, ids, "exclude_order_id", excludeOrderId);
    }

    /** 查询指定采购订单明细在采购入库中的分配量 */
    public List<AllocationProjection> summarizeInboundByPurchaseOrderItems(
            Collection<Long> ids,
            Long excludeInboundId
    ) {
        return queryAllocations(
                INBOUND_BY_PURCHASE_ORDER_ITEMS_SQL,
                ids,
                "exclude_inbound_id",
                excludeInboundId
        );
    }

    /** 查询指定采购入库明细在销售订单中的分配量 */
    public List<AllocationProjection> summarizeSalesByInboundItems(
            Collection<Long> ids,
            Long excludeOrderId
    ) {
        return queryAllocations(SALES_BY_INBOUND_ITEMS_SQL, ids, "exclude_order_id", excludeOrderId);
    }

    /** 查询指定采购订单明细的重量调整量 */
    public List<AllocationProjection> summarizeWeightAdjustmentByPurchaseOrderItems(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                WEIGHT_ADJUSTMENT_BY_PURCHASE_ORDER_ITEMS_SQL,
                new MapSqlParameterSource("ids", ids),
                WEIGHT_ADJUSTMENT_ROW_MAPPER
        );
    }

    private List<AllocationProjection> queryAllocations(
            String sql,
            Collection<Long> ids,
            String exclusionParameter,
            Long exclusionId
    ) {
        if (ids.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("ids", ids)
                .addValue(exclusionParameter, exclusionId, Types.BIGINT);
        return jdbcTemplate.query(sql, parameters, ALLOCATION_ROW_MAPPER);
    }

    public interface AllocationProjection {
        Long getSourceItemId();

        Long getTotalQuantity();

        BigDecimal getTotalWeightTon();
    }

    private record AllocationSummary(
            Long sourceItemId,
            Long totalQuantity,
            BigDecimal totalWeightTon
    ) implements AllocationProjection {

        @Override
        public Long getSourceItemId() {
            return sourceItemId;
        }

        @Override
        public Long getTotalQuantity() {
            return totalQuantity;
        }

        @Override
        public BigDecimal getTotalWeightTon() {
            return totalWeightTon;
        }
    }
}
