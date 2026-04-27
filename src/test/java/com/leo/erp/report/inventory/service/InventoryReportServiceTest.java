package com.leo.erp.report.inventory.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.report.inventory.repository.InventoryReportQueryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReportServiceTest {

    @Test
    void shouldNormalizeWarehouseNameAndCategoryWithoutRejectingUnknownValues() {
        InventoryReportQueryRepository repo = mock(InventoryReportQueryRepository.class);
        PageQuery query = new PageQuery(0, 20, null, null);
        when(repo.page(query, null, "任意仓库名", "任意类别")).thenReturn(new PageImpl<>(List.of()));
        InventoryReportService service = new InventoryReportService(repo);

        assertThatCode(() -> service.page(query, null, " 任意仓库名 ", " 任意类别 "))
                .doesNotThrowAnyException();
        verify(repo).page(query, null, "任意仓库名", "任意类别");
    }
}
