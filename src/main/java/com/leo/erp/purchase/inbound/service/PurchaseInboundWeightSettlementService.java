package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.master.material.domain.entity.MaterialCategory;
import com.leo.erp.master.material.repository.MaterialCategoryRepository;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PurchaseInboundWeightSettlementService {

    private final MaterialCategoryRepository materialCategoryRepository;

    public PurchaseInboundWeightSettlementService(MaterialCategoryRepository materialCategoryRepository) {
        this.materialCategoryRepository = materialCategoryRepository;
    }

    Set<String> loadPurchaseWeighRequiredCategoryNames(PurchaseInboundRequest request) {
        List<String> categoryNames = request.items().stream()
                .map(PurchaseInboundItemRequest::category)
                .map(this::normalizeCategoryName)
                .filter(category -> !category.isBlank())
                .distinct()
                .toList();
        if (categoryNames.isEmpty()) {
            return Set.of();
        }
        return materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(categoryNames).stream()
                .filter(category -> Boolean.TRUE.equals(category.getPurchaseWeighRequired()))
                .map(MaterialCategory::getCategoryName)
                .map(this::normalizeCategoryName)
                .collect(Collectors.toSet());
    }

    String resolveLineSettlementMode(
            PurchaseInboundItemRequest source,
            PurchaseInboundRequest request,
            int lineNo
    ) {
        String settlementMode = BusinessDocumentValidator.trimToNull(source.settlementMode());
        if (settlementMode == null) {
            settlementMode = BusinessDocumentValidator.trimToNull(request.settlementMode());
        }
        if (settlementMode == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行请选择结算方式");
        }
        return settlementMode;
    }

    WeightSettlementResult resolveWeightSettlement(
            PurchaseInboundItemRequest source,
            int lineNo,
            Set<String> purchaseWeighRequiredCategoryNames,
            String settlementMode
    ) {
        BigDecimal sourcePieceWeightTon = TradeItemCalculator.scaleWeightTon(source.pieceWeightTon());
        BigDecimal baseWeightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), sourcePieceWeightTon);
        boolean purchaseWeighRequired = purchaseWeighRequiredCategoryNames
                .contains(normalizeCategoryName(source.category()));
        boolean purchaseWeighSettlement = isPurchaseWeighSettlement(settlementMode);
        if (!purchaseWeighRequired && !purchaseWeighSettlement) {
            return new WeightSettlementResult(
                    baseWeightTon,
                    null,
                    BigDecimal.ZERO.setScale(PrecisionConstants.WEIGHT_SCALE),
                    BigDecimal.ZERO.setScale(PrecisionConstants.AMOUNT_SCALE),
                    sourcePieceWeightTon,
                    baseWeightTon
            );
        }
        if (!purchaseWeighSettlement) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行商品类别需按过磅入库，请将本行结算方式改为过磅");
        }
        BigDecimal weighWeightTon = requireWeighWeightTon(source, lineNo);
        BigDecimal theoreticalWeightTon = resolveAdjustmentBaseWeightTon(source);
        BigDecimal averagePieceWeightTon = TradeItemCalculator
                .calculateAveragePieceWeightTon(source.quantity(), weighWeightTon);
        BigDecimal weightAdjustmentTon = TradeItemCalculator
                .scaleWeightTon(weighWeightTon.subtract(theoreticalWeightTon));
        BigDecimal weightAdjustmentAmount = TradeItemCalculator
                .calculateAmount(weightAdjustmentTon, source.unitPrice());
        return new WeightSettlementResult(
                theoreticalWeightTon, weighWeightTon,
                weightAdjustmentTon, weightAdjustmentAmount,
                averagePieceWeightTon, weighWeightTon
        );
    }

    private String normalizeCategoryName(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isPurchaseWeighSettlement(String settlementMode) {
        return "过磅".equals(settlementMode == null ? "" : settlementMode.trim());
    }

    private BigDecimal requireWeighWeightTon(PurchaseInboundItemRequest source, int lineNo) {
        BigDecimal weighWeightTon = source.weighWeightTon();
        if (weighWeightTon == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行需填写过磅重量");
        }
        BigDecimal normalized = TradeItemCalculator.scaleWeightTon(weighWeightTon);
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行过磅重量不能小于0");
        }
        if (source.quantity() != null && source.quantity() > 0 && normalized.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行过磅重量必须大于0");
        }
        return normalized;
    }

    private BigDecimal resolveAdjustmentBaseWeightTon(PurchaseInboundItemRequest source) {
        return TradeItemCalculator.calculateWeightTon(source.quantity(), source.pieceWeightTon());
    }
}
