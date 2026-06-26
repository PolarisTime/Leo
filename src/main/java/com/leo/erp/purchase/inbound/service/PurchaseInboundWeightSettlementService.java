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
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PurchaseInboundWeightSettlementService {

    private static final BigDecimal DEFAULT_PURCHASE_WEIGH_TOLERANCE_PERCENT = new BigDecimal("5.00");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final MaterialCategoryRepository materialCategoryRepository;

    public PurchaseInboundWeightSettlementService(MaterialCategoryRepository materialCategoryRepository) {
        this.materialCategoryRepository = materialCategoryRepository;
    }

    Map<String, PurchaseWeighCategoryRule> loadPurchaseWeighCategoryRules(PurchaseInboundRequest request) {
        List<String> categoryNames = request.items().stream()
                .map(PurchaseInboundItemRequest::category)
                .map(this::normalizeCategoryName)
                .filter(category -> !category.isBlank())
                .distinct()
                .toList();
        if (categoryNames.isEmpty()) {
            return Map.of();
        }
        Map<String, PurchaseWeighCategoryRule> ruleMap = categoryNames.stream()
                .collect(Collectors.toMap(
                        categoryName -> categoryName,
                        categoryName -> new PurchaseWeighCategoryRule(
                                categoryName,
                                false,
                                DEFAULT_PURCHASE_WEIGH_TOLERANCE_PERCENT,
                                DEFAULT_PURCHASE_WEIGH_TOLERANCE_PERCENT
                        ),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(categoryNames)
                .forEach(category -> ruleMap.put(
                        normalizeCategoryName(category.getCategoryName()),
                        new PurchaseWeighCategoryRule(
                                normalizeCategoryName(category.getCategoryName()),
                                Boolean.TRUE.equals(category.getPurchaseWeighRequired()),
                                normalizeTolerancePercent(category.getPurchaseWeighOverTolerancePercent()),
                                normalizeTolerancePercent(category.getPurchaseWeighUnderTolerancePercent())
                        )
                ));
        return ruleMap;
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
            Map<String, PurchaseWeighCategoryRule> purchaseWeighCategoryRules,
            String settlementMode
    ) {
        BigDecimal sourcePieceWeightTon = TradeItemCalculator.scaleWeightTon(source.pieceWeightTon());
        BigDecimal baseWeightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), sourcePieceWeightTon);
        PurchaseWeighCategoryRule purchaseWeighCategoryRule = resolvePurchaseWeighCategoryRule(
                purchaseWeighCategoryRules, source.category());
        boolean purchaseWeighRequired = purchaseWeighCategoryRule.purchaseWeighRequired();
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
        validateWeighTolerance(weighWeightTon, theoreticalWeightTon, purchaseWeighCategoryRule, lineNo);
        BigDecimal weightAdjustmentTon = TradeItemCalculator
                .scaleWeightTon(weighWeightTon.subtract(theoreticalWeightTon));
        BigDecimal weightAdjustmentAmount = TradeItemCalculator
                .calculateAmount(weightAdjustmentTon, source.unitPrice());
        return new WeightSettlementResult(
                theoreticalWeightTon, weighWeightTon,
                weightAdjustmentTon, weightAdjustmentAmount,
                sourcePieceWeightTon, weighWeightTon
        );
    }

    private String normalizeCategoryName(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isPurchaseWeighSettlement(String settlementMode) {
        return "过磅".equals(settlementMode == null ? "" : settlementMode.trim());
    }

    private PurchaseWeighCategoryRule resolvePurchaseWeighCategoryRule(
            Map<String, PurchaseWeighCategoryRule> ruleMap,
            String categoryName
    ) {
        String normalizedCategoryName = normalizeCategoryName(categoryName);
        return ruleMap.getOrDefault(
                normalizedCategoryName,
                new PurchaseWeighCategoryRule(
                        normalizedCategoryName,
                        false,
                        DEFAULT_PURCHASE_WEIGH_TOLERANCE_PERCENT,
                        DEFAULT_PURCHASE_WEIGH_TOLERANCE_PERCENT
                )
        );
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

    private void validateWeighTolerance(
            BigDecimal weighWeightTon,
            BigDecimal theoreticalWeightTon,
            PurchaseWeighCategoryRule rule,
            int lineNo
    ) {
        if (theoreticalWeightTon.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal diff = weighWeightTon.subtract(theoreticalWeightTon);
        BigDecimal diffPercent = diff.abs()
                .multiply(ONE_HUNDRED)
                .divide(theoreticalWeightTon, 4, RoundingMode.HALF_UP);
        BigDecimal limitPercent = diff.compareTo(BigDecimal.ZERO) > 0
                ? rule.overTolerancePercent()
                : rule.underTolerancePercent();
        if (diffPercent.compareTo(limitPercent) > 0) {
            String direction = diff.compareTo(BigDecimal.ZERO) > 0 ? "上差" : "下差";
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "第" + lineNo + "行过磅重量" + direction + "超过允许范围，当前差异"
                            + formatPercent(diffPercent) + "%，允许" + formatPercent(limitPercent) + "%"
            );
        }
    }

    private BigDecimal normalizeTolerancePercent(BigDecimal value) {
        BigDecimal normalized = value == null ? DEFAULT_PURCHASE_WEIGH_TOLERANCE_PERCENT : value;
        return normalized.setScale(2, RoundingMode.HALF_UP);
    }

    private String formatPercent(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    record PurchaseWeighCategoryRule(
            String categoryName,
            boolean purchaseWeighRequired,
            BigDecimal overTolerancePercent,
            BigDecimal underTolerancePercent
    ) {
    }
}
