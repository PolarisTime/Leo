package com.leo.erp.report.io.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IoReportServiceTest {

    @Test
    void shouldRejectUnknownBusinessTypeFilter() {
        IoReportService service = new IoReportService(null);

        assertThatThrownBy(() -> service.page(new PageQuery(0, 20, null, null), null, "未知类型", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("businessType 不合法");
    }

    @Test
    void shouldRejectInvertedDateRange() {
        IoReportService service = new IoReportService(null);

        assertThatThrownBy(() -> service.page(
                new PageQuery(0, 20, null, null),
                null,
                null,
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 4, 1)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("startDate 不能晚于 endDate");
    }
}
