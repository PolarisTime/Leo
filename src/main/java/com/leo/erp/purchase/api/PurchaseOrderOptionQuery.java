package com.leo.erp.purchase.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 采购订单只读选项查询端口, 供比价报价单关联采购订单并展示订货/已开/剩余吨位。
 * <p>实现由 purchase 模块提供, 调用方无需接触采购实体或 Repository。</p>
 */
public interface PurchaseOrderOptionQuery {

    /** 按关键字(单号/供应商)与状态查询未删除采购订单选项(按 id 倒序)。 */
    List<PurchaseOrderOptionSnapshot> listActiveOptions(String keyword, String status);

    /** 按 id 批量查询未删除采购订单快照(不存在或已删除的 id 不返回)。 */
    List<PurchaseOrderOptionSnapshot> listActiveByIds(Collection<Long> ids);

    record PurchaseOrderOptionSnapshot(
            Long id,
            String orderNo,
            String supplierName,
            BigDecimal totalWeight,
            String status,
            LocalDateTime orderDate
    ) {
    }
}
