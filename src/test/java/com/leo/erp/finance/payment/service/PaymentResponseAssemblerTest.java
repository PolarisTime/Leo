package com.leo.erp.finance.payment.service;

import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.mapper.PaymentMapper;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationResponse;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentResponseAssemblerTest {

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private PaymentAllocationResponseAssembler allocationResponseAssembler;

    @Test
    void toSummaryResponse_shouldDelegateToMapperWithoutAllocations() {
        PaymentResponseAssembler assembler = assembler();
        Payment payment = new Payment();
        PaymentResponse summary = summary();

        when(paymentMapper.toResponse(payment)).thenReturn(summary);

        assertThat(assembler.toSummaryResponse(payment)).isSameAs(summary);
        verify(allocationResponseAssembler, never()).toResponses(any());
    }

    @Test
    void toDetailResponse_shouldIncludeAllocationResponses() {
        PaymentResponseAssembler assembler = assembler();
        Payment payment = new Payment();
        List<PaymentAllocationResponse> allocations = List.of();
        PaymentResponse summary = summary();

        when(paymentMapper.toResponse(payment)).thenReturn(summary);
        when(allocationResponseAssembler.toResponses(payment)).thenReturn(allocations);

        PaymentResponse result = assembler.toDetailResponse(payment);
        assertThat(result.items()).isSameAs(allocations);
        assertThat(result.paymentNo()).isEqualTo(summary.paymentNo());
        assertThat(result.counterpartyId()).isEqualTo(summary.counterpartyId());
        assertThat(result.amount()).isEqualTo(summary.amount());
        assertThat(result.status()).isEqualTo(summary.status());
    }

    @Test
    void toDetailResponse_shouldTolerateEmptyAllocationList() {
        PaymentResponseAssembler assembler = assembler();
        Payment payment = new Payment();
        when(paymentMapper.toResponse(payment)).thenReturn(summary());
        when(allocationResponseAssembler.toResponses(payment)).thenReturn(List.of());

        PaymentResponse result = assembler.toDetailResponse(payment);

        assertThat(result.items()).isEmpty();
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    private PaymentResponseAssembler assembler() {
        return new PaymentResponseAssembler(paymentMapper, allocationResponseAssembler);
    }

    private PaymentResponse summary() {
        return new PaymentResponse(
                1L,
                "PAY001",
                "供应商",
                11L,
                "SUPPLIER_PAYMENT",
                "SUP001",
                "供应商A",
                null,
                null,
                null,
                "SUP001",
                "供应商A",
                30L,
                "结算主体",
                null,
                LocalDate.of(2026, 8, 25),
                "银行转账",
                new BigDecimal("10.00"),
                "草稿",
                false,
                "操作员",
                null,
                List.of()
        );
    }
}
