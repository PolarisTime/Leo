package com.leo.erp.finance.invoicereceipt.repository;

import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceipt;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceiptItem;
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
class InvoiceReceiptRepositoryTest {

    @Mock
    private InvoiceReceiptRepository repository;

    @Test
    void existsByReceiveNoAndDeletedFlagFalse_shouldReturnTrueWhenExists() {
        when(repository.existsByReceiveNoAndDeletedFlagFalse("R001")).thenReturn(true);

        boolean result = repository.existsByReceiveNoAndDeletedFlagFalse("R001");

        assertThat(result).isTrue();
    }

    @Test
    void existsByReceiveNoAndDeletedFlagFalse_shouldReturnFalseWhenNotExists() {
        when(repository.existsByReceiveNoAndDeletedFlagFalse("NONEXIST")).thenReturn(false);

        boolean result = repository.existsByReceiveNoAndDeletedFlagFalse("NONEXIST");

        assertThat(result).isFalse();
    }

    @Test
    void existsByReceiveNoAndDeletedFlagFalse_shouldReturnFalseWhenDeleted() {
        when(repository.existsByReceiveNoAndDeletedFlagFalse("R002")).thenReturn(false);

        boolean result = repository.existsByReceiveNoAndDeletedFlagFalse("R002");

        assertThat(result).isFalse();
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnReceiptWhenExistsAndNotDeleted() {
        InvoiceReceipt receipt = new InvoiceReceipt();
        receipt.setId(3L);
        receipt.setReceiveNo("R003");
        receipt.setInvoiceNo("INV003");
        receipt.setSupplierName("供应商C");
        receipt.setInvoiceDate(LocalDate.now());
        receipt.setInvoiceType("增值税专用发票");
        receipt.setAmount(new BigDecimal("8000.00"));
        receipt.setTaxAmount(new BigDecimal("1040.00"));
        receipt.setStatus("已确认");
        receipt.setOperatorName("李四");
        receipt.setDeletedFlag(false);

        when(repository.findByIdAndDeletedFlagFalse(3L)).thenReturn(Optional.of(receipt));

        Optional<InvoiceReceipt> result = repository.findByIdAndDeletedFlagFalse(3L);

        assertThat(result).isPresent();
        assertThat(result.get().getReceiveNo()).isEqualTo("R003");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(4L)).thenReturn(Optional.empty());

        Optional<InvoiceReceipt> result = repository.findByIdAndDeletedFlagFalse(4L);

        assertThat(result).isEmpty();
    }

    @Test
    void findAllByDeletedFlagFalse_shouldReturnOnlyNonDeleted() {
        InvoiceReceipt receipt1 = new InvoiceReceipt();
        receipt1.setId(5L);
        receipt1.setReceiveNo("R005");
        receipt1.setInvoiceNo("INV005");
        receipt1.setSupplierName("供应商E");
        receipt1.setInvoiceDate(LocalDate.now());
        receipt1.setInvoiceType("增值税专用发票");
        receipt1.setAmount(new BigDecimal("7000.00"));
        receipt1.setTaxAmount(new BigDecimal("910.00"));
        receipt1.setStatus("已确认");
        receipt1.setOperatorName("王五");
        receipt1.setDeletedFlag(false);

        when(repository.findAllByDeletedFlagFalse()).thenReturn(List.of(receipt1));

        List<InvoiceReceipt> results = repository.findAllByDeletedFlagFalse();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getReceiveNo()).isEqualTo("R005");
    }

    @Test
    void summarizeAllocatedBySourcePurchaseOrderItemIds_shouldReturnSummary() {
        InvoiceReceiptRepository.SourceAllocationSummary summary = new InvoiceReceiptRepository.SourceAllocationSummary() {
            @Override
            public Long getSourcePurchaseOrderItemId() {
                return 100L;
            }

            @Override
            public Long getTotalQuantity() {
                return 5L;
            }

            @Override
            public BigDecimal getTotalWeightTon() {
                return new BigDecimal("5.000");
            }

            @Override
            public BigDecimal getTotalAmount() {
                return new BigDecimal("20000.00");
            }
        };

        when(repository.summarizeAllocatedBySourcePurchaseOrderItemIds(List.of(100L), null))
                .thenReturn(List.of(summary));

        List<InvoiceReceiptRepository.SourceAllocationSummary> results = repository.summarizeAllocatedBySourcePurchaseOrderItemIds(
                List.of(100L), null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSourcePurchaseOrderItemId()).isEqualTo(100L);
        assertThat(results.get(0).getTotalWeightTon()).isEqualByComparingTo("5.000");
        assertThat(results.get(0).getTotalAmount()).isEqualByComparingTo("20000.00");
    }

    @Test
    void summarizeAllocatedBySourcePurchaseOrderItemIds_shouldExcludeCurrentReceipt() {
        when(repository.summarizeAllocatedBySourcePurchaseOrderItemIds(List.of(200L), 8L))
                .thenReturn(List.of());

        List<InvoiceReceiptRepository.SourceAllocationSummary> results = repository.summarizeAllocatedBySourcePurchaseOrderItemIds(
                List.of(200L), 8L);

        assertThat(results).isEmpty();
    }
}
