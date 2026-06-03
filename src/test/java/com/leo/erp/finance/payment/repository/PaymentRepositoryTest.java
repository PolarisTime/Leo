package com.leo.erp.finance.payment.repository;

import com.leo.erp.finance.payment.domain.entity.Payment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRepositoryTest {

    @Mock
    private PaymentRepository repository;

    @Test
    void existsByPaymentNoAndDeletedFlagFalse_shouldReturnTrueWhenExists() {
        when(repository.existsByPaymentNoAndDeletedFlagFalse("P001")).thenReturn(true);

        boolean result = repository.existsByPaymentNoAndDeletedFlagFalse("P001");

        assertThat(result).isTrue();
    }

    @Test
    void existsByPaymentNoAndDeletedFlagFalse_shouldReturnFalseWhenNotExists() {
        when(repository.existsByPaymentNoAndDeletedFlagFalse("NONEXIST")).thenReturn(false);

        boolean result = repository.existsByPaymentNoAndDeletedFlagFalse("NONEXIST");

        assertThat(result).isFalse();
    }

    @Test
    void existsByPaymentNoAndDeletedFlagFalse_shouldReturnFalseWhenDeleted() {
        when(repository.existsByPaymentNoAndDeletedFlagFalse("P002")).thenReturn(false);

        boolean result = repository.existsByPaymentNoAndDeletedFlagFalse("P002");

        assertThat(result).isFalse();
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnPaymentWhenExistsAndNotDeleted() {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setPaymentNo("P001");
        payment.setDeletedFlag(false);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(payment));

        Optional<Payment> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getPaymentNo()).isEqualTo("P001");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<Payment> result = repository.findByIdAndDeletedFlagFalse(1L);

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
