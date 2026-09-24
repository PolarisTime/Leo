package com.leo.erp.market.quotation.service;

import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.PurchaseOrderTonnageResponse;
import com.leo.erp.purchase.api.PurchaseOrderOptionQuery;
import com.leo.erp.purchase.api.PurchaseOrderOptionQuery.PurchaseOrderItemOptionSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采购订单明细行吨位汇总: 订货吨数 - 报单已开吨位 = 剩余可开吨, 按「订单明细行(规格)」核算。
 * <p><b>计划态提示</b>: 只读查询, 供比价报价单吨位列的采购订单下拉与"已开/剩余"展示;
 * 不参与写校验、不做额度扣减、不回写采购订单。超额仅前端提示, 允许保存。</p>
 * <p>口径:
 * <ul>
 *   <li>扣减粒度到「订单明细行」, 同一订单下不同规格各自核算;</li>
 *   <li>已开吨位仅统计未删除报价单中带 {@code purchaseOrderItemId} 的商品行(不含未保存改动);
 *       历史未回填明细行的报单不计入行级口径, 会体现在该规格"剩余偏多";</li>
 *   <li>报价单当前无"作废"状态, 软删报价单不计入。</li>
 * </ul>
 */
@Service
public class PurchaseOrderTonnageService {

    private final QuoteSheetRepository quoteSheetRepository;
    private final PurchaseOrderOptionQuery purchaseOrderOptionQuery;

    public PurchaseOrderTonnageService(QuoteSheetRepository quoteSheetRepository,
                                       PurchaseOrderOptionQuery purchaseOrderOptionQuery) {
        this.quoteSheetRepository = quoteSheetRepository;
        this.purchaseOrderOptionQuery = purchaseOrderOptionQuery;
    }

    /**
     * 按关键字/状态列出采购订单明细行及其订货/已开/剩余吨位, 供吨位列下拉选择。
     *
     * @param purchaseOrderId 可空; 指定时仅返回该订单的明细行
     * @param excludeSheetId  排除的报价单标识(编辑当前单据时排除自身已保存吨位), 可为空
     */
    @Transactional(readOnly = true)
    public List<PurchaseOrderTonnageResponse> listOptions(String keyword, String status,
                                                          Long purchaseOrderId, Long excludeSheetId) {
        return attachTonnage(
                purchaseOrderOptionQuery.listActiveItemOptions(keyword, status, purchaseOrderId),
                excludeSheetId);
    }

    /**
     * 汇总指定采购订单明细行的订货/已开/剩余吨位(用于回显已关联行)。
     *
     * @param purchaseOrderItemIds 订单明细行标识(去重; 空返回空列表)
     * @param excludeSheetId       排除的报价单标识, 可为空
     */
    @Transactional(readOnly = true)
    public List<PurchaseOrderTonnageResponse> summarize(Collection<Long> purchaseOrderItemIds,
                                                        Long excludeSheetId) {
        if (purchaseOrderItemIds == null || purchaseOrderItemIds.isEmpty()) {
            return List.of();
        }
        Set<Long> distinctIds = purchaseOrderItemIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        return attachTonnage(
                purchaseOrderOptionQuery.listActiveItemsByIds(distinctIds), excludeSheetId);
    }

    private List<PurchaseOrderTonnageResponse> attachTonnage(List<PurchaseOrderItemOptionSnapshot> items,
                                                             Long excludeSheetId) {
        Set<Long> itemIds = items.stream()
                .map(PurchaseOrderItemOptionSnapshot::purchaseOrderItemId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, BigDecimal> issuedByItemId = quoteSheetRepository
                .sumIssuedTonByPurchaseOrderItemIds(itemIds, excludeSheetId).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1]));
        return items.stream()
                .map(item -> toResponse(item, issuedByItemId.getOrDefault(
                        item.purchaseOrderItemId(), BigDecimal.ZERO)))
                .toList();
    }

    private static PurchaseOrderTonnageResponse toResponse(PurchaseOrderItemOptionSnapshot item,
                                                           BigDecimal issuedWeight) {
        BigDecimal orderedWeight = item.orderedWeight() == null ? BigDecimal.ZERO : item.orderedWeight();
        BigDecimal issued = issuedWeight == null ? BigDecimal.ZERO : issuedWeight;
        return new PurchaseOrderTonnageResponse(
                item.purchaseOrderId(),
                item.purchaseOrderItemId(),
                item.orderNo(),
                item.supplierName(),
                item.category(),
                item.material(),
                item.spec(),
                item.length(),
                orderedWeight,
                issued,
                orderedWeight.subtract(issued),
                item.status());
    }
}
