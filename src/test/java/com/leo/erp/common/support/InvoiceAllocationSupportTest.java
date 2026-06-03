package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.system.company.service.CompanySettingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvoiceAllocationSupportTest {

    @Test
    void shouldUseProvidedWeightTon() {
        BigDecimal result = InvoiceAllocationSupport.resolveWeightTon(10, BigDecimal.ONE, new BigDecimal("5.000"));
        assertThat(result).isEqualByComparingTo(new BigDecimal("5.000"));
    }

    @Test
    void shouldCalculateWeightTonFromQuantityAndPieceWeight() {
        BigDecimal result = InvoiceAllocationSupport.resolveWeightTon(10, new BigDecimal("0.500"), null);
        assertThat(result).isEqualByComparingTo(new BigDecimal("5.000"));
    }

    @Test
    void shouldReturnZeroWhenWeightTonIsZero() {
        BigDecimal result = InvoiceAllocationSupport.resolveWeightTon(10, new BigDecimal("0.500"), BigDecimal.ZERO);
        assertThat(result).isEqualByComparingTo(new BigDecimal("5.000"));
    }

    @Test
    void shouldCalculateTaxAmount() {
        CompanySettingService service = mock(CompanySettingService.class);
        when(service.resolveCurrentTaxRate()).thenReturn(new BigDecimal("0.1300"));

        BigDecimal result = InvoiceAllocationSupport.calculateTaxAmount(
                new BigDecimal("100.00"), null, service);

        assertThat(result).isEqualByComparingTo(new BigDecimal("13.00"));
    }

    @Test
    void shouldReturnRequestedTaxAmountWhenRateIsZero() {
        CompanySettingService service = mock(CompanySettingService.class);
        when(service.resolveCurrentTaxRate()).thenReturn(BigDecimal.ZERO);

        BigDecimal result = InvoiceAllocationSupport.calculateTaxAmount(
                new BigDecimal("100.00"), new BigDecimal("5.00"), service);

        assertThat(result).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    void shouldReturnZeroWhenRateIsZeroAndNoRequestedAmount() {
        CompanySettingService service = mock(CompanySettingService.class);
        when(service.resolveCurrentTaxRate()).thenReturn(BigDecimal.ZERO);

        BigDecimal result = InvoiceAllocationSupport.calculateTaxAmount(
                new BigDecimal("100.00"), null, service);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldValidateDeclaredAmount() {
        InvoiceAllocationSupport.validateDeclaredAmount("金额", new BigDecimal("100.00"), new BigDecimal("100.00"));
    }

    @Test
    void shouldThrowWhenDeclaredAmountMismatch() {
        assertThatThrownBy(() -> InvoiceAllocationSupport.validateDeclaredAmount(
                "金额", new BigDecimal("100.00"), new BigDecimal("90.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("与明细计算结果不一致");
    }

    @Test
    void shouldAllowNullDeclaredAmount() {
        InvoiceAllocationSupport.validateDeclaredAmount("金额", null, new BigDecimal("100.00"));
    }

    @Test
    void shouldMergeAllocationProgress() {
        InvoiceAllocationSupport.AllocationProgress p1 = new InvoiceAllocationSupport.AllocationProgress(
                new BigDecimal("10.000"), new BigDecimal("100.00"));
        InvoiceAllocationSupport.AllocationProgress p2 = new InvoiceAllocationSupport.AllocationProgress(
                new BigDecimal("5.000"), new BigDecimal("50.00"));

        InvoiceAllocationSupport.AllocationProgress merged = p1.merge(p2);

        assertThat(merged.weightTon()).isEqualTo(new BigDecimal("15.000"));
        assertThat(merged.amount()).isEqualTo(new BigDecimal("150.00"));
    }

    @Test
    void shouldHaveEmptyProgress() {
        assertThat(InvoiceAllocationSupport.AllocationProgress.EMPTY.weightTon()).isEqualTo(BigDecimal.ZERO);
        assertThat(InvoiceAllocationSupport.AllocationProgress.EMPTY.amount()).isEqualTo(BigDecimal.ZERO);
    }
}
