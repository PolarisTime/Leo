package com.leo.erp.system.company.mapper;

import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.web.dto.CompanySettingResponse;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompanySettingMapperTest {

    private final CompanySettingMapper mapper = Mappers.getMapper(CompanySettingMapper.class);

    @Test
    void shouldMapEntityToResponse() {
        CompanySetting entity = new CompanySetting();
        entity.setId(1L);
        entity.setCompanyName("测试公司");
        entity.setTaxNo("TAX001");
        entity.setBankName("工商银行");
        entity.setBankAccount("6222000000000000");
        entity.setStatus("正常");
        entity.setRemark("备注");

        BigDecimal taxRate = new BigDecimal("0.13");
        List<CompanySettlementAccountResponse> accounts = List.of(
                new CompanySettlementAccountResponse(1L, "测试公司", "工商银行", "6222000000000000", "通用", "正常", "备注")
        );

        CompanySettingResponse response = mapper.toResponse(entity, taxRate, accounts);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.companyName()).isEqualTo("测试公司");
        assertThat(response.taxNo()).isEqualTo("TAX001");
        assertThat(response.bankName()).isEqualTo("工商银行");
        assertThat(response.taxRate()).isEqualByComparingTo("0.13");
        assertThat(response.settlementAccounts()).hasSize(1);
    }

    @Test
    void shouldMapWithEmptyAccounts() {
        CompanySetting entity = new CompanySetting();
        entity.setId(2L);
        entity.setCompanyName("公司B");
        entity.setTaxNo("TAX002");
        entity.setBankName("建设银行");
        entity.setBankAccount("6227000000000000");
        entity.setStatus("正常");

        CompanySettingResponse response = mapper.toResponse(entity, BigDecimal.ZERO, List.of());

        assertThat(response).isNotNull();
        assertThat(response.settlementAccounts()).isEmpty();
    }

    @Test
    void shouldReturnNullWhenAllSourcesAreNull() {
        assertThat(mapper.toResponse(null, null, null)).isNull();
    }

    @Test
    void shouldMapAdditionalSourcesWhenEntityIsNull() {
        List<CompanySettlementAccountResponse> accounts = List.of(
                new CompanySettlementAccountResponse(1L, "账户A", "银行A", "账号A", "通用", "正常", null)
        );

        CompanySettingResponse response = mapper.toResponse(null, new BigDecimal("0.13"), accounts);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNull();
        assertThat(response.taxRate()).isEqualByComparingTo("0.13");
        assertThat(response.settlementAccounts()).containsExactlyElementsOf(accounts);
        assertThat(response.settlementAccounts()).isNotSameAs(accounts);
    }

    @Test
    void shouldMapSettlementAccountsWhenEntityAndTaxRateAreNull() {
        List<CompanySettlementAccountResponse> accounts = List.of(
                new CompanySettlementAccountResponse(1L, "账户A", "银行A", "账号A", "通用", "正常", null)
        );

        CompanySettingResponse response = mapper.toResponse(null, null, accounts);

        assertThat(response).isNotNull();
        assertThat(response.taxRate()).isNull();
        assertThat(response.settlementAccounts()).containsExactlyElementsOf(accounts);
    }

    @Test
    void shouldKeepSettlementAccountsNullWhenSourceAccountsAreNull() {
        CompanySetting entity = new CompanySetting();
        entity.setId(3L);
        entity.setCompanyName("公司C");

        CompanySettingResponse response = mapper.toResponse(entity, null, null);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.settlementAccounts()).isNull();
    }
}
