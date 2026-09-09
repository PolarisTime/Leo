package com.leo.erp.system.company.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompanySettlementNameSyncServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void syncSettlementCompanyName_shouldBeNoOpWhenJdbcTemplateMissing() {
        CompanySettlementNameSyncService service = new CompanySettlementNameSyncService(null);

        assertThatCode(() -> service.syncSettlementCompanyName(1L, "公司A"))
                .doesNotThrowAnyException();
    }

    @Test
    void syncSettlementCompanyName_shouldBeNoOpForNullOrBlankName() {
        CompanySettlementNameSyncService service = new CompanySettlementNameSyncService(jdbcTemplate);

        service.syncSettlementCompanyName(1L, null);
        service.syncSettlementCompanyName(1L, "   ");
        service.syncSettlementCompanyName(null, "公司A");

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void syncSettlementCompanyName_shouldUpdateAllConfiguredTables() {
        CompanySettlementNameSyncService service = new CompanySettlementNameSyncService(jdbcTemplate);

        service.syncSettlementCompanyName(1L, "公司A");

        verify(jdbcTemplate, times(23)).update(anyString(), any(Object[].class));
        verify(jdbcTemplate, times(1)).update(
                eq("UPDATE sys_print_template SET settlement_company_name = ? WHERE settlement_company_id = ?"),
                eq("公司A"),
                eq(1L)
        );
        verify(jdbcTemplate, times(1)).update(
                eq("UPDATE md_project SET settlement_company_name = ? WHERE settlement_company_id = ?"),
                eq("公司A"),
                eq(1L)
        );
    }

    @Test
    void syncSettlementCompanyName_shouldIgnoreZeroId() {
        CompanySettlementNameSyncService service = new CompanySettlementNameSyncService(jdbcTemplate);

        service.syncSettlementCompanyName(0L, "公司A");

        verify(jdbcTemplate, times(23)).update(anyString(), any(Object[].class));
    }
}
