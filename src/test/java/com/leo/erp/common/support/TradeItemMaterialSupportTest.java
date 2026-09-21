package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TradeItemMaterialSupport 商品解析与批号规范化回归测试。
 */
class TradeItemMaterialSupportTest {

    private final MaterialCatalog catalog = mock(MaterialCatalog.class);
    private final TradeItemMaterialSupport support = new TradeItemMaterialSupport(catalog);

    @Test
    void marksTheDependencyConstructorForSpringInjection() throws NoSuchMethodException {
        Constructor<TradeItemMaterialSupport> constructor = TradeItemMaterialSupport.class
                .getConstructor(MaterialCatalog.class, SnowflakeIdGenerator.class);

        assertThat(constructor.isAnnotationPresent(Autowired.class)).isTrue();
    }

    // ---------- 批号生成与规范化 ----------

    @Test
    void normalizeBatchNo_shouldGenerateSnowflakeIdWhenNull() {
        String generated = support.normalizeBatchNo(null, 1);

        assertThat(generated).isNotBlank();
        assertThat(Long.parseLong(generated)).isPositive();
        assertThat(generated).doesNotContain(".");
    }

    @Test
    void normalizeBatchNo_shouldGenerateSnowflakeIdWhenBlank() {
        String generated = support.normalizeBatchNo("   ", 1);

        assertThat(generated).isNotBlank();
    }

    @Test
    void normalizeBatchNo_shouldTrimProvidedValue() {
        assertThat(support.normalizeBatchNo("  B001  ", 1)).isEqualTo("B001");
    }

    @Test
    void normalizeBatchNo_shouldRejectValueLongerThan64() {
        assertThatThrownBy(() -> support.normalizeBatchNo("B".repeat(65), 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void normalizeRequiredBatchNo_shouldRejectNull() {
        assertThatThrownBy(() -> support.normalizeRequiredBatchNo(null, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("批号不能为空");
    }

    @Test
    void normalizeRequiredBatchNo_shouldRejectBlank() {
        assertThatThrownBy(() -> support.normalizeRequiredBatchNo("  ", 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("批号不能为空");
    }

    // ---------- 商品解析 ----------

    @Test
    void resolveMaterial_shouldResolveByMaterialIdAndMatchingCode() {
        when(catalog.listActiveMaterials())
                .thenReturn(List.of(new TradeMaterialSnapshot(500L, "M001")));

        TradeMaterialSnapshot snapshot = support.resolveMaterial(500L, "M001", 1);

        assertThat(snapshot.materialId()).isEqualTo(500L);
        assertThat(snapshot.materialCode()).isEqualTo("M001");
    }

    @Test
    void resolveMaterial_shouldRejectIdCodeMismatch() {
        when(catalog.listActiveMaterials())
                .thenReturn(List.of(new TradeMaterialSnapshot(500L, "M001")));

        assertThatThrownBy(() -> support.resolveMaterial(500L, "M002", 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    void resolveMaterial_shouldRejectUnknownMaterialId() {
        when(catalog.listActiveMaterials())
                .thenReturn(List.of(new TradeMaterialSnapshot(500L, "M001")));

        assertThatThrownBy(() -> support.resolveMaterial(99L, "M001", 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    @Test
    void resolveMaterial_shouldResolveByCodeWhenMaterialIdNull() {
        when(catalog.listActiveMaterials())
                .thenReturn(List.of(new TradeMaterialSnapshot(500L, "M001")));

        TradeMaterialSnapshot snapshot = support.resolveMaterial(null, "M001", 1);

        assertThat(snapshot.materialCode()).isEqualTo("M001");
    }

    @Test
    void resolveMaterial_shouldRejectBlankCode() {
        assertThatThrownBy(() -> support.resolveMaterial(null, "  ", 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品编码不能为空");
    }

    @Test
    void loadMaterialMap_shouldRejectMissingMaterial() {
        when(catalog.listActiveMaterials())
                .thenReturn(List.of(new TradeMaterialSnapshot(500L, "M001")));

        assertThatThrownBy(() -> support.loadMaterialMap(List.of("M999")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品不存在");
    }

    @Test
    void loadMaterialMap_shouldReturnEmptyForNullCodes() {
        assertThat(support.loadMaterialMap(null)).isEmpty();
    }

    // ---------- A3 回归: 请求级解析器只加载一次商品目录 ----------

    @Test
    void prepareResolver_shouldLoadMaterialCatalogExactlyOnceForMultipleResolves() {
        when(catalog.listActiveMaterials()).thenReturn(List.of(
                new TradeMaterialSnapshot(500L, "M001"),
                new TradeMaterialSnapshot(501L, "M002")
        ));
        MaterialResolver resolver = support.prepareResolver();

        assertThat(resolver.resolve(null, "M001", 1).materialId()).isEqualTo(500L);
        assertThat(resolver.resolve(501L, " M002 ", 2).materialId()).isEqualTo(501L);
        assertThat(resolver.resolve(null, "M002", 3).materialCode()).isEqualTo("M002");

        verify(catalog, times(1)).listActiveMaterials();
    }

    @Test
    void prepareResolver_shouldRejectIdCodeMismatch() {
        when(catalog.listActiveMaterials())
                .thenReturn(List.of(new TradeMaterialSnapshot(500L, "M001")));
        MaterialResolver resolver = support.prepareResolver();

        assertThatThrownBy(() -> resolver.resolve(500L, "M002", 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void prepareResolver_shouldRejectUnknownOrInactiveMaterialId() {
        when(catalog.listActiveMaterials())
                .thenReturn(List.of(new TradeMaterialSnapshot(500L, "M001")));
        MaterialResolver resolver = support.prepareResolver();

        assertThatThrownBy(() -> resolver.resolve(99L, "M001", 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    @Test
    void prepareResolver_shouldResolveByCodeWhenMaterialIdNull() {
        when(catalog.listActiveMaterials())
                .thenReturn(List.of(new TradeMaterialSnapshot(500L, "M001")));
        MaterialResolver resolver = support.prepareResolver();

        TradeMaterialSnapshot snapshot = resolver.resolve(null, "M001", 1);

        assertThat(snapshot.materialId()).isEqualTo(500L);
        assertThat(snapshot.materialCode()).isEqualTo("M001");
    }

    @Test
    void prepareResolver_shouldRejectBlankCode() {
        when(catalog.listActiveMaterials()).thenReturn(List.of());
        MaterialResolver resolver = support.prepareResolver();

        assertThatThrownBy(() -> resolver.resolve(null, "  ", 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品编码不能为空")
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void prepareResolver_shouldRejectUnknownCode() {
        when(catalog.listActiveMaterials())
                .thenReturn(List.of(new TradeMaterialSnapshot(500L, "M001")));
        MaterialResolver resolver = support.prepareResolver();

        assertThatThrownBy(() -> resolver.resolve(null, "M999", 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品不存在")
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    @Test
    void prepareResolver_shouldRejectUnknownCodeWhenCatalogEmpty() {
        when(catalog.listActiveMaterials()).thenReturn(List.of());
        MaterialResolver resolver = support.prepareResolver();

        assertThatThrownBy(() -> resolver.resolve(null, "M001", 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_ERROR);
        verify(catalog, times(1)).listActiveMaterials();
    }

    @Test
    void prepareResolver_shouldTrimCodeBeforeMatching() {
        when(catalog.listActiveMaterials())
                .thenReturn(List.of(new TradeMaterialSnapshot(500L, "M001")));
        MaterialResolver resolver = support.prepareResolver();

        assertThat(resolver.resolve(null, "  M001  ", 1).materialCode()).isEqualTo("M001");
        assertThat(resolver.resolve(500L, "  M001  ", 2).materialId()).isEqualTo(500L);
    }

    @Test
    void prepareResolver_shouldRejectLongMaxValueMaterialId() {
        when(catalog.listActiveMaterials())
                .thenReturn(List.of(new TradeMaterialSnapshot(500L, "M001")));
        MaterialResolver resolver = support.prepareResolver();

        assertThatThrownBy(() -> resolver.resolve(Long.MAX_VALUE, "M001", 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_ERROR);
    }
}
