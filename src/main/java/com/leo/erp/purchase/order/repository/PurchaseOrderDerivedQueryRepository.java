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
 * 采购订单派生聚合批量查询(入库进度 / 实际货值 / 差额), 避免列表页按行查询产生 N+1。
 *
 * <p>「实际货值」按明细逐行四舍五入到分后求和, 与暂定金额同口径: total_amount 是各行
 * amount(numeric(14,2)) 之和再加费用, 若实际货值改为整单原始乘积求和, 同一批重量会因
 * 行级取整产生 ±0.01 的伪差额(实测重量与暂定重量完全一致时也会显示"退 0.01")。</p>
 *
 * <p>「实际货值 / 差额」只在存在已过磅行时才有值: 未过磅行不产生实际货值, 也不产生补退。
 * 否则一行都未入库的订单会显示成"退掉整单暂定金额"(实为已付预付款, 并非退款)。</p>
 */
@Repository
public class PurchaseOrderDerivedQueryRepository {

    private static final String SQL = """
            SELECT po.id,
                   -- 强制结单: 剩余未入库件数已作废, 对外按 0 返回(不再作为待入库量展示)
                   CASE WHEN po.force_closed THEN 0 ELSE COALESCE((
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
                   ), 0) END AS unreceived_quantity,
                   COALESCE((
                       SELECT SUM(pii.quantity)
                         FROM po_purchase_inbound_item pii
                         JOIN po_purchase_inbound inbound
                           ON inbound.id = pii.inbound_id
                          AND inbound.deleted_flag = FALSE
                        WHERE pii.source_purchase_order_item_id IN (
                            SELECT poi.id
                              FROM po_purchase_order_item poi
                             WHERE poi.order_id = po.id
                        )
                   ), 0) AS received_quantity,
                   -- 实际货值: 仅统计已过磅行; 一行都未过磅时返回 NULL(未产生实际货值, 前端显示 —),
                   -- 否则"0 − 暂定金额"会把整单预付款显示成一笔退款。
                   (SELECT CASE WHEN COUNT(poi.actual_weight_ton) = 0 THEN NULL
                                ELSE SUM(ROUND(poi.actual_weight_ton * poi.unit_price, 2)) END
                      FROM po_purchase_order_item poi
                     WHERE poi.order_id = po.id) AS actual_amount,
                   -- 差额(补退): 只对已过磅行按行结算(行实际金额 − 行暂定金额);
                   -- 无过磅行时为 NULL, 未入库/未过磅的货不产生补退。
                   -- 全部过磅且无附加费用时等价于 实际货值 − 暂定金额。
                   (SELECT CASE WHEN COUNT(poi.actual_weight_ton) = 0 THEN NULL
                                ELSE SUM(ROUND(poi.actual_weight_ton * poi.unit_price, 2)
                                         - COALESCE(poi.amount, 0)) END
                      FROM po_purchase_order_item poi
                     WHERE poi.order_id = po.id) AS amount_difference
              FROM po_purchase_order po
             WHERE po.id IN (:ids)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PurchaseOrderDerivedQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<Long, DerivedStatus> findByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource("ids", orderIds);
        List<DerivedStatus> statuses = jdbcTemplate.query(SQL, parameters, (resultSet, rowNum) ->
                new DerivedStatus(
                        resultSet.getLong("id"),
                        saturateToInt(resultSet.getLong("unreceived_quantity")),
                        saturateToInt(resultSet.getLong("received_quantity")),
                        resultSet.getBigDecimal("actual_amount"),
                        resultSet.getBigDecimal("amount_difference")
                ));
        Map<Long, DerivedStatus> result = new HashMap<>(statuses.size());
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

    /**
     * 单个采购订单的派生聚合: 未入库件数 / 已入库件数 / 实际货值 / 差额。
     *
     * <p>入库进度、实际货值与差额的唯一来源: 列表与详情都取这份聚合结果。</p>
     */
    public record DerivedStatus(
            Long orderId,
            int unreceivedQuantity,
            int receivedQuantity,
            BigDecimal actualAmount,
            BigDecimal amountDifference
    ) {
    }
}
