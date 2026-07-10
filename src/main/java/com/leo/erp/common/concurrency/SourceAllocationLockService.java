package com.leo.erp.common.concurrency;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

@Service
public class SourceAllocationLockService {

    private static final String LOCK_PURCHASE_ORDER_ITEMS_SQL = """
            SELECT source_item.id
            FROM po_purchase_order source_parent
            JOIN po_purchase_order_item source_item ON source_item.order_id = source_parent.id
            WHERE source_item.id IN (:sourceIds)
            ORDER BY source_parent.id, source_item.id
            FOR UPDATE OF source_parent, source_item
            """;
    private static final String LOCK_PURCHASE_INBOUND_ITEMS_SQL = """
            SELECT source_item.id
            FROM po_purchase_inbound source_parent
            JOIN po_purchase_inbound_item source_item ON source_item.inbound_id = source_parent.id
            WHERE source_item.id IN (:sourceIds)
            ORDER BY source_parent.id, source_item.id
            FOR UPDATE OF source_parent, source_item
            """;
    private static final String LOCK_SALES_ORDER_ITEMS_SQL = """
            SELECT source_item.id
            FROM so_sales_order source_parent
            JOIN so_sales_order_item source_item ON source_item.order_id = source_parent.id
            WHERE source_item.id IN (:sourceIds)
            ORDER BY source_parent.id, source_item.id
            FOR UPDATE OF source_parent, source_item
            """;
    private static final String LOCK_PURCHASE_INBOUNDS_SQL = lockDocumentSql("po_purchase_inbound");
    private static final String LOCK_SALES_ORDERS_SQL = lockDocumentSql("so_sales_order");
    private static final String LOCK_SALES_OUTBOUNDS_SQL = lockDocumentSql("so_sales_outbound");
    private static final String LOCK_FREIGHT_BILLS_SQL = lockDocumentSql("lg_freight_bill");
    private static final String LOCK_CUSTOMER_STATEMENTS_SQL = lockDocumentSql("st_customer_statement");
    private static final String LOCK_SUPPLIER_STATEMENTS_SQL = lockDocumentSql("st_supplier_statement");
    private static final String LOCK_FREIGHT_STATEMENTS_SQL = lockDocumentSql("st_freight_statement");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SourceAllocationLockService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockTradeItemSources(Collection<Long> purchaseOrderItemIds,
                                     Collection<Long> purchaseInboundItemIds,
                                     Collection<Long> salesOrderItemIds) {
        lockRows(LOCK_PURCHASE_ORDER_ITEMS_SQL, purchaseOrderItemIds, "采购订单明细");
        lockRows(LOCK_PURCHASE_INBOUND_ITEMS_SQL, purchaseInboundItemIds, "采购入库明细");
        lockRows(LOCK_SALES_ORDER_ITEMS_SQL, salesOrderItemIds, "销售订单明细");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockDocumentSources(Collection<Long> purchaseInboundIds,
                                    Collection<Long> salesOrderIds,
                                    Collection<Long> salesOutboundIds,
                                    Collection<Long> freightBillIds) {
        lockRows(LOCK_PURCHASE_INBOUNDS_SQL, purchaseInboundIds, "采购入库单");
        lockRows(LOCK_SALES_ORDERS_SQL, salesOrderIds, "销售订单");
        lockRows(LOCK_SALES_OUTBOUNDS_SQL, salesOutboundIds, "销售出库单");
        lockRows(LOCK_FREIGHT_BILLS_SQL, freightBillIds, "物流单");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockStatementSources(Collection<Long> customerStatementIds,
                                     Collection<Long> supplierStatementIds,
                                     Collection<Long> freightStatementIds) {
        lockRows(LOCK_CUSTOMER_STATEMENTS_SQL, customerStatementIds, "客户对账单");
        lockRows(LOCK_SUPPLIER_STATEMENTS_SQL, supplierStatementIds, "供应商对账单");
        lockRows(LOCK_FREIGHT_STATEMENTS_SQL, freightStatementIds, "物流对账单");
    }

    private void lockRows(String sql, Collection<Long> sourceIds, String sourceName) {
        List<Long> normalizedIds = normalizeIds(sourceIds);
        if (normalizedIds.isEmpty()) {
            return;
        }
        List<Long> lockedIds = jdbcTemplate.queryForList(
                sql,
                new MapSqlParameterSource("sourceIds", normalizedIds),
                Long.class
        ).stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (!lockedIds.equals(normalizedIds)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, sourceName + "不存在或已失效");
        }
    }

    private List<Long> normalizeIds(Collection<Long> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return List.of();
        }
        TreeSet<Long> orderedIds = new TreeSet<>();
        sourceIds.stream().filter(Objects::nonNull).forEach(orderedIds::add);
        return List.copyOf(orderedIds);
    }

    private static String lockDocumentSql(String tableName) {
        return """
                SELECT source_record.id
                FROM %s source_record
                WHERE source_record.id IN (:sourceIds)
                ORDER BY source_record.id
                FOR UPDATE OF source_record
                """.formatted(tableName);
    }
}
