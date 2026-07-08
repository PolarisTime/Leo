package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        Set<String> sourceNos = collectSourceNos(request.items());
        if (sourceNos.isEmpty()) {
            return new SourceValidationContext(Map.of(), Map.of());
        }

        assertSourceOutboundsNotOccupied(sourceNos, currentBillId);
        Map<String, SalesOutbound> outboundMap = loadOutboundMap(sourceNos);
        Map<Integer, SalesOutboundItem> sourceItemMap = new HashMap<>();
        for (int i = 0; i < request.items().size(); i++) {
            SalesOutboundItem sourceItem = validateLine(request.items().get(i), i + 1, outboundMap);
            sourceItemMap.put(i + 1, sourceItem);
        }
        return new SourceValidationContext(outboundMap, sourceItemMap);
    }

    void assertSourcesAuditable(SourceValidationContext sourceContext) {
        sourceContext.outboundMap().values().forEach(this::assertOutboundAudited);
    }

    void assertSourceNosAuditable(Collection<String> sourceNos) {
        Set<String> normalizedSourceNos = sourceNos.stream()
                .map(BusinessDocumentValidator::trimToNull)
                .filter(value -> value != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedSourceNos.isEmpty()) {
            return;
        }

        Map<String, SalesOutbound> outboundMap = loadOutboundMap(normalizedSourceNos);
        for (String sourceNo : normalizedSourceNos) {
            SalesOutbound outbound = outboundMap.get(sourceNo);
            if (outbound == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源销售出库单" + sourceNo + "不存在");
            }
            assertOutboundAudited(outbound);
        }
    }

    private Set<String> collectSourceNos(List<FreightBillItemRequest> items) {
        return items.stream()
                .map(FreightBillItemRequest::sourceNo)
                .map(BusinessDocumentValidator::trimToNull)
                .filter(value -> value != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void assertSourceOutboundsNotOccupied(Set<String> sourceNos, Long currentBillId) {
        List<FreightBill> occupiedBills =
                freightBillRepository.findAllBySourceNosExcludingCurrentBill(sourceNos, currentBillId);
        for (String sourceNo : sourceNos) {
            for (FreightBill occupiedBill : occupiedBills) {
                boolean matched = occupiedBill.getItems().stream()
                        .anyMatch(item -> sourceNo.equals(BusinessDocumentValidator.trimToNull(item.getSourceNo())));
                if (!matched) {
                    continue;
                }
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
    }

    private Map<String, SalesOutbound> loadOutboundMap(Collection<String> sourceNos) {
        return salesOutboundRepository.findByOutboundNoInAndDeletedFlagFalse(sourceNos).stream()
                .collect(Collectors.toMap(SalesOutbound::getOutboundNo, outbound -> outbound));
    }

    private SalesOutboundItem validateLine(
            FreightBillItemRequest request,
            int lineNo,
            Map<String, SalesOutbound> outboundMap
    ) {
        String sourceNo = BusinessDocumentValidator.trimToNull(request.sourceNo());
        SalesOutbound outbound = outboundMap.get(sourceNo);
        if (outbound == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售出库单不存在");
        }
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

        SalesOutboundItem outboundItem = findMatchingItem(request, outbound);
        if (outboundItem == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售出库明细不存在");
        }
        BusinessDocumentValidator.requireSameSourceText(request.materialCode(), outboundItem.getMaterialCode(), lineNo, "来源销售出库明细", "物料编码");
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

    private SalesOutboundItem findMatchingItem(FreightBillItemRequest request, SalesOutbound outbound) {
        if (request.sourceSalesOutboundItemId() != null) {
            return outbound.getItems().stream()
                    .filter(item -> request.sourceSalesOutboundItemId().equals(item.getId()))
                    .findFirst()
                    .orElse(null);
        }
        return outbound.getItems().stream()
                .filter(item -> BusinessDocumentValidator.normalizeText(request.materialCode())
                        .equals(BusinessDocumentValidator.normalizeText(item.getMaterialCode())))
                .filter(item -> BusinessDocumentValidator.normalizeText(request.batchNo())
                        .equals(BusinessDocumentValidator.normalizeText(item.getBatchNo())))
                .filter(item -> BusinessDocumentValidator.normalizeText(request.warehouseName())
                        .equals(BusinessDocumentValidator.normalizeText(item.getWarehouseName())))
                .filter(item -> java.util.Objects.equals(request.quantity(), item.getQuantity()))
                .findFirst()
                .orElse(null);
    }

    public record SourceValidationContext(
            Map<String, SalesOutbound> outboundMap,
            Map<Integer, SalesOutboundItem> sourceItemMap
    ) {
        SalesOutboundItem sourceItemAt(int lineNo) {
            return sourceItemMap.get(lineNo);
        }
    }
}
