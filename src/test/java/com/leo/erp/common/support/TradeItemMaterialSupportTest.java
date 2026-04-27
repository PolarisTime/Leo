package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.system.norule.service.NoRuleSequenceService;
import com.leo.erp.system.norule.service.SystemSwitchService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeItemMaterialSupportTest {

    @Test
    void shouldRejectMissingMaterialCode() {
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(repository(List.of()));

        assertThatThrownBy(() -> support.loadMaterialMap(List.of("MISSING")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品不存在");
    }

    @Test
    void shouldRequireBatchNoWhenMaterialBatchManaged() {
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(repository(List.of(batchManagedMaterial("MAT-001"))));

        assertThatThrownBy(() -> support.normalizeBatchNo(batchManagedMaterial("MAT-001"), " ", 1, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行商品已启用批号管理，批号不能为空");
    }

    @Test
    void shouldClearBatchNoWhenMaterialDoesNotManageBatch() {
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(repository(List.of(batchDisabledMaterial("MAT-001"))));

        String normalized = support.normalizeBatchNo(batchDisabledMaterial("MAT-001"), "BATCH-001", 1, true);

        assertThat(normalized).isNull();
    }

    @Test
    void shouldAutoGenerateBatchNoWhenSwitchEnabled() {
        SystemSwitchService systemSwitchService = mock(SystemSwitchService.class);
        NoRuleSequenceService noRuleSequenceService = mock(NoRuleSequenceService.class);
        when(systemSwitchService.shouldAutoGenerateBatchNo()).thenReturn(true);
        when(noRuleSequenceService.nextValue(NoRuleSequenceService.BATCH_NO_RULE_CODE)).thenReturn("2026LOT000001");
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                repository(List.of(batchManagedMaterial("MAT-001"))),
                null,
                systemSwitchService,
                noRuleSequenceService
        );

        String normalized = support.normalizeBatchNo(batchManagedMaterial("MAT-001"), " ", 1, true);

        assertThat(normalized).isEqualTo("2026LOT000001");
        verify(noRuleSequenceService).nextValue(NoRuleSequenceService.BATCH_NO_RULE_CODE);
    }

    @Test
    void shouldKeepManualBatchNoWhenSwitchEnabled() {
        SystemSwitchService systemSwitchService = mock(SystemSwitchService.class);
        NoRuleSequenceService noRuleSequenceService = mock(NoRuleSequenceService.class);
        when(systemSwitchService.shouldAutoGenerateBatchNo()).thenReturn(true);
        TradeItemMaterialSupport support = new TradeItemMaterialSupport(
                repository(List.of(batchManagedMaterial("MAT-001"))),
                null,
                systemSwitchService,
                noRuleSequenceService
        );

        String normalized = support.normalizeBatchNo(batchManagedMaterial("MAT-001"), " LOT-001 ", 1, true);

        assertThat(normalized).isEqualTo("LOT-001");
    }

    private Material batchManagedMaterial(String materialCode) {
        Material material = new Material();
        material.setMaterialCode(materialCode);
        material.setBatchNoEnabled(Boolean.TRUE);
        return material;
    }

    private Material batchDisabledMaterial(String materialCode) {
        Material material = new Material();
        material.setMaterialCode(materialCode);
        material.setBatchNoEnabled(Boolean.FALSE);
        return material;
    }

    @SuppressWarnings("unchecked")
    private MaterialRepository repository(List<Material> materials) {
        return (MaterialRepository) Proxy.newProxyInstance(
                MaterialRepository.class.getClassLoader(),
                new Class[]{MaterialRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByDeletedFlagFalseOrderByMaterialCodeAsc" -> materials;
                    case "toString" -> "MaterialRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
