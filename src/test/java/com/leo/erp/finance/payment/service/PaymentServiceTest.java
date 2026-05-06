package com.leo.erp.finance.payment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.mapper.PaymentMapper;
import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationRequest;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.service.FreightStatementQueryService;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.service.SupplierStatementQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    @Test
    void shouldAllowSupplierPaymentWithoutStatementAllocations() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(new PaymentResponse(
                1L,
                "FK-001",
                "供应商",
                "供应商A",
                null,
                LocalDate.of(2026, 4, 26),
                "银行转账",
                new BigDecimal("100.00"),
                "已付款",
                "财务A",
                null,
                List.of()
        ));

        PaymentService service = new PaymentService(
                paymentRepository,
                mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                paymentMapper,
                mock(SupplierStatementQueryService.class),
                mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatCode(() -> service.create(buildRequest("供应商", null, "供应商A", new BigDecimal("100.00"), "已付款", List.of())))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectOverPaymentAgainstSupplierStatement() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentAllocationRepository allocationRepository = mock(PaymentAllocationRepository.class);
        SupplierStatementQueryService supplierStatementQueryService = mock(SupplierStatementQueryService.class);
        SupplierStatement statement = new SupplierStatement();
        statement.setId(11L);
        statement.setSupplierName("供应商A");
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        when(supplierStatementQueryService.requireActiveById(11L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);
        when(allocationRepository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId(
                eq(11L),
                eq("供应商"),
                eq(StatusConstants.PAID),
                anyLong()
        )).thenReturn(new BigDecimal("900.00"));

        PaymentService service = new PaymentService(
                paymentRepository,
                allocationRepository,
                new SnowflakeIdGenerator(0L),
                mock(PaymentMapper.class),
                supplierStatementQueryService,
                mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                "供应商",
                11L,
                "供应商A",
                new BigDecimal("200.00"),
                "已付款",
                List.of(new PaymentAllocationRequest(null, 11L, new BigDecimal("200.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("累计付款金额不能超过采购金额");
    }

    @Test
    void shouldRejectOverPaymentAgainstFreightStatement() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentAllocationRepository allocationRepository = mock(PaymentAllocationRepository.class);
        FreightStatementQueryService freightStatementQueryService = mock(FreightStatementQueryService.class);
        FreightStatement statement = new FreightStatement();
        statement.setId(31L);
        statement.setCarrierName("物流商A");
        statement.setTotalFreight(new BigDecimal("500.00"));
        when(freightStatementQueryService.requireActiveById(31L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);
        when(allocationRepository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId(
                eq(31L),
                eq("物流商"),
                eq(StatusConstants.PAID),
                anyLong()
        )).thenReturn(new BigDecimal("450.00"));

        PaymentService service = new PaymentService(
                paymentRepository,
                allocationRepository,
                new SnowflakeIdGenerator(0L),
                mock(PaymentMapper.class),
                mock(SupplierStatementQueryService.class),
                freightStatementQueryService,
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                "物流商",
                31L,
                "物流商A",
                new BigDecimal("100.00"),
                "已付款",
                List.of(new PaymentAllocationRequest(null, 31L, new BigDecimal("100.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("累计付款金额不能超过总运费");
    }

    @Test
    void shouldRejectAllocationAmountExceedingPaymentAmount() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        SupplierStatementQueryService supplierStatementQueryService = mock(SupplierStatementQueryService.class);
        SupplierStatement statement = new SupplierStatement();
        statement.setId(11L);
        statement.setSupplierName("供应商A");
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        when(supplierStatementQueryService.requireActiveById(11L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);

        PaymentService service = new PaymentService(
                paymentRepository,
                mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(PaymentMapper.class),
                supplierStatementQueryService,
                mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                "供应商",
                11L,
                "供应商A",
                new BigDecimal("100.00"),
                "草稿",
                List.of(new PaymentAllocationRequest(null, 11L, new BigDecimal("120.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("核销金额合计不能超过付款金额");
    }

    @Test
    void shouldRejectSupplierStatementOutsideDataScope() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        SupplierStatementQueryService supplierStatementQueryService = mock(SupplierStatementQueryService.class);
        ResourceRecordAccessGuard resourceRecordAccessGuard = mock(ResourceRecordAccessGuard.class);
        SupplierStatement statement = new SupplierStatement();
        statement.setId(11L);
        statement.setSupplierName("供应商A");
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        when(supplierStatementQueryService.requireActiveById(11L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无数据权限"))
                .when(resourceRecordAccessGuard)
                .assertCurrentUserCanAccess("supplier-statements", "read", statement);

        PaymentService service = new PaymentService(
                paymentRepository,
                mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(PaymentMapper.class),
                supplierStatementQueryService,
                mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class),
                resourceRecordAccessGuard,
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                "供应商",
                11L,
                "供应商A",
                new BigDecimal("100.00"),
                "已付款",
                List.of(new PaymentAllocationRequest(null, 11L, new BigDecimal("100.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
    }

    @Test
    void shouldRejectFreightStatementOutsideDataScope() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        FreightStatementQueryService freightStatementQueryService = mock(FreightStatementQueryService.class);
        ResourceRecordAccessGuard resourceRecordAccessGuard = mock(ResourceRecordAccessGuard.class);
        FreightStatement statement = new FreightStatement();
        statement.setId(31L);
        statement.setCarrierName("物流商A");
        statement.setTotalFreight(new BigDecimal("500.00"));
        when(freightStatementQueryService.requireActiveById(31L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无数据权限"))
                .when(resourceRecordAccessGuard)
                .assertCurrentUserCanAccess("freight-statements", "read", statement);

        PaymentService service = new PaymentService(
                paymentRepository,
                mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(PaymentMapper.class),
                mock(SupplierStatementQueryService.class),
                freightStatementQueryService,
                mock(ApplicationEventPublisher.class),
                resourceRecordAccessGuard,
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                "物流商",
                31L,
                "物流商A",
                new BigDecimal("100.00"),
                "已付款",
                List.of(new PaymentAllocationRequest(null, 31L, new BigDecimal("100.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无数据权限");
    }

    private PaymentRequest buildRequest(String businessType,
                                        Long sourceStatementId,
                                        String counterpartyName,
                                        BigDecimal amount,
                                        String status,
                                        List<PaymentAllocationRequest> items) {
        return new PaymentRequest(
                "FK-001",
                businessType,
                counterpartyName,
                sourceStatementId,
                LocalDate.of(2026, 4, 26),
                "银行转账",
                amount,
                status,
                "财务A",
                null,
                items
        );
    }
}
