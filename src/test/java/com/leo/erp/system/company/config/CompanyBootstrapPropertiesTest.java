package com.leo.erp.system.company.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyBootstrapPropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        var properties = new CompanyBootstrapProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getCompanyName()).isNull();
        assertThat(properties.getTaxNo()).isNull();
        assertThat(properties.getBankName()).isNull();
        assertThat(properties.getBankAccount()).isNull();
        assertThat(properties.getTaxRate()).isNull();
        assertThat(properties.getStatus()).isNull();
        assertThat(properties.getRemark()).isNull();
    }

    @Test
    void shouldSetAndGetEnabled() {
        var properties = new CompanyBootstrapProperties();
        properties.setEnabled(true);

        assertThat(properties.isEnabled()).isTrue();
    }

    @Test
    void shouldSetAndGetCompanyName() {
        var properties = new CompanyBootstrapProperties();
        properties.setCompanyName("测试公司");

        assertThat(properties.getCompanyName()).isEqualTo("测试公司");
    }

    @Test
    void shouldSetAndGetTaxNo() {
        var properties = new CompanyBootstrapProperties();
        properties.setTaxNo("91110000MA0XXXXXXX");

        assertThat(properties.getTaxNo()).isEqualTo("91110000MA0XXXXXXX");
    }

    @Test
    void shouldSetAndGetBankName() {
        var properties = new CompanyBootstrapProperties();
        properties.setBankName("中国银行");

        assertThat(properties.getBankName()).isEqualTo("中国银行");
    }

    @Test
    void shouldSetAndGetBankAccount() {
        var properties = new CompanyBootstrapProperties();
        properties.setBankAccount("6222021234567890123");

        assertThat(properties.getBankAccount()).isEqualTo("6222021234567890123");
    }

    @Test
    void shouldSetAndGetTaxRate() {
        var properties = new CompanyBootstrapProperties();
        properties.setTaxRate(new BigDecimal("0.13"));

        assertThat(properties.getTaxRate()).isEqualTo(new BigDecimal("0.13"));
    }

    @Test
    void shouldSetAndGetStatus() {
        var properties = new CompanyBootstrapProperties();
        properties.setStatus("正常");

        assertThat(properties.getStatus()).isEqualTo("正常");
    }

    @Test
    void shouldSetAndGetRemark() {
        var properties = new CompanyBootstrapProperties();
        properties.setRemark("备注信息");

        assertThat(properties.getRemark()).isEqualTo("备注信息");
    }
}
