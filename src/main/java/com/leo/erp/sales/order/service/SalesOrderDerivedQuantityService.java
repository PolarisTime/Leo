package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 销售订单派生数量实时聚合：只读统计已审核销售出库与销售退货数量，不落字段。
 * 订单级与明细级均通过批量 JDBC 聚合避免 N+1。
 */
@Service
public class SalesOrderDerivedQuantityService {

    private static final String ITEM_DELIVERED_SQL = """
            SELECT item.source_sales_order_item_id AS item_id,
                   COALESCE(SUM(item.quantity), 0) AS total_quantity
              FROM so_sales_outbound_item item
              JOIN so_sales_outbound outbound
                ON outbound.id = item.outbound_id
               AND outbound.deleted_flag = FALSE
               AND outbound.status = :audited
             WHERE item.source_sales_order_item_id IN (:ids)
             GROUP BY item.source_sales_order_item_id
            """;
    private static final String ITEM_OUTBOUND_RESERVED_SQL = """
            SELECT item.source_sales_order_item_id AS item_id,
                   COALESCE(SUM(item.quantity), 0) AS total_quantity
              FROM so_sales_outbound_item item
              JOIN so_sales_outbound outbound
                ON outbound.id = item.outbound_id
               AND outbound.deleted_flag = FALSE
             WHERE item.source_sales_order_item_id IN (:ids)
             GROUP BY item.source_sales_order_item_id
            """;
    private static final String ITEM_RETURNED_SQL = """
            SELECT item.source_sales_order_item_id AS item_id,
                   COALESCE(SUM(item.quantity), 0) AS total_quantity
              FROM so_sales_return_item item
              JOIN so_sales_return ret
                ON ret.id = item.return_id
               AND ret.deleted_flag = FALSE
               AND ret.status = :audited
             WHERE item.source_sales_order_item_id IN (:ids)
             GROUP BY item.source_sales_order_item_id
            """;
    private static final String ORDER_DELIVERED_SQL = """
            SELECT order_item.order_id AS order_id,
                   COALESCE(SUM(outbound_item.quantity), 0) AS total_quantity
              FROM so_sales_order_item order_item
              JOIN so_sales_outbound_item outbound_item
                ON outbound_item.source_sales_order_item_id = order_item.id
              JOIN so_sales_outbound outbound
                ON outbound.id = outbound_item.outbound_id
               AND outbound.deleted_flag = FALSE
               AND outbound.status = :audited
             WHERE order_item.order_id IN (:ids)
             GROUP BY order_item.order_id
            """;
    private static final String ORDER_RETURNED_SQL = """
            SELECT order_item.order_id AS order_id,
                   COALESCE(SUM(return_item.quantity), 0) AS total_quantity
              FROM so_sales_order_item order_item
              JOIN so_sales_return_item return_item
                ON return_item.source_sales_order_item_id = order_item.id
              JOIN so_sales_return ret
                ON ret.id = return_item.return_id
               AND ret.deleted_flag = FALSE
               AND ret.status = :audited
             WHERE order_item.order_id IN (:ids)
             GROUP BY order_item.order_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SalesOrderDerivedQuantityService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Map<Long, Quantities> itemQuantities(Collection<Long> salesOrderItemIds) {
        Set<Long> ids = normalize(salesOrderItemIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return combine(
                sumByKey(ITEM_DELIVERED_SQL, "item_id", ids),
                sumByKey(ITEM_RETURNED_SQL, "item_id", ids));
    }

    /**
     * 订单明细级“未删除出库已占用数量”：包含草稿出库在内的所有未删除销售出库数量之和。
     * 用于计算剩余可出库数量，与销售出库累计覆盖校验口径一致。
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> reservedOutboundQuantities(Collection<Long> salesOrderItemIds) {
        Set<Long> ids = normalize(salesOrderItemIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return sumByKey(ITEM_OUTBOUND_RESERVED_SQL, "item_id", ids);
    }

    @Transactional(readOnly = true)
    public Map<Long, Quantities> orderQuantities(Collection<Long> salesOrderIds) {
        Set<Long> ids = normalize(salesOrderIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return combine(
                sumByKey(ORDER_DELIVERED_SQL, "order_id", ids),
                sumByKey(ORDER_RETURNED_SQL, "order_id", ids));
    }

    private Map<Long, Integer> sumByKey(String sql, String keyColumn, Collection<Long> ids) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("ids", ids)
                .addValue("audited", StatusConstants.AUDITED);
        Map<Long, Integer> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, parameters, (org.springframework.jdbc.core.RowCallbackHandler) resultSet ->
                result.put(resultSet.getLong(keyColumn), resultSet.getInt("total_quantity")));
        return result;
    }

    private Map<Long, Quantities> combine(Map<Long, Integer> delivered, Map<Long, Integer> returned) {
        Set<Long> keys = new LinkedHashSet<>(delivered.keySet());
        keys.addAll(returned.keySet());
        Map<Long, Quantities> result = new LinkedHashMap<>(keys.size());
        for (Long key : keys) {
            int deliveredValue = delivered.getOrDefault(key, 0);
            int returnedValue = returned.getOrDefault(key, 0);
            result.put(key, new Quantities(deliveredValue, returnedValue, deliveredValue - returnedValue));
        }
        return result;
    }

    private Set<Long> normalize(Collection<Long> ids) {
        Set<Long> result = new LinkedHashSet<>();
        if (ids != null) {
            ids.stream().filter(java.util.Objects::nonNull).forEach(result::add);
        }
        return result;
    }

    public record Quantities(Integer deliveredQuantity, Integer returnedQuantity, Integer deliveredNetQuantity) {

        public static final Quantities ZERO = new Quantities(0, 0, 0);

        public static Quantities of(Integer delivered, Integer returned) {
            int deliveredValue = delivered == null ? 0 : delivered;
            int returnedValue = returned == null ? 0 : returned;
            return new Quantities(deliveredValue, returnedValue, deliveredValue - returnedValue);
        }
    }
}
