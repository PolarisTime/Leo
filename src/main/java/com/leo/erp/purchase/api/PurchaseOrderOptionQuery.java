package com.leo.erp.purchase.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 采购订单只读选项查询端口, 供比价报价单关联采购订单/订单明细行并展示订货/已开/剩余吨位。
 * <p>实现由 purchase 模块提供, 调用方无需接触采购实体或 Repository。</p>
 */
public interface PurchaseOrderOptionQuery {

    /** 按关键字(单号/供应商)与状态查询未删除采购订单选项(按 id 倒序)。 */
    List<PurchaseOrderOptionSnapshot> listActiveOptions(String keyword, String status);

    /** 按 id 批量查询未删除采购订单快照(不存在或已删除的 id 不返回)。 */
    List<PurchaseOrderOptionSnapshot> listActiveByIds(Collection<Long> ids);

    /**
     * 按关键字(单号/供应商)/状态查询未删除采购订单的明细行选项, 供按规格关联。
     *
     * @param purchaseOrderId 可空; 指定时仅返回该订单的明细行
     */
    List<PurchaseOrderItemOptionSnapshot> listActiveItemOptions(String keyword, String status,
                                                                Long purchaseOrderId);

    /** 按明细行 id 批量查询快照(订单或明细不存在/已删除的不返回)。 */
    List<PurchaseOrderItemOptionSnapshot> listActiveItemsByIds(Collection<Long> itemIds);

    record PurchaseOrderOptionSnapshot(
            Long id,
            String orderNo,
            String supplierName,
            BigDecimal totalWeight,
            String status,
            LocalDateTime orderDate
    ) {
    }

    /**
     * 采购订单明细行快照: 按规格关联与扣减的最小契约。
     *
     * @param purchaseOrderId     订单标识
     * @param purchaseOrderItemId 订单明细行标识
     * @param orderNo             订单号
     * @param supplierName        供应商
     * @param category            类别
     * @param material            材质
     * @param spec                规格(订单明细为字符串, 保持原样)
     * @param length              长度
     * @param orderedWeight       该明细行订货吨数
     * @param status              订单状态
     */
    record PurchaseOrderItemOptionSnapshot(
            Long purchaseOrderId,
            Long purchaseOrderItemId,
            String orderNo,
            String supplierName,
            String category,
            String material,
            String spec,
            String length,
            BigDecimal orderedWeight,
            String status
    ) {
    }
}
