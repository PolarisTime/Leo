package com.leo.erp.statement.customer.service;

import com.leo.erp.sales.api.ProjectReconciledMetricsQuery;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerStatementProjectMetricsAdapterTest {

    @Mock
    private CustomerStatementRepository repository;

    private CustomerStatementProjectMetricsAdapter adapter() {
        return new CustomerStatementProjectMetricsAdapter(repository);
    }

    @Test
    void findByProjectId_nullProjectId_returnsZeroWithoutQuery() {
        ProjectReconciledMetricsQuery.ProjectReconciledMetrics metrics = adapter().findByProjectId(null);

        assertThat(metrics.amount()).isEqualByComparingTo("0");
        assertThat(metrics.tonnage()).isEqualByComparingTo("0");
        verifyNoInteractions(repository);
    }

    @Test
    void findByProjectId_nonPositiveProjectId_returnsZeroWithoutQuery() {
        ProjectReconciledMetricsQuery.ProjectReconciledMetrics metrics = adapter().findByProjectId(0L);

        assertThat(metrics.amount()).isEqualByComparingTo("0");
        assertThat(metrics.tonnage()).isEqualByComparingTo("0");
        verifyNoInteractions(repository);
    }

    @Test
    void findByProjectId_mapsSumsWithAmountTwoDecimalsAndTonnageEight() {
        when(repository.sumItemAmountByProjectId(7L)).thenReturn(new BigDecimal("1234.5"));
        when(repository.sumItemWeightTonByProjectId(7L)).thenReturn(new BigDecimal("12.345678901"));

        ProjectReconciledMetricsQuery.ProjectReconciledMetrics metrics = adapter().findByProjectId(7L);

        assertThat(metrics.amount()).isEqualByComparingTo("1234.50");
        assertThat(metrics.amount().scale()).isEqualTo(2);
        assertThat(metrics.tonnage()).isEqualByComparingTo("12.34567890");
        assertThat(metrics.tonnage().scale()).isEqualTo(8);
    }

    @Test
    void findByProjectId_keepsNegativeNetAmountFromRedStatements() {
        when(repository.sumItemAmountByProjectId(7L)).thenReturn(new BigDecimal("-88.00"));
        when(repository.sumItemWeightTonByProjectId(7L)).thenReturn(new BigDecimal("-8.80000000"));

        ProjectReconciledMetricsQuery.ProjectReconciledMetrics metrics = adapter().findByProjectId(7L);

        assertThat(metrics.amount()).isEqualByComparingTo("-88.00");
        assertThat(metrics.tonnage()).isEqualByComparingTo("-8.80000000");
    }

    @Test
    void findByProjectId_nullSums_areTreatedAsZero() {
        when(repository.sumItemAmountByProjectId(7L)).thenReturn(null);
        when(repository.sumItemWeightTonByProjectId(7L)).thenReturn(null);

        ProjectReconciledMetricsQuery.ProjectReconciledMetrics metrics = adapter().findByProjectId(7L);

        assertThat(metrics.amount()).isEqualByComparingTo("0");
        assertThat(metrics.tonnage()).isEqualByComparingTo("0");
    }
}
