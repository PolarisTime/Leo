package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SalesOutboundPurchaseInboundGuard {

    private static final String AUDIT_BLOCK_MESSAGE =
            "来源采购明细尚未完成采购入库，不能直接审核销售出库。请先将销售出库保存为预出库，待采购入库过磅同步重量后再审核。";

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
        List<Long> sourcePurchaseOrderItemIds = outbound.getItems().stream()
                .map(SalesOutboundItem::getSourceSalesOrderItemId)
                .map(sourceSalesOrderItemMap::get)
                .filter(Objects::nonNull)
                .map(SalesOrderItem::getSourcePurchaseOrderItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        for (Long sourcePurchaseOrderItemId : sourcePurchaseOrderItemIds) {
            if (!hasCompletedPurchaseInbound(sourcePurchaseOrderItemId)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, AUDIT_BLOCK_MESSAGE);
            }
        }
    }

    private boolean hasCompletedPurchaseInbound(Long sourcePurchaseOrderItemId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(1)
                  FROM po_purchase_inbound_item item
                  JOIN po_purchase_inbound inbound ON inbound.id = item.inbound_id
                 WHERE inbound.deleted_flag = FALSE
                   AND item.source_purchase_order_item_id = ?
                   AND inbound.status IN ('已审核', '完成入库')
                """, Long.class, sourcePurchaseOrderItemId);
        return count != null && count > 0;
    }
}
