package com.leo.erp.purchase.order.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购订单下游引用状态批量查询，避免列表页按行查询产生 N+1。
 */
@Repository
public class PurchaseOrderReferenceQueryRepository {

    private static final String SQL = """
            SELECT po.id,
                   EXISTS (
                       SELECT 1
                         FROM po_purchase_order_item poi
                         JOIN so_sales_order_item soi
                           ON soi.source_purchase_order_item_id = poi.id
                         JOIN so_sales_order so
                           ON so.id = soi.order_id
                          AND so.deleted_flag = FALSE
                        WHERE poi.order_id = po.id
                   )
                   OR EXISTS (
                       SELECT 1
                         FROM so_sales_order_item soi
                         JOIN so_sales_order so
                           ON so.id = soi.order_id
                          AND so.deleted_flag = FALSE
                         JOIN po_purchase_inbound_item pii
                           ON pii.id = soi.source_inbound_item_id
                         JOIN po_purchase_order_item poi
                           ON poi.id = pii.source_purchase_order_item_id
                        WHERE poi.order_id = po.id
                   ) AS referenced_by_sales_order,
                   EXISTS (
                       SELECT 1
                         FROM po_purchase_order_item poi
                         JOIN po_purchase_inbound_item pii
                           ON pii.source_purchase_order_item_id = poi.id
                         JOIN po_purchase_inbound inbound
                           ON inbound.id = pii.inbound_id
                          AND inbound.deleted_flag = FALSE
                        WHERE poi.order_id = po.id
                   ) AS referenced_by_purchase_inbound,
                   COALESCE((
                       SELECT SUM(GREATEST(poi.quantity - COALESCE((
                           SELECT SUM(pii.quantity)
                             FROM po_purchase_inbound_item pii
                             JOIN po_purchase_inbound inbound
                               ON inbound.id = pii.inbound_id
                              AND inbound.deleted_flag = FALSE
                             WHERE pii.source_purchase_order_item_id = poi.id
                       ), 0), 0))
                         FROM po_purchase_order_item poi
                        WHERE poi.order_id = po.id
                   ), 0) AS unreceived_quantity,
                   COALESCE((
                       SELECT SUM(poi.actual_weight_ton * poi.unit_price)
                         FROM po_purchase_order_item poi
                        WHERE poi.order_id = po.id
                          AND poi.actual_weight_ton IS NOT NULL
                   ), 0) AS actual_amount,
                   COALESCE((
                       SELECT SUM(poi.actual_weight_ton * poi.unit_price)
                         FROM po_purchase_order_item poi
                        WHERE poi.order_id = po.id
                          AND poi.actual_weight_ton IS NOT NULL
                   ), 0) - COALESCE(po.total_amount, 0) AS amount_difference
              FROM po_purchase_order po
             WHERE po.id IN (:ids)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PurchaseOrderReferenceQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<Long, ReferenceStatus> findByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource("ids", orderIds);
        List<ReferenceStatus> statuses = jdbcTemplate.query(SQL, parameters, (resultSet, rowNum) ->
                new ReferenceStatus(
                        resultSet.getLong("id"),
                        resultSet.getBoolean("referenced_by_sales_order"),
                        resultSet.getBoolean("referenced_by_purchase_inbound"),
                        saturateToInt(resultSet.getLong("unreceived_quantity")),
                        resultSet.getBigDecimal("actual_amount"),
                        resultSet.getBigDecimal("amount_difference")
                ));
        Map<Long, ReferenceStatus> result = new HashMap<>(statuses.size());
        statuses.forEach(status -> result.put(status.orderId(), status));
        return result;
    }

    /** 聚合值超出 int 时收敛到 {@link Integer#MAX_VALUE}，避免读取时溢出。 */
    private static int saturateToInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return value < 0 ? 0 : (int) value;
    }

    public record ReferenceStatus(
            Long orderId,
            boolean referencedBySalesOrder,
            boolean referencedByPurchaseInbound,
            int unreceivedQuantity,
            BigDecimal actualAmount,
            BigDecimal amountDifference
    ) {
    }
}
