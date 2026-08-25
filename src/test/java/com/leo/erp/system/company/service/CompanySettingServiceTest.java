package com.leo.erp.system.company.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.mapper.CompanySettingMapper;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.leo.erp.common.support.StatusConstants.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanySettingServiceTest {

    @Test
    void requireActiveSettlementCompanySnapshot_shouldExposeOnlyPublicFields() {
        CompanySettingRepository repository = mock(CompanySettingRepository.class);
        CompanySetting company = new CompanySetting();
        company.setId(30L);
        company.setCompanyName("结算主体A");
        when(repository.findByIdAndStatusAndDeletedFlagFalse(30L, NORMAL))
                .thenReturn(Optional.of(company));

        CompanySettingService service = new CompanySettingService(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(CompanySettingMapper.class),
                mock(DashboardSummaryService.class),
                mock(ObjectMapper.class)
        );

        assertThat(service.requireActiveSettlementCompanySnapshot(30L))
                .isEqualTo(new com.leo.erp.system.company.api.SettlementCompanySnapshot(30L, "结算主体A"));
    }
}
