package com.leo.erp.sales.contract;

import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.common.api.PageSortFieldCatalog;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.sales.contract.repository.SalesContractRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 销售合同模块注册回归: 附件业务类型、列表排序白名单与权限资源登记。
 */
class SalesContractRegistrationTest {

    @Test
    void moduleCatalog_registersSalesContractForAttachmentBinding() {
        ModuleCatalog moduleCatalog = new ModuleCatalog();

        assertThat(moduleCatalog.containsModule("sales-contract")).isTrue();
        assertThat(moduleCatalog.resolveModuleName("sales-contract")).isEqualTo("销售合同");
    }

    @Test
    void repository_exposesAttachmentExistencePort() {
        assertThat(RecordExistencePort.class.isAssignableFrom(SalesContractRepository.class)).isTrue();
    }

    @Test
    void pageSortFieldCatalog_allowsContractSortFields() {
        assertThat(PageSortFieldCatalog.fields("sales-contract"))
                .contains("contractNo", "name", "totalAmount", "totalTonnage", "status");
    }
}
