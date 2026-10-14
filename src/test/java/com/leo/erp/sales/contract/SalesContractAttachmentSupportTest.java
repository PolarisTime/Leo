package com.leo.erp.sales.contract;

import com.leo.erp.attachment.service.RecordExistenceRegistry;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.sales.contract.domain.entity.SalesContract;
import com.leo.erp.sales.contract.repository.SalesContractRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 销售合同附件支持契约。
 *
 * <p>附件绑定链路要求目标模块同时具备两处注册：{@link ModuleCatalog} 的模块键（决定
 * {@code module_key} 是否合法）与 {@link com.leo.erp.attachment.api.RecordExistencePort}
 * 的记录存在性校验（决定目标业务记录是否活跃）。任一处缺失，前端调用
 * {@code /api/v2.0/attachments/bindings} 都会以「当前模块不支持附件」失败，
 * 因此这里把「销售合同可用附件」固化为可回归的契约，防止后续重构静默移除注册。</p>
 */
class SalesContractAttachmentSupportTest {

    private final ModuleCatalog moduleCatalog = new ModuleCatalog();

    /** default 方法需要走真实实现，否则 moduleKey()/existsActive() 会被 mock 成 null/false。 */
    private SalesContractRepository repository() {
        return mock(SalesContractRepository.class, CALLS_REAL_METHODS);
    }

    @Test
    void moduleCatalog_registersSalesContractModuleKey() {
        assertThat(moduleCatalog.containsModule(ModuleKeys.SALES_CONTRACT)).isTrue();
        assertThat(moduleCatalog.resolveModuleName(ModuleKeys.SALES_CONTRACT)).isEqualTo("销售合同");
    }

    @Test
    void moduleCatalog_normalizesSalesContractModuleKeyVariants() {
        // 前端传参、历史数据可能带大小写差异与前导斜杠，归一化后必须仍指向同一模块键。
        assertThat(moduleCatalog.normalizeModuleKey("  /Sales-Contract ")).isEqualTo(ModuleKeys.SALES_CONTRACT);
        assertThat(moduleCatalog.containsModule("  /SALES-CONTRACT ")).isTrue();
    }

    @Test
    void repository_declaresSalesContractModuleKey() {
        assertThat(repository().moduleKey()).isEqualTo(ModuleKeys.SALES_CONTRACT);
    }

    @Test
    void existsActive_excludesSoftDeletedContract() {
        SalesContractRepository repository = repository();
        when(repository.existsByIdAndDeletedFlagFalse(100L)).thenReturn(true);
        when(repository.existsByIdAndDeletedFlagFalse(200L)).thenReturn(false);

        assertThat(repository.existsActive(100L)).isTrue();
        assertThat(repository.existsActive(200L)).isFalse();
    }

    @Test
    void lockActive_reflectsActiveContractLookup() {
        SalesContractRepository repository = repository();
        when(repository.findActiveForAttachmentBinding(100L)).thenReturn(Optional.of(new SalesContract()));
        when(repository.findActiveForAttachmentBinding(200L)).thenReturn(Optional.empty());

        assertThat(repository.lockActive(100L)).isTrue();
        assertThat(repository.lockActive(200L)).isFalse();
    }

    @Test
    void recordExistenceRegistry_resolvesSalesContractPort() {
        SalesContractRepository repository = repository();
        RecordExistenceRegistry registry = new RecordExistenceRegistry(List.of(repository), moduleCatalog);

        assertThat(registry.require(ModuleKeys.SALES_CONTRACT)).isSameAs(repository);
    }

    @Test
    void recordExistenceRegistry_rejectsModuleWithoutPort() {
        RecordExistenceRegistry registry = new RecordExistenceRegistry(List.of(repository()), moduleCatalog);

        assertThatThrownBy(() -> registry.require("sales-contract-archive"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
