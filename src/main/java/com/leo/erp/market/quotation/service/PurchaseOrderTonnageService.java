package com.leo.erp.market.quotation.service;

import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.PurchaseOrderTonnageResponse;
import com.leo.erp.purchase.api.PurchaseOrderOptionQuery;
import com.leo.erp.purchase.api.PurchaseOrderOptionQuery.PurchaseOrderOptionSnapshot;
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
 * 采购订单吨位汇总: 订货吨数 - 报单已开吨位 = 剩余可开吨。
 * <p>只读查询, 供比价报价单吨位列的采购订单下拉与"已开/剩余"展示; 不参与写校验(超额仅前端提示)。</p>
 * <p>口径边界: 扣减粒度到「采购订单」而非订单明细行, 同一订单下不同规格的吨位会汇总到同一口径;
 * 若业务需要按商品规格逐行核对, 需另建行级关联。已开吨位仅统计已保存的报价单, 未保存改动不计入。</p>
 */
@Service
public class PurchaseOrderTonnageService {

    private static final int MAX_OPTIONS = 200;

    private final QuoteSheetRepository quoteSheetRepository;
    private final PurchaseOrderOptionQuery purchaseOrderOptionQuery;

    public PurchaseOrderTonnageService(QuoteSheetRepository quoteSheetRepository,
                                       PurchaseOrderOptionQuery purchaseOrderOptionQuery) {
        this.quoteSheetRepository = quoteSheetRepository;
        this.purchaseOrderOptionQuery = purchaseOrderOptionQuery;
    }

    /**
     * 按关键字/状态列出采购订单及其订货/已开/剩余吨位, 供吨位列下拉选择。
     *
     * @param excludeSheetId 排除的报价单标识(编辑当前单据时排除自身已保存吨位), 可为空
     */
    @Transactional(readOnly = true)
    public List<PurchaseOrderTonnageResponse> listOptions(String keyword, String status, Long excludeSheetId) {
        List<PurchaseOrderOptionSnapshot> orders = purchaseOrderOptionQuery.listActiveOptions(keyword, status);
        if (orders.isEmpty()) {
            return List.of();
        }
        return attachTonnage(orders, excludeSheetId);
    }

    /**
     * 汇总指定采购订单的订货/已开/剩余吨位。
     *
     * @param purchaseOrderIds 采购订单标识(去重; 空返回空列表)
     * @param excludeSheetId   排除的报价单标识(编辑当前单据时排除自身已保存吨位), 可为空
     */
    @Transactional(readOnly = true)
    public List<PurchaseOrderTonnageResponse> summarize(Collection<Long> purchaseOrderIds, Long excludeSheetId) {
        if (purchaseOrderIds == null || purchaseOrderIds.isEmpty()) {
            return List.of();
        }
        Set<Long> distinctIds = purchaseOrderIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        return attachTonnage(purchaseOrderOptionQuery.listActiveByIds(distinctIds), excludeSheetId);
    }

    private List<PurchaseOrderTonnageResponse> attachTonnage(List<PurchaseOrderOptionSnapshot> orders,
                                                             Long excludeSheetId) {
        Set<Long> orderIds = orders.stream()
                .map(PurchaseOrderOptionSnapshot::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, BigDecimal> issuedByOrderId = quoteSheetRepository
                .sumIssuedTonByPurchaseOrderIds(orderIds, excludeSheetId).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1]));
        return orders.stream()
                .limit(MAX_OPTIONS)
                .map(order -> toResponse(order, issuedByOrderId.getOrDefault(order.id(), BigDecimal.ZERO)))
                .toList();
    }

    private static PurchaseOrderTonnageResponse toResponse(PurchaseOrderOptionSnapshot order,
                                                           BigDecimal issuedWeight) {
        BigDecimal orderedWeight = order.totalWeight() == null ? BigDecimal.ZERO : order.totalWeight();
        BigDecimal issued = issuedWeight == null ? BigDecimal.ZERO : issuedWeight;
        return new PurchaseOrderTonnageResponse(
                order.id(),
                order.orderNo(),
                order.supplierName(),
                orderedWeight,
                issued,
                orderedWeight.subtract(issued),
                order.status());
    }
}
