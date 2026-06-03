package com.leo.erp.finance.receipt.repository;

import com.leo.erp.finance.receipt.domain.entity.Receipt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptRepositoryTest {

    @Mock
    private ReceiptRepository repository;

    @Test
    void existsByReceiptNoAndDeletedFlagFalse_shouldReturnTrueWhenExists() {
        when(repository.existsByReceiptNoAndDeletedFlagFalse("R001")).thenReturn(true);

        boolean result = repository.existsByReceiptNoAndDeletedFlagFalse("R001");

        assertThat(result).isTrue();
    }

    @Test
    void existsByReceiptNoAndDeletedFlagFalse_shouldReturnFalseWhenNotExists() {
        when(repository.existsByReceiptNoAndDeletedFlagFalse("NONEXIST")).thenReturn(false);

        boolean result = repository.existsByReceiptNoAndDeletedFlagFalse("NONEXIST");

        assertThat(result).isFalse();
    }

    @Test
    void existsByReceiptNoAndDeletedFlagFalse_shouldReturnFalseWhenDeleted() {
        when(repository.existsByReceiptNoAndDeletedFlagFalse("R002")).thenReturn(false);

        boolean result = repository.existsByReceiptNoAndDeletedFlagFalse("R002");

        assertThat(result).isFalse();
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnReceiptWhenExistsAndNotDeleted() {
        Receipt receipt = new Receipt();
        receipt.setId(1L);
        receipt.setReceiptNo("R001");
        receipt.setDeletedFlag(false);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(receipt));

        Optional<Receipt> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getReceiptNo()).isEqualTo("R001");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<Receipt> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void sumAmountBySourceStatementIdAndStatus_shouldReturnSum() {
        when(repository.sumAmountBySourceStatementIdAndStatus(1L, "CONFIRMED"))
                .thenReturn(new BigDecimal("3000.00"));

        BigDecimal result = repository.sumAmountBySourceStatementIdAndStatus(1L, "CONFIRMED");

        assertThat(result).isEqualByComparingTo("3000.00");
    }

    @Test
    void sumAmountBySourceStatementIdAndStatusExcludingId_shouldReturnSumExcludingSpecifiedId() {
        when(repository.sumAmountBySourceStatementIdAndStatusExcludingId(1L, "CONFIRMED", 1L))
                .thenReturn(new BigDecimal("2000.00"));

        BigDecimal result = repository.sumAmountBySourceStatementIdAndStatusExcludingId(1L, "CONFIRMED", 1L);

        assertThat(result).isEqualByComparingTo("2000.00");
    }
}
