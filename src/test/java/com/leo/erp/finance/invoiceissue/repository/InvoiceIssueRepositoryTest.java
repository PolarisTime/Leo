package com.leo.erp.finance.invoiceissue.repository;

import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceIssueRepositoryTest {

    @Mock
    private InvoiceIssueRepository repository;

    @Test
    void existsByIssueNoAndDeletedFlagFalse_shouldReturnTrueWhenExists() {
        when(repository.existsByIssueNoAndDeletedFlagFalse("I001")).thenReturn(true);

        boolean result = repository.existsByIssueNoAndDeletedFlagFalse("I001");

        assertThat(result).isTrue();
    }

    @Test
    void existsByIssueNoAndDeletedFlagFalse_shouldReturnFalseWhenNotExists() {
        when(repository.existsByIssueNoAndDeletedFlagFalse("NONEXIST")).thenReturn(false);

        boolean result = repository.existsByIssueNoAndDeletedFlagFalse("NONEXIST");

        assertThat(result).isFalse();
    }

    @Test
    void existsByIssueNoAndDeletedFlagFalse_shouldReturnFalseWhenDeleted() {
        when(repository.existsByIssueNoAndDeletedFlagFalse("I002")).thenReturn(false);

        boolean result = repository.existsByIssueNoAndDeletedFlagFalse("I002");

        assertThat(result).isFalse();
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnIssueWhenExistsAndNotDeleted() {
        InvoiceIssue issue = new InvoiceIssue();
        issue.setId(3L);
        issue.setIssueNo("I003");
        issue.setInvoiceNo("INV003");
        issue.setCustomerName("客户C");
        issue.setProjectName("项目C");
        issue.setInvoiceDate(LocalDate.now());
        issue.setInvoiceType("增值税专用发票");
        issue.setAmount(new BigDecimal("40000.00"));
        issue.setTaxAmount(new BigDecimal("5200.00"));
        issue.setStatus("已确认");
        issue.setOperatorName("王五");
        issue.setDeletedFlag(false);

        when(repository.findByIdAndDeletedFlagFalse(3L)).thenReturn(Optional.of(issue));

        Optional<InvoiceIssue> result = repository.findByIdAndDeletedFlagFalse(3L);

        assertThat(result).isPresent();
        assertThat(result.get().getIssueNo()).isEqualTo("I003");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(4L)).thenReturn(Optional.empty());

        Optional<InvoiceIssue> result = repository.findByIdAndDeletedFlagFalse(4L);

        assertThat(result).isEmpty();
    }

    @Test
    void findAllByDeletedFlagFalse_shouldReturnOnlyNonDeleted() {
        InvoiceIssue issue1 = new InvoiceIssue();
        issue1.setId(5L);
        issue1.setIssueNo("I005");
        issue1.setInvoiceNo("INV005");
        issue1.setCustomerName("客户E");
        issue1.setProjectName("项目E");
        issue1.setInvoiceDate(LocalDate.now());
        issue1.setInvoiceType("增值税专用发票");
        issue1.setAmount(new BigDecimal("60000.00"));
        issue1.setTaxAmount(new BigDecimal("7800.00"));
        issue1.setStatus("已确认");
        issue1.setOperatorName("赵六");
        issue1.setDeletedFlag(false);

        when(repository.findAllByDeletedFlagFalse()).thenReturn(List.of(issue1));

        List<InvoiceIssue> results = repository.findAllByDeletedFlagFalse();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getIssueNo()).isEqualTo("I005");
    }

    @Test
    void summarizeAllocatedBySourceSalesOrderItemIds_shouldReturnSummary() {
        InvoiceIssueRepository.SourceAllocationSummary summary = new InvoiceIssueRepository.SourceAllocationSummary() {
            @Override
            public Long getSourceSalesOrderItemId() {
                return 100L;
            }

            @Override
            public Long getTotalQuantity() {
                return 10L;
            }

            @Override
            public BigDecimal getTotalWeightTon() {
                return new BigDecimal("10.000");
            }

            @Override
            public BigDecimal getTotalAmount() {
                return new BigDecimal("80000.00");
            }
        };

        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(List.of(100L), null))
                .thenReturn(List.of(summary));

        List<InvoiceIssueRepository.SourceAllocationSummary> results = repository.summarizeAllocatedBySourceSalesOrderItemIds(
                List.of(100L), null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSourceSalesOrderItemId()).isEqualTo(100L);
        assertThat(results.get(0).getTotalWeightTon()).isEqualByComparingTo("10.000");
        assertThat(results.get(0).getTotalAmount()).isEqualByComparingTo("80000.00");
    }

    @Test
    void summarizeAllocatedBySourceSalesOrderItemIds_shouldExcludeCurrentIssue() {
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(List.of(200L), 8L))
                .thenReturn(List.of());

        List<InvoiceIssueRepository.SourceAllocationSummary> results = repository.summarizeAllocatedBySourceSalesOrderItemIds(
                List.of(200L), 8L);

        assertThat(results).isEmpty();
    }
}
