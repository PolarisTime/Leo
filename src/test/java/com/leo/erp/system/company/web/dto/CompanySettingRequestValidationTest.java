package com.leo.erp.system.company.web.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompanySettingRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldPass_whenValidRequest() {
        var request = new CompanySettingRequest(
                "测试公司", "91110000MA0XXXXXXX",
                List.of(new CompanySettlementAccountRequest(null, "账户", "银行", "账号", "通用", "正常", "")),
                "正常", "备注"
        );

        var violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldFail_whenCompanyNameBlank() {
        var request = new CompanySettingRequest(
                "", "91110000MA0XXXXXXX",
                List.of(new CompanySettlementAccountRequest(null, "账户", "银行", "账号", "通用", "正常", "")),
                "正常", null
        );

        var violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("companyName"))).isTrue();
    }

    @Test
    void shouldFail_whenTaxNoBlank() {
        var request = new CompanySettingRequest(
                "测试公司", "",
                List.of(new CompanySettlementAccountRequest(null, "账户", "银行", "账号", "通用", "正常", "")),
                "正常", null
        );

        var violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("taxNo"))).isTrue();
    }

    @Test
    void shouldFail_whenSettlementAccountsEmpty() {
        var request = new CompanySettingRequest(
                "测试公司", "91110000MA0XXXXXXX",
                List.of(),
                "正常", null
        );

        var violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("settlementAccounts"))).isTrue();
    }

    @Test
    void shouldFail_whenStatusBlank() {
        var request = new CompanySettingRequest(
                "测试公司", "91110000MA0XXXXXXX",
                List.of(new CompanySettlementAccountRequest(null, "账户", "银行", "账号", "通用", "正常", "")),
                "", null
        );

        var violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("status"))).isTrue();
    }
}
