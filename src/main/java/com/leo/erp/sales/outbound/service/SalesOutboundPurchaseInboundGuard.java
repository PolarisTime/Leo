package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SalesOutboundPurchaseInboundGuard {

    private static final String AUDIT_BLOCK_MESSAGE =
            "来源采购明细尚未完成采购入库，不能审核销售出库。请先完成采购入库后重试。";

    private final SalesOutboundSourceService sourceService;
    private final JdbcTemplate jdbc;

    public SalesOutboundPurchaseInboundGuard(SalesOutboundSourceService sourceService,
                                             JdbcTemplate jdbc) {
        this.sourceService = sourceService;
        this.jdbc = jdbc;
    }

    void assertPurchaseInboundCompletedBeforeAudit(SalesOutbound outbound) {
        if (outbound == null || outbound.getItems() == null || outbound.getItems().isEmpty()) {
            return;
        }
        Map<Long, SalesOrderItem> sourceSalesOrderItemMap =
                sourceService.loadSourceSalesOrderItemMap(outbound.getItems());
        Map<Long, Integer> requiredQuantityByPurchaseOrderItemId = outbound.getItems().stream()
                .map(item -> toPurchaseRequirement(item, sourceSalesOrderItemMap))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        PurchaseRequirement::purchaseOrderItemId,
                        PurchaseRequirement::quantity,
                        Integer::sum
                ));
        for (Map.Entry<Long, Integer> requirement : requiredQuantityByPurchaseOrderItemId.entrySet()) {
            PurchaseInboundCoverage coverage = loadCoverage(requirement.getKey());
            if (!StatusConstants.PURCHASE_COMPLETED.equals(coverage.purchaseOrderStatus())
                    || coverage.inboundQuantity() < requirement.getValue()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, AUDIT_BLOCK_MESSAGE);
            }
        }
    }

    private PurchaseRequirement toPurchaseRequirement(
            SalesOutboundItem outboundItem,
            Map<Long, SalesOrderItem> sourceSalesOrderItemMap
    ) {
        SalesOrderItem salesOrderItem = sourceSalesOrderItemMap.get(outboundItem.getSourceSalesOrderItemId());
        if (salesOrderItem == null || salesOrderItem.getSourcePurchaseOrderItemId() == null) {
            return null;
        }
        return new PurchaseRequirement(
                salesOrderItem.getSourcePurchaseOrderItemId(),
                outboundItem.getQuantity() == null ? 0 : outboundItem.getQuantity()
        );
    }

    private PurchaseInboundCoverage loadCoverage(Long sourcePurchaseOrderItemId) {
        List<PurchaseInboundCoverage> rows = jdbc.query("""
                    SELECT source_order.status,
                           COALESCE(SUM(
                               CASE WHEN inbound.status IN ('已审核', '完成入库')
                                    THEN inbound_item.quantity ELSE 0 END
                           ), 0) AS inbound_quantity
                      FROM po_purchase_order_item source_item
                      JOIN po_purchase_order source_order ON source_order.id = source_item.order_id
                 LEFT JOIN po_purchase_inbound_item inbound_item
                        ON inbound_item.source_purchase_order_item_id = source_item.id
                 LEFT JOIN po_purchase_inbound inbound
                        ON inbound.id = inbound_item.inbound_id
                       AND inbound.deleted_flag = FALSE
                     WHERE source_item.id = ?
                       AND source_order.deleted_flag = FALSE
                  GROUP BY source_order.status
                    """, (rs, rowNum) -> new PurchaseInboundCoverage(
                        rs.getString("status"),
                        rs.getInt("inbound_quantity")
                ), sourcePurchaseOrderItemId);
        return rows.isEmpty() ? new PurchaseInboundCoverage(null, 0) : rows.get(0);
    }

    private record PurchaseRequirement(Long purchaseOrderItemId, int quantity) {
    }

    private record PurchaseInboundCoverage(String purchaseOrderStatus, int inboundQuantity) {
    }
}
