package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.master.material.domain.entity.MaterialCategory;
import com.leo.erp.master.material.repository.MaterialCategoryRepository;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PurchaseInboundWeightSettlementServiceTest {

    @Test
    void shouldSkipNullAndBlankCategoriesWhenLoadingRules() {
        MaterialCategoryRepository materialCategoryRepository = mock(MaterialCategoryRepository.class);
        PurchaseInboundWeightSettlementService service = new PurchaseInboundWeightSettlementService(materialCategoryRepository);
        PurchaseInboundRequest request = request(null, item(null), item("  "));

        Map<String, PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule> rules =
                service.loadPurchaseWeighCategoryRules(request);

        assertThat(rules).isEmpty();
        verifyNoInteractions(materialCategoryRepository);
    }

    @Test
    void shouldKeepLeftRuleWhenDefaultMergeFunctionIsInvoked() throws ReflectiveOperationException {
        PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule left = rule("盘螺", true, "1.00", "2.00");
        PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule right = rule("盘螺", false, "3.00", "4.00");

        PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule merged = invokeDefaultMergeFunction(left, right);

        assertThat(merged).isSameAs(left);
    }

    @Test
    void shouldLoadRepositoryRuleWithDefaultAndRoundedTolerance() {
        MaterialCategoryRepository materialCategoryRepository = mock(MaterialCategoryRepository.class);
        PurchaseInboundWeightSettlementService service = new PurchaseInboundWeightSettlementService(materialCategoryRepository);
        MaterialCategory category = materialCategory("  盘螺  ", true, null, new BigDecimal("1.235"));
        when(materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(List.of("盘螺")))
                .thenReturn(List.of(category));

        Map<String, PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule> rules =
                service.loadPurchaseWeighCategoryRules(request(null, item("  盘螺  ")));

        assertThat(rules).containsOnlyKeys("盘螺");
        assertThat(rules.get("盘螺").purchaseWeighRequired()).isTrue();
        assertThat(rules.get("盘螺").overTolerancePercent()).isEqualByComparingTo("5.00");
        assertThat(rules.get("盘螺").underTolerancePercent()).isEqualByComparingTo("1.24");
    }

    @Test
    void shouldUseBaseWeightWhenCategoryDoesNotRequireWeighAndSettlementModeIsNull() {
        PurchaseInboundWeightSettlementService service = service();
        PurchaseInboundItemRequest source = item("螺纹钢", null, 4, "0.250", null);

        WeightSettlementResult result = service.resolveWeightSettlement(source, 1, Map.of(), null);

        assertThat(result.weightTon()).isEqualByComparingTo("1.00000000");
        assertThat(result.weighWeightTon()).isNull();
        assertThat(result.weightAdjustmentTon()).isEqualByComparingTo("0.00000000");
        assertThat(result.weightAdjustmentAmount()).isEqualByComparingTo("0.00");
        assertThat(result.pieceWeightTon()).isEqualByComparingTo("0.25000000");
        assertThat(result.calculatedWeightTon()).isEqualByComparingTo("1.00000000");
    }

    @Test
    void shouldTrimLineSettlementModeBeforeResolvingWeightSettlement() {
        PurchaseInboundWeightSettlementService service = service();
        PurchaseInboundItemRequest source = item("盘螺", "  过磅  ", 4, "0.250", "1.010");

        WeightSettlementResult result = service.resolveWeightSettlement(source, 1, Map.of(), source.settlementMode());

        assertThat(result.weighWeightTon()).isEqualByComparingTo("1.01000000");
        assertThat(result.weightAdjustmentTon()).isEqualByComparingTo("0.01000000");
        assertThat(result.weightAdjustmentAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void shouldTrimHeaderSettlementModeWhenLineSettlementModeIsBlank() {
        PurchaseInboundWeightSettlementService service = service();
        PurchaseInboundItemRequest source = item("螺纹钢", "  ");
        PurchaseInboundRequest request = request("  过磅  ", source);

        String settlementMode = service.resolveLineSettlementMode(source, request, 1);

        assertThat(settlementMode).isEqualTo("过磅");
    }

    @Test
    void shouldRejectWhenSettlementModeIsMissing() {
        PurchaseInboundWeightSettlementService service = service();
        PurchaseInboundItemRequest source = item("螺纹钢", null);
        PurchaseInboundRequest request = request(null, source);

        assertThatThrownBy(() -> service.resolveLineSettlementMode(source, request, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行请选择结算方式");
    }

    @Test
    void shouldRejectNonWeighSettlementWhenCategoryRequiresWeigh() {
        PurchaseInboundWeightSettlementService service = service();
        PurchaseInboundItemRequest source = item("盘螺", "理算", 4, "0.250", "1.000");

        assertThatThrownBy(() -> service.resolveWeightSettlement(
                source,
                1,
                Map.of("盘螺", rule("盘螺", true, "5.00", "5.00")),
                source.settlementMode()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行商品类别需按过磅入库");
    }

    @Test
    void shouldRejectMissingWeighWeightTon() {
        PurchaseInboundWeightSettlementService service = service();
        PurchaseInboundItemRequest source = item("盘螺", "过磅", 4, "0.250", null);

        assertThatThrownBy(() -> service.resolveWeightSettlement(source, 1, Map.of(), source.settlementMode()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行需填写过磅重量");
    }

    @Test
    void shouldRejectNegativeWeighWeightTon() {
        PurchaseInboundWeightSettlementService service = service();
        PurchaseInboundItemRequest source = item("盘螺", "过磅", 4, "0.250", "-0.001");

        assertThatThrownBy(() -> service.resolveWeightSettlement(source, 1, Map.of(), source.settlementMode()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行过磅重量不能小于0");
    }

    @Test
    void shouldRejectZeroWeighWeightTonWhenQuantityIsPositive() {
        PurchaseInboundWeightSettlementService service = service();
        PurchaseInboundItemRequest source = item("盘螺", "过磅", 4, "0.250", "0.000");

        assertThatThrownBy(() -> service.resolveWeightSettlement(source, 1, Map.of(), source.settlementMode()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行过磅重量必须大于0");
    }

    @Test
    void shouldAllowZeroWeighWeightTonWhenQuantityIsZero() {
        PurchaseInboundWeightSettlementService service = service();
        PurchaseInboundItemRequest source = item("盘螺", "过磅", 0, "0.250", "0.000");

        WeightSettlementResult result = service.resolveWeightSettlement(source, 1, Map.of(), source.settlementMode());

        assertThat(result.weighWeightTon()).isEqualByComparingTo("0.00000000");
    }

    @Test
    void shouldAllowZeroWeighWeightTonWhenQuantityIsMissing() {
        PurchaseInboundWeightSettlementService service = service();
        PurchaseInboundItemRequest source = item("盘螺", "过磅", null, "0.250", "0.000");

        WeightSettlementResult result = service.resolveWeightSettlement(source, 1, Map.of(), source.settlementMode());

        assertThat(result.weighWeightTon()).isEqualByComparingTo("0.00000000");
    }

    @Test
    void shouldSkipToleranceWhenTheoreticalWeightIsZero() {
        PurchaseInboundWeightSettlementService service = service();
        PurchaseInboundItemRequest source = item("盘螺", "过磅", 0, "0.250", "10.000");

        WeightSettlementResult result = service.resolveWeightSettlement(
                source,
                1,
                Map.of("盘螺", rule("盘螺", true, "0.00", "0.00")),
                source.settlementMode()
        );

        assertThat(result.weightTon()).isEqualByComparingTo("0.00000000");
        assertThat(result.weighWeightTon()).isEqualByComparingTo("10.00000000");
        assertThat(result.weightAdjustmentTon()).isEqualByComparingTo("10.00000000");
    }

    @Test
    void shouldRejectOverToleranceWhenWeighWeightIsHigherThanTheory() {
        PurchaseInboundWeightSettlementService service = service();
        PurchaseInboundItemRequest source = item("盘螺", "过磅", 4, "0.250", "1.011");

        assertThatThrownBy(() -> service.resolveWeightSettlement(
                source,
                1,
                Map.of("盘螺", rule("盘螺", true, "1.00", "5.00")),
                source.settlementMode()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上差超过允许范围")
                .hasMessageContaining("当前差异1.1%")
                .hasMessageContaining("允许1%");
    }

    @Test
    void shouldRejectUnderToleranceWhenWeighWeightIsLowerThanTheory() {
        PurchaseInboundWeightSettlementService service = service();
        PurchaseInboundItemRequest source = item("盘螺", "过磅", 4, "0.250", "0.979");

        assertThatThrownBy(() -> service.resolveWeightSettlement(
                source,
                1,
                Map.of("盘螺", rule("盘螺", true, "5.00", "2.00")),
                source.settlementMode()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下差超过允许范围")
                .hasMessageContaining("当前差异2.1%")
                .hasMessageContaining("允许2%");
    }

    private PurchaseInboundWeightSettlementService service() {
        return new PurchaseInboundWeightSettlementService(mock(MaterialCategoryRepository.class));
    }

    private PurchaseInboundRequest request(String settlementMode, PurchaseInboundItemRequest... items) {
        return new PurchaseInboundRequest(
                "PI-001",
                null,
                "供应商A",
                "一号库",
                LocalDate.of(2026, 4, 26),
                settlementMode,
                "草稿",
                null,
                List.of(items)
        );
    }

    private PurchaseInboundItemRequest item(String category) {
        return item(category, null);
    }

    private PurchaseInboundItemRequest item(String category, String settlementMode) {
        return item(category, settlementMode, 4, "0.250", null);
    }

    private PurchaseInboundItemRequest item(String category,
                                            String settlementMode,
                                            Integer quantity,
                                            String pieceWeightTon,
                                            String weighWeightTon) {
        return new PurchaseInboundItemRequest(
                null,
                "M1",
                "宝钢",
                category,
                "HRB400",
                "18",
                "12m",
                "吨",
                101L,
                "一号库",
                settlementMode,
                "B1",
                quantity,
                "支",
                new BigDecimal(pieceWeightTon),
                1,
                null,
                weighWeightTon == null ? null : new BigDecimal(weighWeightTon),
                null,
                null,
                new BigDecimal("4000.00"),
                null
        );
    }

    private MaterialCategory materialCategory(String categoryName,
                                              boolean purchaseWeighRequired,
                                              BigDecimal overTolerancePercent,
                                              BigDecimal underTolerancePercent) {
        MaterialCategory category = new MaterialCategory();
        category.setCategoryName(categoryName);
        category.setPurchaseWeighRequired(purchaseWeighRequired);
        category.setPurchaseWeighOverTolerancePercent(overTolerancePercent);
        category.setPurchaseWeighUnderTolerancePercent(underTolerancePercent);
        return category;
    }

    private PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule rule(String categoryName,
                                                                                  boolean purchaseWeighRequired,
                                                                                  String overTolerancePercent,
                                                                                  String underTolerancePercent) {
        return new PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule(
                categoryName,
                purchaseWeighRequired,
                new BigDecimal(overTolerancePercent),
                new BigDecimal(underTolerancePercent)
        );
    }

    private PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule invokeDefaultMergeFunction(
            PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule left,
            PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule right
    ) throws ReflectiveOperationException {
        Method mergeFunction = Arrays.stream(PurchaseInboundWeightSettlementService.class.getDeclaredMethods())
                .filter(Method::isSynthetic)
                .filter(method -> method.getReturnType()
                        .equals(PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule.class))
                .filter(method -> Arrays.equals(
                        method.getParameterTypes(),
                        new Class<?>[]{
                                PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule.class,
                                PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule.class
                        }
                ))
                .findFirst()
                .orElseThrow();
        mergeFunction.setAccessible(true);
        return (PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule)
                mergeFunction.invoke(null, left, right);
    }
}
