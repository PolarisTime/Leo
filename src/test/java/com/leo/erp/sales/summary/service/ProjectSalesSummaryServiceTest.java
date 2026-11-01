package com.leo.erp.sales.summary.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.sales.api.ProjectReconciledMetricsQuery;
import com.leo.erp.sales.contract.repository.SalesContractRepository;
import com.leo.erp.sales.contract.repository.SalesOrderContractMetricsRepository;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.returns.repository.SalesReturnRepository;
import com.leo.erp.sales.summary.web.dto.ProjectSalesSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectSalesSummaryServiceTest {

    private static final long PROJECT_ID = 7L;
    private static final long ORDER_ID = 9L;

    @Mock
    private ProjectQuery projectQuery;

    @Mock
    private SalesContractRepository contractRepository;

    @Mock
    private SalesOrderContractMetricsRepository orderMetricsRepository;

    @Mock
    private SalesOutboundRepository outboundRepository;

    @Mock
    private SalesReturnRepository returnRepository;

    @Mock
    private ProjectReconciledMetricsQuery reconciledMetricsQuery;

    private ProjectSalesSummaryService service() {
        return new ProjectSalesSummaryService(
                projectQuery,
                contractRepository,
                orderMetricsRepository,
                outboundRepository,
                returnRepository,
                reconciledMetricsQuery
        );
    }

    @Test
    void summarize_projectNotFound_throwsNotFound() {
        when(projectQuery.findEnabledById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().summarize(PROJECT_ID, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verifyNoInteractions(contractRepository, orderMetricsRepository,
                outboundRepository, returnRepository, reconciledMetricsQuery);
    }

    @Test
    void summarize_nullProjectId_throwsValidationErrorWithoutQuerying() {
        assertThatThrownBy(() -> service().summarize(null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(projectQuery, contractRepository, orderMetricsRepository,
                outboundRepository, returnRepository, reconciledMetricsQuery);
    }

    @Test
    void summarize_withoutContract_keepsMetricsAndReturnsNullUsageRates() {
        stubProject();
        stubOrders(null, "300.00", "30.00000000");
        stubDelivered("200.00", "50.00", "20.00000000", "5.00000000");
        stubReconciled("100.00", "10.00000000");
        when(contractRepository.countByProjectIdAndStatusInAndDeletedFlagFalse(
                PROJECT_ID, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES)).thenReturn(0L);

        ProjectSalesSummaryResponse response = service().summarize(PROJECT_ID, null, null, null);

        assertThat(response.projectId()).isEqualTo(PROJECT_ID);
        assertThat(response.projectName()).isEqualTo("项目A");
        assertThat(response.hasContract()).isFalse();
        assertThat(response.contractAmount()).isEqualByComparingTo("0");
        assertThat(response.contractTonnage()).isEqualByComparingTo("0");
        assertThat(response.amountUsageRate()).isNull();
        assertThat(response.tonnageUsageRate()).isNull();
        // 无合同不影响其余指标照常返回。
        assertThat(response.orderAmount()).isEqualByComparingTo("300.00");
        assertThat(response.orderTonnage()).isEqualByComparingTo("30.00000000");
        assertThat(response.deliveredAmount()).isEqualByComparingTo("150.00");
        assertThat(response.deliveredTonnage()).isEqualByComparingTo("15.00000000");
        assertThat(response.reconciledAmount()).isEqualByComparingTo("100.00");
        assertThat(response.reconciledTonnage()).isEqualByComparingTo("10.00000000");
        assertThat(response.unreconciledAmount()).isEqualByComparingTo("50.00");
        assertThat(response.unreconciledTonnage()).isEqualByComparingTo("5.00000000");
        assertThat(response.message()).contains("未关联有效销售合同");
    }

    @Test
    void summarize_withinQuota_returnsRemainingAndUsageRates() {
        stubProject();
        stubOrders(null, "300.00", "30.00000000");
        stubDelivered("0", "0", "0", "0");
        stubReconciled("0", "0");
        stubContract("1000.00", "100.00000000");

        ProjectSalesSummaryResponse response = service().summarize(PROJECT_ID, null, null, null);

        assertThat(response.hasContract()).isTrue();
        assertThat(response.contractAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.contractTonnage()).isEqualByComparingTo("100.00000000");
        assertThat(response.remainingAmount()).isEqualByComparingTo("700.00");
        assertThat(response.remainingTonnage()).isEqualByComparingTo("70.00000000");
        assertThat(response.exceededAmount()).isEqualByComparingTo("0");
        assertThat(response.exceededTonnage()).isEqualByComparingTo("0");
        assertThat(response.amountUsageRate()).isEqualByComparingTo("0.3000");
        assertThat(response.tonnageUsageRate()).isEqualByComparingTo("0.3000");
        assertThat(response.message()).isEqualTo("合同额度充足");
    }

    @Test
    void summarize_exceedsAmount_reportsAmountOnly() {
        stubProject();
        stubOrders(null, "150.00", "10.00000000");
        stubDelivered("0", "0", "0", "0");
        stubReconciled("0", "0");
        stubContract("100.00", "100.00000000");

        ProjectSalesSummaryResponse response = service().summarize(PROJECT_ID, null, null, null);

        assertThat(response.remainingAmount()).isEqualByComparingTo("-50.00");
        assertThat(response.exceededAmount()).isEqualByComparingTo("50.00");
        assertThat(response.exceededTonnage()).isEqualByComparingTo("0");
        assertThat(response.amountUsageRate()).isEqualByComparingTo("1.5000");
        assertThat(response.message()).contains("合同金额").doesNotContain("吨位");
    }

    @Test
    void summarize_exceedsTonnage_reportsTonnageOnly() {
        stubProject();
        stubOrders(null, "100.00", "12.00000000");
        stubDelivered("0", "0", "0", "0");
        stubReconciled("0", "0");
        stubContract("1000.00", "10.00000000");

        ProjectSalesSummaryResponse response = service().summarize(PROJECT_ID, null, null, null);

        assertThat(response.remainingAmount()).isEqualByComparingTo("900.00");
        assertThat(response.exceededAmount()).isEqualByComparingTo("0");
        assertThat(response.exceededTonnage()).isEqualByComparingTo("2.00000000");
        assertThat(response.tonnageUsageRate()).isEqualByComparingTo("1.2000");
        assertThat(response.message()).contains("合同吨位").doesNotContain("金额");
    }

    @Test
    void summarize_pendingAndExcludeOrderId_areComposedIntoOrderMetrics() {
        stubProject();
        stubOrders(ORDER_ID, "300.00", "30.00000000");
        stubDelivered("0", "0", "0", "0");
        stubReconciled("0", "0");
        stubContract("1000.00", "100.00000000");

        ProjectSalesSummaryResponse response = service().summarize(
                PROJECT_ID, ORDER_ID, new BigDecimal("20.50"), new BigDecimal("2.50000000"));

        assertThat(response.orderAmount()).isEqualByComparingTo("320.50");
        assertThat(response.orderTonnage()).isEqualByComparingTo("32.50000000");
        assertThat(response.remainingAmount()).isEqualByComparingTo("679.50");
        assertThat(response.remainingTonnage()).isEqualByComparingTo("67.50000000");
        verify(orderMetricsRepository).sumTotalAmountByProjectId(PROJECT_ID, ORDER_ID);
        verify(orderMetricsRepository).sumTotalWeightByProjectId(PROJECT_ID, ORDER_ID);
    }

    @Test
    void summarize_unreconciledNegative_isNotClampedToZero() {
        stubProject();
        stubOrders(null, "100.00", "10.00000000");
        stubDelivered("100.00", "0", "10.00000000", "0");
        stubReconciled("150.00", "15.00000000");
        when(contractRepository.countByProjectIdAndStatusInAndDeletedFlagFalse(
                PROJECT_ID, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES)).thenReturn(0L);

        ProjectSalesSummaryResponse response = service().summarize(PROJECT_ID, null, null, null);

        assertThat(response.unreconciledAmount()).isEqualByComparingTo("-50.00");
        assertThat(response.unreconciledTonnage()).isEqualByComparingTo("-5.00000000");
    }

    @Test
    void summarize_nullPendingValues_areTreatedAsZero() {
        stubProject();
        stubOrders(null, "0", "0");
        stubDelivered("0", "0", "0", "0");
        stubReconciled("0", "0");
        when(contractRepository.countByProjectIdAndStatusInAndDeletedFlagFalse(
                PROJECT_ID, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES)).thenReturn(0L);

        ProjectSalesSummaryResponse response = service().summarize(PROJECT_ID, null, null, null);

        assertThat(response.orderAmount()).isEqualByComparingTo("0");
        assertThat(response.orderTonnage()).isEqualByComparingTo("0");
        assertThat(response.deliveredAmount()).isEqualByComparingTo("0");
        assertThat(response.unreconciledAmount()).isEqualByComparingTo("0");
    }

    @Test
    void summarize_projectWithoutAnyDocuments_returnsAllZeros() {
        stubProject();
        stubOrders(null, "0", "0");
        stubDelivered("0", "0", "0", "0");
        stubReconciled("0", "0");
        when(contractRepository.countByProjectIdAndStatusInAndDeletedFlagFalse(
                PROJECT_ID, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES)).thenReturn(0L);

        ProjectSalesSummaryResponse response = service().summarize(PROJECT_ID, null, null, null);

        assertThat(response.orderAmount()).isEqualByComparingTo("0");
        assertThat(response.orderTonnage()).isEqualByComparingTo("0");
        assertThat(response.deliveredAmount()).isEqualByComparingTo("0");
        assertThat(response.deliveredTonnage()).isEqualByComparingTo("0");
        assertThat(response.reconciledAmount()).isEqualByComparingTo("0");
        assertThat(response.reconciledTonnage()).isEqualByComparingTo("0");
        assertThat(response.unreconciledAmount()).isEqualByComparingTo("0");
        assertThat(response.unreconciledTonnage()).isEqualByComparingTo("0");
        assertThat(response.remainingAmount()).isEqualByComparingTo("0");
        assertThat(response.remainingTonnage()).isEqualByComparingTo("0");
        assertThat(response.exceededAmount()).isEqualByComparingTo("0");
        assertThat(response.exceededTonnage()).isEqualByComparingTo("0");
        assertThat(response.hasContract()).isFalse();
    }

    @Test
    void summarize_normalizesAmountToTwoDecimalsAndTonnageToEight() {
        stubProject();
        stubOrders(null, "0", "0");
        stubDelivered("0", "0", "0", "0");
        stubReconciled("0", "0");
        stubContract("100.00", "100.00000000");

        ProjectSalesSummaryResponse response = service().summarize(
                PROJECT_ID, null, new BigDecimal("10.005"), new BigDecimal("1.123456789"));

        // 金额 2 位、吨位 8 位，均 HALF_UP。
        assertThat(response.orderAmount()).isEqualByComparingTo("10.01");
        assertThat(response.orderAmount().scale()).isEqualTo(2);
        assertThat(response.orderTonnage()).isEqualByComparingTo("1.12345679");
        assertThat(response.orderTonnage().scale()).isEqualTo(8);
        assertThat(response.unreconciledAmount().scale()).isEqualTo(2);
        assertThat(response.unreconciledTonnage().scale()).isEqualTo(8);
        // 使用率固定 4 位小数（HALF_UP）。
        assertThat(response.amountUsageRate()).isEqualByComparingTo("0.1001");
        assertThat(response.amountUsageRate().scale()).isEqualTo(4);
    }

    @Test
    void summarize_contractWithNonPositiveQuota_returnsNullUsageRatesButKeepsRemaining() {
        stubProject();
        stubOrders(null, "50.00", "5.00000000");
        stubDelivered("0", "0", "0", "0");
        stubReconciled("0", "0");
        stubContract("0", "0");

        ProjectSalesSummaryResponse response = service().summarize(PROJECT_ID, null, null, null);

        assertThat(response.hasContract()).isTrue();
        assertThat(response.amountUsageRate()).isNull();
        assertThat(response.tonnageUsageRate()).isNull();
        assertThat(response.remainingAmount()).isEqualByComparingTo("-50.00");
        assertThat(response.exceededAmount()).isEqualByComparingTo("50.00");
    }

    private void stubProject() {
        when(projectQuery.findEnabledById(PROJECT_ID)).thenReturn(Optional.of(
                new ProjectQuery.ProjectSnapshot(PROJECT_ID, "项目A", "项A", 10L, "CUST001")));
    }

    private void stubOrders(Long excludeOrderId, String amount, String tonnage) {
        when(orderMetricsRepository.sumTotalAmountByProjectId(PROJECT_ID, excludeOrderId))
                .thenReturn(new BigDecimal(amount));
        when(orderMetricsRepository.sumTotalWeightByProjectId(PROJECT_ID, excludeOrderId))
                .thenReturn(new BigDecimal(tonnage));
    }

    private void stubDelivered(String outboundAmount,
                               String returnAmount,
                               String outboundTonnage,
                               String returnTonnage) {
        when(outboundRepository.sumTotalAmountByProjectIdAndStatus(PROJECT_ID, StatusConstants.AUDITED))
                .thenReturn(new BigDecimal(outboundAmount));
        when(returnRepository.sumTotalAmountByProjectIdAndStatus(PROJECT_ID, StatusConstants.AUDITED))
                .thenReturn(new BigDecimal(returnAmount));
        when(outboundRepository.sumTotalWeightByProjectIdAndStatus(PROJECT_ID, StatusConstants.AUDITED))
                .thenReturn(new BigDecimal(outboundTonnage));
        when(returnRepository.sumTotalWeightByProjectIdAndStatus(PROJECT_ID, StatusConstants.AUDITED))
                .thenReturn(new BigDecimal(returnTonnage));
    }

    private void stubReconciled(String amount, String tonnage) {
        when(reconciledMetricsQuery.findByProjectId(PROJECT_ID))
                .thenReturn(new ProjectReconciledMetricsQuery.ProjectReconciledMetrics(
                        new BigDecimal(amount), new BigDecimal(tonnage)));
    }

    private void stubContract(String amount, String tonnage) {
        when(contractRepository.countByProjectIdAndStatusInAndDeletedFlagFalse(
                PROJECT_ID, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES)).thenReturn(1L);
        when(contractRepository.sumTotalAmountByProjectIdAndStatusIn(
                PROJECT_ID, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES)).thenReturn(new BigDecimal(amount));
        when(contractRepository.sumTotalTonnageByProjectIdAndStatusIn(
                PROJECT_ID, StatusConstants.SALES_CONTRACT_QUOTA_STATUSES)).thenReturn(new BigDecimal(tonnage));
    }
}
