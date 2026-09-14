package com.leo.erp.sales.returns.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.returns.repository.SalesReturnItemRepository;
import com.leo.erp.sales.returns.web.dto.SalesReturnCandidateItemResponse;
import com.leo.erp.sales.returns.web.dto.SalesReturnCandidateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 销售退货来源候选查询：给定一张已审核销售出库单，逐行计算可退数量
 * （出库数量 − 累计已审核退货数量，下限为 0），供前端选择退货明细。
 */
@Service
public class SalesReturnCandidateService {

    private final SalesOutboundRepository salesOutboundRepository;
    private final SalesReturnItemRepository salesReturnItemRepository;

    public SalesReturnCandidateService(SalesOutboundRepository salesOutboundRepository,
                                       SalesReturnItemRepository salesReturnItemRepository) {
        this.salesOutboundRepository = salesOutboundRepository;
        this.salesReturnItemRepository = salesReturnItemRepository;
    }

    @Transactional(readOnly = true)
    public SalesReturnCandidateResponse candidates(Long salesOutboundId) {
        SalesOutbound outbound = salesOutboundRepository.findByIdAndDeletedFlagFalse(salesOutboundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "销售出库单不存在"));
        if (!StatusConstants.AUDITED.equals(outbound.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库单未审核，不能作为退货来源");
        }

        List<SalesOutboundItem> items = outbound.getItems() == null ? List.of() : outbound.getItems();
        Map<Long, Integer> returnedByOutboundItem = summarizeAuditedReturned(items);
        List<SalesReturnCandidateItemResponse> itemResponses = items.stream()
                .sorted(Comparator.comparing(SalesOutboundItem::getLineNo, Comparator.nullsLast(Integer::compareTo)))
                .map(item -> toItemResponse(item, returnedByOutboundItem.getOrDefault(item.getId(), 0)))
                .filter(response -> response.returnableQuantity() > 0)
                .toList();

        return new SalesReturnCandidateResponse(
                outbound.getId(),
                outbound.getOutboundNo(),
                outbound.getSalesOrderNo(),
                outbound.getCustomerId(),
                outbound.getCustomerName(),
                outbound.getProjectId(),
                outbound.getProjectName(),
                outbound.getWarehouseId(),
                outbound.getWarehouseName(),
                outbound.getSettlementCompanyId(),
                outbound.getSettlementCompanyName(),
                itemResponses
        );
    }

    private Map<Long, Integer> summarizeAuditedReturned(List<SalesOutboundItem> items) {
        List<Long> itemIds = items.stream()
                .map(SalesOutboundItem::getId)
                .filter(Objects::nonNull)
                .toList();
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> summary = new LinkedHashMap<>();
        salesReturnItemRepository
                .summarizeAuditedQuantityBySourceOutboundItemIds(itemIds, StatusConstants.AUDITED, null)
                .forEach(row -> summary.put(
                        row.getSourceSalesOutboundItemId(),
                        toInt(row.getTotalQuantity())));
        return summary;
    }

    private SalesReturnCandidateItemResponse toItemResponse(SalesOutboundItem item, int returnedQuantity) {
        int outboundQuantity = quantity(item.getQuantity());
        int safeReturnedQuantity = Math.max(returnedQuantity, 0);
        int returnableQuantity = Math.max(outboundQuantity - safeReturnedQuantity, 0);
        return new SalesReturnCandidateItemResponse(
                item.getId(),
                item.getSourceSalesOrderItemId(),
                item.getMaterialId(),
                item.getMaterialCode(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getUnit(),
                item.getWarehouseId(),
                item.getWarehouseName(),
                item.getBatchNo(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                outboundQuantity,
                safeReturnedQuantity,
                returnableQuantity,
                item.getUnitPrice()
        );
    }

    private int quantity(Integer value) {
        return value == null ? 0 : value;
    }

    private int toInt(Long value) {
        if (value == null || value <= 0) {
            return 0;
        }
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
