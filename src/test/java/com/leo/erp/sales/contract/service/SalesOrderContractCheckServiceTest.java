package com.leo.erp.sales.contract.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.contract.repository.SalesContractRepository;
import com.leo.erp.sales.contract.repository.SalesOrderContractMetricsRepository;
import com.leo.erp.sales.contract.web.dto.SalesContractCheckResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderContractCheckServiceTest {

    @Mock
    private SalesContractRepository contractRepository;

    @Mock
    private SalesOrderContractMetricsRepository orderMetricsRepository;

    private SalesOrderContractCheckService service() {
        return new SalesOrderContractCheckService(contractRepository, orderMetricsRepository);
    }

    @Test
    void check_rejectsNullProjectId() {
        assertThatThrownBy(() -> service().check(null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(contractRepository, orderMetricsRepository);
    }

    @Test
    void check_withoutQuotaContract_returnsZerosAndMessage() {
        when(contractRepository.countByProjectIdAndStatusInAndDeletedFlagFalse(
                7L, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES)).thenReturn(0L);

        SalesContractCheckResponse response = service().check(7L,
                new BigDecimal("9999"), new BigDecimal("99"), null);

        assertThat(response.hasContract()).isFalse();
        assertThat(response.contractAmount()).isEqualByComparingTo("0");
        assertThat(response.usedAmount()).isEqualByComparingTo("0");
        assertThat(response.remainingAmount()).isEqualByComparingTo("0");
        assertThat(response.exceededAmount()).isEqualByComparingTo("0");
        assertThat(response.exceededTonnage()).isEqualByComparingTo("0");
        assertThat(response.message()).contains("未关联有效销售合同");
        verifyNoInteractions(orderMetricsRepository);
    }

    @Test
    void check_withinBudget_includesPendingAndExcludesCurrentOrder() {
        stubContract(7L, 1L, "1000.00", "100.00000000");
        when(orderMetricsRepository.sumTotalAmountByProjectId(7L, 9L)).thenReturn(new BigDecimal("300.00"));
        when(orderMetricsRepository.sumTotalWeightByProjectId(7L, 9L)).thenReturn(new BigDecimal("30.00000000"));

        SalesContractCheckResponse response = service().check(7L,
                new BigDecimal("200.00"), new BigDecimal("20.00000000"), 9L);

        assertThat(response.hasContract()).isTrue();
        assertThat(response.usedAmount()).isEqualByComparingTo("500.00");
        assertThat(response.remainingAmount()).isEqualByComparingTo("500.00");
        assertThat(response.usedTonnage()).isEqualByComparingTo("50.00000000");
        assertThat(response.remainingTonnage()).isEqualByComparingTo("50.00000000");
        assertThat(response.exceededAmount()).isEqualByComparingTo("0");
        assertThat(response.exceededTonnage()).isEqualByComparingTo("0");
        assertThat(response.message()).isEqualTo("合同额度充足");
        verify(orderMetricsRepository).sumTotalAmountByProjectId(7L, 9L);
        verify(orderMetricsRepository).sumTotalWeightByProjectId(7L, 9L);
    }

    @Test
    void check_exactlyAtBoundary_isNotExceeded() {
        stubContract(7L, 1L, "100.00", "10.00000000");
        when(orderMetricsRepository.sumTotalAmountByProjectId(7L, null)).thenReturn(new BigDecimal("80.00"));
        when(orderMetricsRepository.sumTotalWeightByProjectId(7L, null)).thenReturn(new BigDecimal("8.00000000"));

        SalesContractCheckResponse response = service().check(7L,
                new BigDecimal("20.00"), new BigDecimal("2.00000000"), null);

        assertThat(response.remainingAmount()).isEqualByComparingTo("0");
        assertThat(response.remainingTonnage()).isEqualByComparingTo("0");
        assertThat(response.exceededAmount()).isEqualByComparingTo("0");
        assertThat(response.exceededTonnage()).isEqualByComparingTo("0");
        assertThat(response.message()).isEqualTo("合同额度充足");
    }

    @Test
    void check_exceedsAmountAndTonnage_reportsBoth() {
        stubContract(7L, 1L, "100.00", "10.00000000");
        when(orderMetricsRepository.sumTotalAmountByProjectId(7L, null)).thenReturn(new BigDecimal("90.00"));
        when(orderMetricsRepository.sumTotalWeightByProjectId(7L, null)).thenReturn(new BigDecimal("9.00000000"));

        SalesContractCheckResponse response = service().check(7L,
                new BigDecimal("20.00"), new BigDecimal("2.00000000"), null);

        assertThat(response.usedAmount()).isEqualByComparingTo("110.00");
        assertThat(response.exceededAmount()).isEqualByComparingTo("10.00");
        assertThat(response.exceededTonnage()).isEqualByComparingTo("1.00000000");
        assertThat(response.remainingAmount()).isEqualByComparingTo("-10.00");
        assertThat(response.message()).contains("金额", "吨位");
    }

    @Test
    void check_exceedsAmountOnly_reportsAmount() {
        stubContract(7L, 1L, "100.00", "100.00000000");
        when(orderMetricsRepository.sumTotalAmountByProjectId(7L, null)).thenReturn(new BigDecimal("95.00"));
        when(orderMetricsRepository.sumTotalWeightByProjectId(7L, null)).thenReturn(new BigDecimal("10.00000000"));

        SalesContractCheckResponse response = service().check(7L,
                new BigDecimal("10.00"), BigDecimal.ZERO, null);

        assertThat(response.exceededAmount()).isEqualByComparingTo("5.00");
        assertThat(response.exceededTonnage()).isEqualByComparingTo("0");
        assertThat(response.message()).contains("合同金额").doesNotContain("吨位");
    }

    @Test
    void check_nullAmountAndTonnage_treatedAsZero() {
        stubContract(7L, 1L, "100.00", "10.00000000");
        when(orderMetricsRepository.sumTotalAmountByProjectId(7L, null)).thenReturn(new BigDecimal("10.00"));
        when(orderMetricsRepository.sumTotalWeightByProjectId(7L, null)).thenReturn(new BigDecimal("1.00000000"));

        SalesContractCheckResponse response = service().check(7L, null, null, null);

        assertThat(response.usedAmount()).isEqualByComparingTo("10.00");
        assertThat(response.usedTonnage()).isEqualByComparingTo("1.00000000");
    }

    private void stubContract(Long projectId, long count, String amount, String tonnage) {
        when(contractRepository.countByProjectIdAndStatusInAndDeletedFlagFalse(
                projectId, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES)).thenReturn(count);
        when(contractRepository.sumTotalAmountByProjectIdAndStatusIn(
                projectId, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES)).thenReturn(new BigDecimal(amount));
        when(contractRepository.sumTotalTonnageByProjectIdAndStatusIn(
                projectId, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES)).thenReturn(new BigDecimal(tonnage));
    }
}
