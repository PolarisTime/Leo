package com.leo.erp.report.inventory.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryReportServiceTest {

    @Test
    void shouldRejectUnknownWarehouseNameFilter() {
        InventoryReportService service = new InventoryReportService(null);

        assertThatThrownBy(() -> service.page(new PageQuery(0, 20, null, null), null, "未知仓库", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("warehouseName 不合法");
    }

    @Test
    void shouldRejectUnknownCategoryFilter() {
        InventoryReportService service = new InventoryReportService(null);

        assertThatThrownBy(() -> service.page(new PageQuery(0, 20, null, null), null, null, "未知类别"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("category 不合法");
    }
}
