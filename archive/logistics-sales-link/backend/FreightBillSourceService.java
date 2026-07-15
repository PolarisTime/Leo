package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Objects;

@Service
public class FreightBillSourceService {

    private static final Set<String> IMPORTABLE_OUTBOUND_STATUSES =
            Set.of(StatusConstants.PRE_OUTBOUND, StatusConstants.AUDITED);

    private final FreightBillRepository freightBillRepository;
    private final SalesOutboundRepository salesOutboundRepository;

    public FreightBillSourceService(FreightBillRepository freightBillRepository,
                                    SalesOutboundRepository salesOutboundRepository) {
        this.freightBillRepository = freightBillRepository;
        this.salesOutboundRepository = salesOutboundRepository;
    }

    public SourceValidationContext validateSources(FreightBillRequest request, Long currentBillId) {
        Set<Long> sourceItemIds = collectSourceItemIds(request.items());
        if (sourceItemIds.isEmpty()) {
            return new SourceValidationContext(Map.of(), Map.of());
        }

        List<SalesOutbound> outbounds = salesOutboundRepository.findAllWithItemsByItemIds(sourceItemIds);
        Map<Long, SourceItemReference> sourceItemsById = indexSourceItems(outbounds, sourceItemIds);
        Map<String, SalesOutbound> outboundMap = new LinkedHashMap<>();
        Map<Integer, SalesOutboundItem> sourceItemMap = new HashMap<>();
        Map<Integer, SalesOutbound> sourceOutboundMap = new HashMap<>();
        Map<Long, String> sourceNoByItemId = new LinkedHashMap<>();
        for (int i = 0; i < request.items().size(); i++) {
            FreightBillItemRequest line = request.items().get(i);
            Long sourceItemId = line.sourceSalesOutboundItemId();
            if (sourceItemId == null) {
                if (BusinessDocumentValidator.trimToNull(line.sourceNo()) == null) {
                    continue;
                }
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "第" + (i + 1) + "行来源销售出库明细ID不能为空");
            }
            SourceItemReference reference = sourceItemsById.get(sourceItemId);
            if (reference == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "第" + (i + 1) + "行来源销售出库明细不存在");
            }
            SalesOutboundItem sourceItem = validateLine(line, i + 1, reference.outbound(), reference.item());
            outboundMap.putIfAbsent(
                    BusinessDocumentValidator.trimToNull(reference.outbound().getOutboundNo()),
                    reference.outbound()
            );
            sourceItemMap.put(i + 1, sourceItem);
            sourceOutboundMap.put(i + 1, reference.outbound());
            sourceNoByItemId.put(sourceItem.getId(),
                    BusinessDocumentValidator.trimToNull(line.sourceNo()));
        }
        assertSourceOutboundsNotOccupied(sourceNoByItemId, currentBillId);
        return new SourceValidationContext(outboundMap, sourceItemMap, sourceOutboundMap);
    }

    void assertSourcesAuditable(SourceValidationContext sourceContext) {
        sourceContext.outboundMap().values().forEach(this::assertOutboundAudited);
    }

    void assertSourceItemsAuditable(Collection<Long> sourceItemIds) {
        Set<Long> normalizedSourceItemIds = sourceItemIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedSourceItemIds.isEmpty()) {
            return;
        }

        List<SalesOutbound> outbounds = salesOutboundRepository.findAllWithItemsByItemIds(normalizedSourceItemIds);
        Map<Long, SourceItemReference> sourceItemsById = indexSourceItems(outbounds, normalizedSourceItemIds);
        for (Long sourceItemId : normalizedSourceItemIds) {
            SourceItemReference reference = sourceItemsById.get(sourceItemId);
            if (reference == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "来源销售出库明细" + sourceItemId + "不存在");
            }
            assertOutboundAudited(reference.outbound());
        }
    }

    private Set<Long> collectSourceItemIds(List<FreightBillItemRequest> items) {
        Set<Long> sourceItemIds = new LinkedHashSet<>();
        for (int index = 0; index < items.size(); index++) {
            FreightBillItemRequest item = items.get(index);
            String sourceNo = BusinessDocumentValidator.trimToNull(item.sourceNo());
            if (item.sourceSalesOutboundItemId() == null) {
                if (sourceNo == null) {
                    continue;
                }
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "第" + (index + 1) + "行来源销售出库明细ID不能为空");
            }
            if (sourceNo == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "第" + (index + 1) + "行来源销售出库单号不能为空");
            }
            if (!sourceItemIds.add(item.sourceSalesOutboundItemId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "来源销售出库明细ID重复");
            }
        }
        return sourceItemIds;
    }

    private void assertSourceOutboundsNotOccupied(Map<Long, String> sourceNoByItemId, Long currentBillId) {
        List<FreightBill> occupiedBills =
                freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(sourceNoByItemId.keySet(), currentBillId);
        for (FreightBill occupiedBill : occupiedBills) {
            FreightBillItem matchedItem = occupiedBill.getItems().stream()
                    .filter(item -> sourceNoByItemId.containsKey(item.getSourceSalesOutboundItemId()))
                    .findFirst()
                    .orElse(null);
            if (matchedItem == null) {
                continue;
            }
            String sourceNo = sourceNoByItemId.get(matchedItem.getSourceSalesOutboundItemId());
            String billNo = BusinessDocumentValidator.trimToNull(occupiedBill.getBillNo());
            String carrierName = BusinessDocumentValidator.trimToNull(occupiedBill.getCarrierName());
            StringBuilder message = new StringBuilder("销售出库单").append(sourceNo).append("已归集到物流单");
            if (billNo != null) {
                message.append(billNo);
            }
            if (carrierName != null) {
                message.append("（物流商：").append(carrierName).append("）");
            }
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message.toString());
        }
    }

    private Map<Long, SourceItemReference> indexSourceItems(List<SalesOutbound> outbounds,
                                                              Set<Long> requestedItemIds) {
        Map<Long, SourceItemReference> sourceItemsById = new LinkedHashMap<>();
        for (SalesOutbound outbound : outbounds) {
            for (SalesOutboundItem item : outbound.getItems()) {
                if (item.getId() == null || !requestedItemIds.contains(item.getId())) {
                    continue;
                }
                SourceItemReference previous = sourceItemsById.putIfAbsent(
                        item.getId(), new SourceItemReference(outbound, item)
                );
                if (previous != null && !Objects.equals(previous.outbound().getId(), outbound.getId())) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                            "来源销售出库明细ID指向多个来源单");
                }
            }
        }
        return sourceItemsById;
    }

    private SalesOutboundItem validateLine(
            FreightBillItemRequest request,
            int lineNo,
            SalesOutbound outbound,
            SalesOutboundItem outboundItem
    ) {
        BusinessDocumentValidator.requireSameSourceText(
                request.sourceNo(), outbound.getOutboundNo(), lineNo, "来源销售出库单", "单号"
        );
        BusinessDocumentValidator.requireStatusIn(
                outbound.getStatus(),
                IMPORTABLE_OUTBOUND_STATUSES,
                "第" + lineNo + "行来源销售出库状态不允许导入物流单"
        );
        BusinessDocumentValidator.requireSameSourceText(
                request.customerName(),
                outbound.getCustomerName(),
                lineNo,
                "来源销售出库单",
                "客户"
        );
        BusinessDocumentValidator.requireSameSourceText(
                request.projectName(),
                outbound.getProjectName(),
                lineNo,
                "来源销售出库单",
                "项目"
        );
        requireSameSourceId(request.customerId(), outbound.getCustomerId(), lineNo, "客户");
        requireSameSourceId(request.projectId(), outbound.getProjectId(), lineNo, "项目");

        BusinessDocumentValidator.requireSameSourceText(request.materialCode(), outboundItem.getMaterialCode(), lineNo, "来源销售出库明细", "物料编码");
        requireSameSourceId(request.materialId(), outboundItem.getMaterialId(), lineNo, "商品");
        BusinessDocumentValidator.requireSameSourceText(request.brand(), outboundItem.getBrand(), lineNo, "来源销售出库明细", "品牌");
        BusinessDocumentValidator.requireSameSourceText(request.category(), outboundItem.getCategory(), lineNo, "来源销售出库明细", "品类");
        BusinessDocumentValidator.requireSameSourceText(request.material(), outboundItem.getMaterial(), lineNo, "来源销售出库明细", "材质");
        BusinessDocumentValidator.requireSameSourceText(request.spec(), outboundItem.getSpec(), lineNo, "来源销售出库明细", "规格");
        BusinessDocumentValidator.requireSameSourceText(request.length(), outboundItem.getLength(), lineNo, "来源销售出库明细", "长度");
        BusinessDocumentValidator.requireSameSourceInteger(request.quantity(), outboundItem.getQuantity(), lineNo, "来源销售出库明细", "数量");
        BusinessDocumentValidator.requireSameSourceText(
                TradeItemCalculator.normalizeQuantityUnit(request.quantityUnit()),
                TradeItemCalculator.normalizeQuantityUnit(outboundItem.getQuantityUnit()),
                lineNo,
                "来源销售出库明细",
                "数量单位"
        );
        BusinessDocumentValidator.requireSameSourceDecimal(request.pieceWeightTon(), outboundItem.getPieceWeightTon(), lineNo, "来源销售出库明细", "件重");
        BusinessDocumentValidator.requireSameSourceInteger(request.piecesPerBundle(), outboundItem.getPiecesPerBundle(), lineNo, "来源销售出库明细", "每捆支数");
        BusinessDocumentValidator.requireSameSourceText(request.batchNo(), outboundItem.getBatchNo(), lineNo, "来源销售出库明细", "批号");
        BigDecimal requestedWeightTon = request.weightTon() != null
                ? request.weightTon()
                : TradeItemCalculator.calculateWeightTon(request.quantity(), request.pieceWeightTon());
        BusinessDocumentValidator.requireSameSourceDecimal(requestedWeightTon, outboundItem.getWeightTon(), lineNo, "来源销售出库明细", "重量");
        BusinessDocumentValidator.requireSameSourceText(request.warehouseName(), outboundItem.getWarehouseName(), lineNo, "来源销售出库明细", "仓库");
        requireSameSourceId(request.warehouseId(), outboundItem.getWarehouseId(), lineNo, "仓库");
        return outboundItem;
    }

    private void assertOutboundAudited(SalesOutbound outbound) {
        if (StatusConstants.AUDITED.equals(BusinessDocumentValidator.normalizeText(outbound.getStatus()))) {
            return;
        }
        String sourceNo = BusinessDocumentValidator.trimToNull(outbound.getOutboundNo());
        String suffix = sourceNo == null ? "" : "：" + sourceNo;
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源销售出库尚未审核" + suffix);
    }

    private void requireSameSourceId(Long requestedId, Long sourceId, int lineNo, String fieldName) {
        if (requestedId == null) {
            return;
        }
        if (!Objects.equals(requestedId, sourceId)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行" + fieldName + "ID与来源销售出库单不一致"
            );
        }
    }

    public record SourceValidationContext(
            Map<String, SalesOutbound> outboundMap,
            Map<Integer, SalesOutboundItem> sourceItemMap,
            Map<Integer, SalesOutbound> sourceOutboundMap
    ) {
        public SourceValidationContext(Map<String, SalesOutbound> outboundMap,
                                       Map<Integer, SalesOutboundItem> sourceItemMap) {
            this(outboundMap, sourceItemMap, Map.of());
        }

        SalesOutboundItem sourceItemAt(int lineNo) {
            return sourceItemMap.get(lineNo);
        }

        SalesOutbound sourceOutboundAt(String sourceNo) {
            String normalizedSourceNo = BusinessDocumentValidator.trimToNull(sourceNo);
            return normalizedSourceNo == null ? null : outboundMap.get(normalizedSourceNo);
        }

        SalesOutbound sourceOutboundAt(int lineNo) {
            SalesOutbound mapped = sourceOutboundMap.get(lineNo);
            if (mapped != null) {
                return mapped;
            }
            SalesOutboundItem sourceItem = sourceItemAt(lineNo);
            if (sourceItem == null) {
                return null;
            }
            SalesOutbound direct = sourceItem.getSalesOutbound();
            if (direct != null) {
                return direct;
            }
            return outboundMap.values().stream()
                    .filter(outbound -> outbound.getItems().stream()
                            .anyMatch(item -> Objects.equals(item.getId(), sourceItem.getId())))
                    .findFirst()
                    .orElse(null);
        }
    }

    private record SourceItemReference(SalesOutbound outbound, SalesOutboundItem item) {
    }
}
