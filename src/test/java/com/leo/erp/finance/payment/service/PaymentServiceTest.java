package com.leo.erp.finance.payment.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.CrudRuntimeSettings;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import com.leo.erp.finance.payment.mapper.PaymentMapper;
import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationRequest;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationResponse;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.service.FreightStatementQueryService;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.service.SupplierStatementQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    @Test
    void shouldRequireSourceAllocationLockServiceAsConstructorDependency() {
        boolean hasRequiredDependency = java.util.Arrays.stream(PaymentService.class.getConstructors())
                .anyMatch(constructor -> java.util.Arrays.asList(constructor.getParameterTypes())
                        .contains(SourceAllocationLockService.class));

        assertThat(hasRequiredDependency).isTrue();
    }

    @Test
    void shouldLockSupplierStatementsBeforeCreatingPaymentAllocations() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PaymentApplyService applyService = mock(PaymentApplyService.class);
        PaymentAllocationService allocationService = mock(PaymentAllocationService.class);
        PaymentSettlementSyncService settlementSyncService = mock(PaymentSettlementSyncService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PaymentService service = lockAwareService(
                paymentRepository,
                idGenerator,
                applyService,
                allocationService,
                settlementSyncService,
                lockService
        );
        PaymentRequest request = buildRequest(
                "供应商",
                null,
                "供应商A",
                new BigDecimal("100.00"),
                StatusConstants.DRAFT,
                List.of(
                        new PaymentAllocationRequest(null, 13L, new BigDecimal("30.00")),
                        new PaymentAllocationRequest(null, 11L, new BigDecimal("40.00")),
                        new PaymentAllocationRequest(null, 13L, new BigDecimal("30.00"))
                )
        );

        when(idGenerator.nextId()).thenReturn(1L);

        service.create(request);

        InOrder flow = inOrder(lockService, applyService);
        flow.verify(lockService).lockStatementSources(List.of(), List.of(11L, 13L), List.of());
        flow.verify(applyService).apply(any(Payment.class), eq(request), any());
    }

    @Test
    void shouldLockOldSupplierAndNewFreightStatementsBeforeUpdatingPaymentAllocations() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentApplyService applyService = mock(PaymentApplyService.class);
        PaymentAllocationService allocationService = mock(PaymentAllocationService.class);
        PaymentSettlementSyncService settlementSyncService = mock(PaymentSettlementSyncService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PaymentService service = lockAwareService(
                paymentRepository,
                mock(SnowflakeIdGenerator.class),
                applyService,
                allocationService,
                settlementSyncService,
                lockService
        );
        Payment existing = buildPaymentEntity(1L, "FK-001");
        existing.setBusinessType("供应商");
        existing.setItems(new ArrayList<>(List.of(
                paymentAllocation(existing, 101L, 15L),
                paymentAllocation(existing, 102L, 11L)
        )));
        PaymentRequest request = buildRequest(
                "物流商",
                null,
                "物流商A",
                new BigDecimal("100.00"),
                StatusConstants.DRAFT,
                List.of(
                        new PaymentAllocationRequest(null, 22L, new BigDecimal("30.00")),
                        new PaymentAllocationRequest(null, 21L, new BigDecimal("40.00")),
                        new PaymentAllocationRequest(null, 22L, new BigDecimal("30.00"))
                )
        );

        when(paymentRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        service.update(1L, request);

        InOrder flow = inOrder(lockService, applyService);
        flow.verify(lockService).lockStatementSources(
                List.of(),
                List.of(11L, 15L),
                List.of(21L, 22L)
        );
        flow.verify(applyService).apply(eq(existing), eq(request), any());
    }

    @Test
    void shouldLockExistingSupplierStatementsBeforeUpdatingPaymentStatus() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentAllocationService allocationService = mock(PaymentAllocationService.class);
        PaymentSettlementSyncService settlementSyncService = mock(PaymentSettlementSyncService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PaymentService service = lockAwareService(
                paymentRepository,
                mock(SnowflakeIdGenerator.class),
                mock(PaymentApplyService.class),
                allocationService,
                settlementSyncService,
                lockService
        );
        Payment source = buildPaymentEntity(1L, "FK-001");
        source.setBusinessType("供应商");
        source.setStatus(StatusConstants.PAID);
        source.setItems(new ArrayList<>(List.of(
                paymentAllocation(source, 101L, 13L),
                paymentAllocation(source, 102L, 11L)
        )));
        Payment existing = spy(source);

        when(paymentRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        service.updateStatus(1L, StatusConstants.DRAFT);

        InOrder flow = inOrder(lockService, settlementSyncService, allocationService, existing);
        flow.verify(lockService).lockStatementSources(List.of(), List.of(11L, 13L), List.of());
        flow.verify(settlementSyncService).captureOriginalAllocationState(existing);
        flow.verify(allocationService).validateExistingAllocationsForSettlement(existing, StatusConstants.DRAFT);
        flow.verify(existing).setStatus(StatusConstants.DRAFT);
    }

    @Test
    void shouldLockExistingFreightStatementsBeforeDeletingPayment() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        PaymentService service = lockAwareService(
                paymentRepository,
                mock(SnowflakeIdGenerator.class),
                mock(PaymentApplyService.class),
                mock(PaymentAllocationService.class),
                mock(PaymentSettlementSyncService.class),
                lockService
        );
        Payment source = buildPaymentEntity(1L, "FK-001");
        source.setBusinessType("物流商");
        source.setItems(new ArrayList<>(List.of(
                paymentAllocation(source, 101L, 31L),
                paymentAllocation(source, 102L, 29L)
        )));
        Payment existing = spy(source);

        when(paymentRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        InOrder flow = inOrder(lockService, existing);
        flow.verify(lockService).lockStatementSources(List.of(), List.of(), List.of(29L, 31L));
        flow.verify(existing).setDeletedFlag(true);
    }

    @Test
    void shouldRejectDuplicatePaymentNo() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-DUP")).thenReturn(true);

        PaymentService service = service(
                paymentRepository,
                mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(PaymentMapper.class),
                mock(SupplierStatementQueryService.class),
                mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(new PaymentRequest(
                "FK-DUP", "供应商", "供应商A", 11L,
                LocalDate.of(2026, 4, 26), "银行转账",
                new BigDecimal("100.00"), "草稿", "财务A", null, List.of()
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("付款单号已存在");
    }

    @Test
    void shouldRejectSupplierNameMismatchBetweenPaymentAndStatement() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        SupplierStatementQueryService supplierStatementQueryService = mock(SupplierStatementQueryService.class);
        ResourceRecordAccessGuard resourceRecordAccessGuard = mock(ResourceRecordAccessGuard.class);
        SupplierStatement statement = new SupplierStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(11L);
        statement.setSupplierName("供应商B");
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        when(supplierStatementQueryService.requireActiveById(11L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);

        PaymentService service = service(
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
                "草稿",
                List.of(new PaymentAllocationRequest(null, 11L, new BigDecimal("100.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对账单供应商与付款单往来单位不一致");
    }

    @Test
    void shouldRejectCarrierNameMismatchBetweenPaymentAndStatement() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        FreightStatementQueryService freightStatementQueryService = mock(FreightStatementQueryService.class);
        ResourceRecordAccessGuard resourceRecordAccessGuard = mock(ResourceRecordAccessGuard.class);
        FreightStatement statement = new FreightStatement();
        statement.setStatus(StatusConstants.AUDITED);
        statement.setId(31L);
        statement.setCarrierName("物流商B");
        statement.setTotalFreight(new BigDecimal("500.00"));
        when(freightStatementQueryService.requireActiveById(31L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);

        PaymentService service = service(
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
                "草稿",
                List.of(new PaymentAllocationRequest(null, 31L, new BigDecimal("100.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对账单物流商与付款单往来单位不一致");
    }

    @Test
    void shouldRejectDuplicateSupplierAllocationToSameStatement() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        SupplierStatementQueryService supplierStatementQueryService = mock(SupplierStatementQueryService.class);
        SupplierStatement statement = new SupplierStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(11L);
        statement.setSupplierName("供应商A");
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        when(supplierStatementQueryService.requireActiveById(11L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);

        PaymentService service = service(
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
                List.of(
                        new PaymentAllocationRequest(null, 11L, new BigDecimal("50.00")),
                        new PaymentAllocationRequest(null, 11L, new BigDecimal("50.00"))
                )
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复核销同一供应商对账单");
    }

    @Test
    void shouldRejectDuplicateFreightAllocationToSameStatement() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        FreightStatementQueryService freightStatementQueryService = mock(FreightStatementQueryService.class);
        FreightStatement statement = new FreightStatement();
        statement.setStatus(StatusConstants.AUDITED);
        statement.setId(31L);
        statement.setCarrierName("物流商A");
        statement.setTotalFreight(new BigDecimal("500.00"));
        when(freightStatementQueryService.requireActiveById(31L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);

        PaymentService service = service(
                paymentRepository,
                mock(PaymentAllocationRepository.class),
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
                "草稿",
                List.of(
                        new PaymentAllocationRequest(null, 31L, new BigDecimal("50.00")),
                        new PaymentAllocationRequest(null, 31L, new BigDecimal("50.00"))
                )
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复核销同一物流对账单");
    }

    @Test
    void shouldRejectUnsupportedBusinessTypeWithAllocations() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);

        PaymentService service = service(
                paymentRepository,
                mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(PaymentMapper.class),
                mock(SupplierStatementQueryService.class),
                mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                "其他",
                11L,
                "往来单位A",
                new BigDecimal("100.00"),
                "草稿",
                List.of(new PaymentAllocationRequest(null, 11L, new BigDecimal("100.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前业务类型不支持对账单核销");
    }

    @Test
    void shouldRejectZeroSupplierAllocationAmount() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        SupplierStatementQueryService supplierStatementQueryService = mock(SupplierStatementQueryService.class);
        SupplierStatement statement = new SupplierStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(11L);
        statement.setSupplierName("供应商A");
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        when(supplierStatementQueryService.requireActiveById(11L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);

        PaymentService service = service(
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
                List.of(new PaymentAllocationRequest(null, 11L, BigDecimal.ZERO))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("核销金额必须大于0");
    }

    @Test
    void shouldRejectSupplierPaymentAmountNotMatchingAllocations() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        SupplierStatementQueryService supplierStatementQueryService = mock(SupplierStatementQueryService.class);
        SupplierStatement statement = new SupplierStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(11L);
        statement.setSupplierName("供应商A");
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        when(supplierStatementQueryService.requireActiveById(11L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);

        PaymentService service = service(
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
                "已付款",
                List.of(new PaymentAllocationRequest(null, 11L, new BigDecimal("80.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("付款金额必须等于核销金额合计");
    }

    @Test
    void shouldRejectPaidSupplierPaymentWithoutAllocations() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);

        PaymentService service = service(
                paymentRepository,
                mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(PaymentMapper.class),
                mock(SupplierStatementQueryService.class),
                mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                "供应商",
                null,
                "供应商A",
                new BigDecimal("100.00"),
                "已付款",
                List.of()
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已付款状态必须填写核销明细");
    }

    @Test
    void shouldRejectPaidUnsupportedBusinessTypeWithoutAllocations() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);

        PaymentService service = service(
                paymentRepository,
                mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(PaymentMapper.class),
                mock(SupplierStatementQueryService.class),
                mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                "其他",
                null,
                "往来单位A",
                new BigDecimal("100.00"),
                "已付款",
                List.of()
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已付款状态必须关联供应商或物流商对账单核销");
    }

    @Test
    void shouldRejectPaidSupplierPaymentWithUnconfirmedStatement() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        SupplierStatementQueryService supplierStatementQueryService = mock(SupplierStatementQueryService.class);
        SupplierStatement statement = new SupplierStatement();
        statement.setStatus(StatusConstants.PENDING_CONFIRM);
        statement.setId(11L);
        statement.setSupplierName("供应商A");
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        when(supplierStatementQueryService.requireActiveById(11L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);

        PaymentService service = service(
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
                "已付款",
                List.of(new PaymentAllocationRequest(null, 11L, new BigDecimal("100.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商对账单未确认，不能付款");
    }

    @Test
    void shouldRejectPaidFreightPaymentWithUnauditedStatement() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        FreightStatementQueryService freightStatementQueryService = mock(FreightStatementQueryService.class);
        FreightStatement statement = new FreightStatement();
        statement.setStatus(StatusConstants.PENDING_AUDIT);
        statement.setId(31L);
        statement.setCarrierName("物流商A");
        statement.setTotalFreight(new BigDecimal("500.00"));
        when(freightStatementQueryService.requireActiveById(31L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);

        PaymentService service = service(
                paymentRepository,
                mock(PaymentAllocationRepository.class),
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
                .hasMessageContaining("物流对账单未审核，不能付款");
    }

    @Test
    void shouldAllowDraftSupplierPaymentWithPartialAllocation() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        SupplierStatementQueryService supplierStatementQueryService = mock(SupplierStatementQueryService.class);
        SupplierStatement statement = new SupplierStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(11L);
        statement.setSupplierName("供应商A");
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        when(supplierStatementQueryService.requireActiveById(11L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment payment = inv.getArgument(0);
            payment.setId(1L);
            return payment;
        });
        when(paymentMapper.toResponse(any(Payment.class))).thenAnswer(inv -> {
            Payment payment = inv.getArgument(0);
            return new PaymentResponse(
                    payment.getId(), payment.getPaymentNo(), payment.getBusinessType(), payment.getCounterpartyCode(),
                    payment.getCounterpartyName(), payment.getSourceStatementId(), payment.getPaymentDate(),
                    payment.getPayType(), payment.getAmount(), payment.getStatus(), payment.getOperatorName(),
                    payment.getRemark(), List.of()
            );
        });

        PaymentService service = service(
                paymentRepository,
                mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                paymentMapper,
                supplierStatementQueryService,
                mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        PaymentResponse result = service.create(buildRequest(
                "供应商",
                11L,
                "供应商A",
                new BigDecimal("100.00"),
                "草稿",
                List.of(new PaymentAllocationRequest(null, 11L, new BigDecimal("80.00")))
        ));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(StatusConstants.DRAFT);
    }

    @Test
    void shouldRejectOverPaymentAgainstSupplierStatement() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentAllocationRepository allocationRepository = mock(PaymentAllocationRepository.class);
        SupplierStatementQueryService supplierStatementQueryService = mock(SupplierStatementQueryService.class);
        SupplierStatement statement = new SupplierStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
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

        PaymentService service = service(
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
    void shouldRejectOverPaymentAgainstSupplierStatementWhenStatusContainsWhitespace() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentAllocationRepository allocationRepository = mock(PaymentAllocationRepository.class);
        SupplierStatementQueryService supplierStatementQueryService = mock(SupplierStatementQueryService.class);
        SupplierStatement statement = new SupplierStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
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

        PaymentService service = service(
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
                " 已付款 ",
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
        statement.setStatus(StatusConstants.AUDITED);
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

        PaymentService service = service(
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
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(11L);
        statement.setSupplierName("供应商A");
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        when(supplierStatementQueryService.requireActiveById(11L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);

        PaymentService service = service(
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
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(11L);
        statement.setSupplierName("供应商A");
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        when(supplierStatementQueryService.requireActiveById(11L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无数据权限"))
                .when(resourceRecordAccessGuard)
                .assertCurrentUserCanAccess("supplier-statement", "read", statement);

        PaymentService service = service(
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
        statement.setStatus(StatusConstants.AUDITED);
        statement.setId(31L);
        statement.setCarrierName("物流商A");
        statement.setTotalFreight(new BigDecimal("500.00"));
        when(freightStatementQueryService.requireActiveById(31L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无数据权限"))
                .when(resourceRecordAccessGuard)
                .assertCurrentUserCanAccess("freight-statement", "read", statement);

        PaymentService service = service(
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

    @Test
    void shouldCreateSuccessfullyForSupplierType() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentAllocationRepository allocationRepository = mock(PaymentAllocationRepository.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        SupplierStatementQueryService supplierStatementQueryService = mock(SupplierStatementQueryService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ResourceRecordAccessGuard resourceRecordAccessGuard = mock(ResourceRecordAccessGuard.class);

        SupplierStatement statement = new SupplierStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(11L);
        statement.setSupplierName("供应商A");
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        statement.setStatementNo("SST-001");
        statement.setClosingAmount(new BigDecimal("500.00"));

        when(supplierStatementQueryService.requireActiveById(11L)).thenReturn(statement);
        when(supplierStatementQueryService.findActiveById(11L)).thenReturn(Optional.of(statement));
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(
                new PaymentResponse(1L, "FK-001", "供应商", "供应商A", 11L,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        "草稿", "财务A", null, null)
        );

        PaymentService service = service(
                paymentRepository, allocationRepository, new SnowflakeIdGenerator(0L),
                paymentMapper, supplierStatementQueryService, mock(FreightStatementQueryService.class),
                eventPublisher, resourceRecordAccessGuard, mock(WorkflowTransitionGuard.class)
        );

        PaymentResponse result = service.create(buildRequest(
                "供应商", 11L, "供应商A", new BigDecimal("100.00"), "草稿",
                List.of(new PaymentAllocationRequest(null, 11L, new BigDecimal("100.00")))
        ));

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void shouldCreateSuccessfullyForFreightType() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        FreightStatementQueryService freightStatementQueryService = mock(FreightStatementQueryService.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ResourceRecordAccessGuard resourceRecordAccessGuard = mock(ResourceRecordAccessGuard.class);

        FreightStatement statement = new FreightStatement();
        statement.setStatus(StatusConstants.AUDITED);
        statement.setId(31L);
        statement.setCarrierName("物流商A");
        statement.setTotalFreight(new BigDecimal("500.00"));
        statement.setStatementNo("FST-001");
        statement.setUnpaidAmount(new BigDecimal("300.00"));

        when(freightStatementQueryService.requireActiveById(31L)).thenReturn(statement);
        when(freightStatementQueryService.findActiveById(31L)).thenReturn(Optional.of(statement));
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-002")).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(2L);
            return p;
        });
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(
                new PaymentResponse(2L, "FK-002", "物流商", "物流商A", 31L,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        "草稿", "财务A", null, null)
        );

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), paymentMapper,
                mock(SupplierStatementQueryService.class), freightStatementQueryService,
                eventPublisher, resourceRecordAccessGuard, mock(WorkflowTransitionGuard.class)
        );

        PaymentResponse result = service.create(buildRequest(
                "物流商", 31L, "物流商A", new BigDecimal("100.00"), "草稿",
                List.of(new PaymentAllocationRequest(null, 31L, new BigDecimal("100.00")))
        ));

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(2L);
    }

    @Test
    void shouldCreateSuccessfullyForUnsupportedBusinessTypeWithoutItems() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);

        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-003")).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(3L);
            return p;
        });
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(
                new PaymentResponse(3L, "FK-003", "其他", "往来单位A", null,
                        LocalDate.of(2026, 4, 26), "现金", new BigDecimal("100.00"),
                        "草稿", "财务A", null, null)
        );

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), paymentMapper,
                mock(SupplierStatementQueryService.class), mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class), mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        PaymentResponse result = service.create(buildRequest(
                "其他", null, "往来单位A", new BigDecimal("100.00"), "草稿", List.of()
        ));

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(3L);
    }

    @Test
    void shouldNotCheckDuplicatePaymentNoWhenUpdatingWithSameNo() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        Payment existing = buildPaymentEntity(1L, "FK-001");
        existing.setDeletedFlag(false);
        when(paymentRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(true);

        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(
                new PaymentResponse(1L, "FK-001", "供应商", "供应商A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        "草稿", "财务A", null, null)
        );

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), paymentMapper,
                mock(SupplierStatementQueryService.class), mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class), mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        PaymentRequest request = new PaymentRequest(
                "FK-001", "供应商", "供应商A", null,
                LocalDate.of(2026, 4, 26), "银行转账",
                new BigDecimal("100.00"), "草稿", "财务A", null, List.of()
        );

        PaymentResponse result = service.update(1L, request);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldRejectUpdateWithChangedDuplicatePaymentNo() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        Payment existing = buildPaymentEntity(1L, "FK-001");
        existing.setDeletedFlag(false);
        when(paymentRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-DUP")).thenReturn(true);

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(PaymentMapper.class),
                mock(SupplierStatementQueryService.class), mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class), mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        PaymentRequest request = new PaymentRequest(
                "FK-DUP", "供应商", "供应商A", null,
                LocalDate.of(2026, 4, 26), "银行转账",
                new BigDecimal("100.00"), "草稿", "财务A", null, List.of()
        );

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(RuntimeException.class)
                ;
    }

    @Test
    void shouldValidateChangedUniquePaymentNoOnUpdate() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        Payment existing = buildPaymentEntity(1L, "FK-001");
        existing.setDeletedFlag(false);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-NEW")).thenReturn(false);

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(PaymentMapper.class),
                mock(SupplierStatementQueryService.class), mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class), mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        ReflectionTestUtils.invokeMethod(service, "validateUpdate", existing, new PaymentRequest(
                "FK-NEW", "供应商", "供应商A", null,
                LocalDate.of(2026, 4, 26), "银行转账",
                new BigDecimal("100.00"), "草稿", "财务A", null, List.of()
        ));
    }

    @Test
    void shouldReturnDetailForExistingPayment() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        Payment existing = buildPaymentEntity(1L, "FK-001");
        existing.setDeletedFlag(false);
        when(paymentRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(paymentMapper.toResponse(existing)).thenReturn(
                new PaymentResponse(1L, "FK-001", "供应商", "供应商A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        "草稿", "财务A", null, null)
        );

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), paymentMapper,
                mock(SupplierStatementQueryService.class), mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class), mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        PaymentResponse result = service.detail(1L);

        assertThat(result).isNotNull();
        assertThat(result.paymentNo()).isEqualTo("FK-001");
    }

    @Test
    void shouldReturnDeletedPaymentDetailForAdminWhenRuntimeAllows() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        Payment deleted = buildPaymentEntity(1L, "FK-DELETED");
        deleted.setDeletedFlag(true);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(deleted));
        when(paymentMapper.toResponse(deleted)).thenReturn(
                new PaymentResponse(1L, "FK-DELETED", "供应商", null, "供应商A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        StatusConstants.DRAFT, true, "财务A", null, null)
        );

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), paymentMapper,
                mock(SupplierStatementQueryService.class), mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class), mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );
        CrudRuntimeSettings runtimeSettings = mock(CrudRuntimeSettings.class);
        when(runtimeSettings.shouldAdminSeeDeletedRecords()).thenReturn(true);
        ReflectionTestUtils.invokeMethod(service, "setCrudRuntimeSettings", runtimeSettings);

        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "admin",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            ));

            PaymentResponse result = service.detail(1L);

            assertThat(result.paymentNo()).isEqualTo("FK-DELETED");
            assertThat(result.status()).isEqualTo(StatusConstants.DRAFT);
            assertThat(result.deletedFlag()).isTrue();
            verify(paymentRepository).findById(1L);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void shouldReturnPageResults() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        Payment payment = buildPaymentEntity(1L, "FK-001");
        Page<Payment> page = new PageImpl<>(List.of(payment));
        when(paymentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(page);
        when(paymentMapper.toResponse(payment)).thenReturn(
                new PaymentResponse(1L, "FK-001", "供应商", "供应商A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        "草稿", "财务A", null, null)
        );

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), paymentMapper,
                mock(SupplierStatementQueryService.class), mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class), mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        Page<PaymentResponse> result = service.page(
                new com.leo.erp.common.api.PageQuery(0, 10, "id", "desc"),
                new com.leo.erp.common.api.PageFilter(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
        );

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void shouldReturnSearchResults() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        Payment payment = buildPaymentEntity(1L, "FK-001");
        Page<Payment> page = new PageImpl<>(List.of(payment));
        when(paymentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(page);
        when(paymentMapper.toResponse(payment)).thenReturn(
                new PaymentResponse(1L, "FK-001", "供应商", "供应商A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        "草稿", "财务A", null, null)
        );

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), paymentMapper,
                mock(SupplierStatementQueryService.class), mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class), mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        List<PaymentResponse> results = service.search("FK", 10);

        assertThat(results).isNotNull();
    }

    @Test
    void shouldAllowValidStatusTransitionDraftToPaid() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentAllocationRepository allocationRepository = mock(PaymentAllocationRepository.class);
        SupplierStatementQueryService supplierStatementQueryService = mock(SupplierStatementQueryService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);

        Payment existing = buildPaymentEntity(1L, "FK-001");
        existing.setStatus(StatusConstants.DRAFT);
        existing.setDeletedFlag(false);
        existing.setBusinessType("供应商");
        existing.setCounterpartyName("供应商A");
        existing.setAmount(new BigDecimal("100.00"));

        SupplierStatement statement = new SupplierStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(11L);
        statement.setSupplierName("供应商A");
        statement.setPurchaseAmount(new BigDecimal("1000.00"));

        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setId(100L);
        allocation.setLineNo(1);
        allocation.setSourceStatementId(11L);
        allocation.setAllocatedAmount(new BigDecimal("100.00"));
        existing.setItems(new ArrayList<>(List.of(allocation)));

        when(paymentRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(supplierStatementQueryService.requireActiveById(11L)).thenReturn(statement);
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(
                new PaymentResponse(1L, "FK-001", "供应商", "供应商A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        StatusConstants.PAID, "财务A", null, null)
        );

        PaymentService service = service(
                paymentRepository, allocationRepository,
                new SnowflakeIdGenerator(0L), paymentMapper,
                supplierStatementQueryService, mock(FreightStatementQueryService.class),
                eventPublisher, mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        PaymentResponse result = service.updateStatus(1L, StatusConstants.PAID);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(StatusConstants.PAID);
    }

    @Test
    void shouldRejectStatusTransitionToPaidWithoutAllocations() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        Payment existing = buildPaymentEntity(1L, "FK-001");
        existing.setStatus(StatusConstants.DRAFT);
        existing.setDeletedFlag(false);
        existing.setBusinessType("供应商");
        existing.setCounterpartyName("供应商A");
        existing.setAmount(new BigDecimal("100.00"));
        when(paymentRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        PaymentService service = service(
                paymentRepository,
                mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L),
                mock(PaymentMapper.class),
                mock(SupplierStatementQueryService.class),
                mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class),
                mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.updateStatus(1L, StatusConstants.PAID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已付款状态必须填写核销明细");
    }

    @Test
    void shouldAllowValidStatusTransitionDraftToPaidForFreight() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        FreightStatementQueryService freightStatementQueryService = mock(FreightStatementQueryService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);

        Payment existing = buildPaymentEntity(1L, "FK-001");
        existing.setStatus(StatusConstants.DRAFT);
        existing.setDeletedFlag(false);
        existing.setBusinessType("物流商");
        existing.setCounterpartyName("物流商A");
        existing.setAmount(new BigDecimal("100.00"));

        FreightStatement statement = new FreightStatement();
        statement.setStatus(StatusConstants.AUDITED);
        statement.setId(31L);
        statement.setCarrierName("物流商A");
        statement.setTotalFreight(new BigDecimal("500.00"));

        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setId(100L);
        allocation.setLineNo(1);
        allocation.setSourceStatementId(31L);
        allocation.setAllocatedAmount(new BigDecimal("100.00"));
        existing.setItems(new ArrayList<>(List.of(allocation)));

        when(paymentRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(freightStatementQueryService.requireActiveById(31L)).thenReturn(statement);
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(
                new PaymentResponse(1L, "FK-001", "物流商", "物流商A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        StatusConstants.PAID, "财务A", null, null)
        );

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), paymentMapper,
                mock(SupplierStatementQueryService.class), freightStatementQueryService,
                eventPublisher, mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        PaymentResponse result = service.updateStatus(1L, StatusConstants.PAID);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(StatusConstants.PAID);
    }

    @Test
    void shouldRejectInvalidStatusTransition() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        Payment existing = buildPaymentEntity(1L, "FK-001");
        existing.setStatus(StatusConstants.DRAFT);
        existing.setDeletedFlag(false);
        when(paymentRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(PaymentMapper.class),
                mock(SupplierStatementQueryService.class), mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class), mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.updateStatus(1L, "已审核"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从");
    }

    @Test
    void shouldHandleBeforeStatusUpdateForNonPaidStatus() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        Payment existing = buildPaymentEntity(1L, "FK-001");
        existing.setStatus(StatusConstants.PAID);
        existing.setDeletedFlag(false);
        existing.setBusinessType("供应商");
        existing.setCounterpartyName("供应商A");
        when(paymentRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(
                new PaymentResponse(1L, "FK-001", "供应商", "供应商A", null,
                        LocalDate.of(2026, 4, 26), "银行转账", new BigDecimal("100.00"),
                        StatusConstants.DRAFT, "财务A", null, null)
        );

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), paymentMapper,
                mock(SupplierStatementQueryService.class), mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class), mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        PaymentResponse result = service.updateStatus(1L, StatusConstants.DRAFT);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(StatusConstants.DRAFT);
    }

    @Test
    void shouldDeleteSuccessfully() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        Payment existing = buildPaymentEntity(1L, "FK-001");
        existing.setStatus(StatusConstants.DRAFT);
        existing.setDeletedFlag(false);
        when(paymentRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(PaymentMapper.class),
                mock(SupplierStatementQueryService.class), mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class), mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        service.delete(1L);

        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void shouldRejectDeleteWhenStatusIsProtected() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        Payment existing = buildPaymentEntity(1L, "FK-001");
        existing.setStatus(StatusConstants.PAID);
        existing.setDeletedFlag(false);
        when(paymentRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(PaymentMapper.class),
                mock(SupplierStatementQueryService.class), mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class), mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能删除");
    }

    @Test
    void shouldRejectEditWhenStatusIsProtected() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        Payment existing = buildPaymentEntity(1L, "FK-001");
        existing.setStatus(StatusConstants.PAID);
        existing.setDeletedFlag(false);
        when(paymentRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(PaymentMapper.class),
                mock(SupplierStatementQueryService.class), mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class), mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        PaymentRequest request = new PaymentRequest(
                "FK-001", "供应商", "供应商A", null,
                LocalDate.of(2026, 4, 26), "银行转账",
                new BigDecimal("200.00"), "草稿", "财务A", null, List.of()
        );

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能编辑");
    }

    @Test
    void shouldRejectSupplierOverPaymentAmount() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        SupplierStatementQueryService supplierStatementQueryService = mock(SupplierStatementQueryService.class);
        SupplierStatement statement = new SupplierStatement();
        statement.setStatus(StatusConstants.CONFIRMED);
        statement.setId(11L);
        statement.setSupplierName("供应商A");
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        when(supplierStatementQueryService.requireActiveById(11L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(PaymentMapper.class),
                supplierStatementQueryService, mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class), mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                "供应商", 11L, "供应商A", new BigDecimal("100.00"), "草稿",
                List.of(new PaymentAllocationRequest(null, 11L, new BigDecimal("120.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("核销金额合计不能超过付款金额");
    }

    @Test
    void shouldRejectFreightOverPaymentAmount() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        FreightStatementQueryService freightStatementQueryService = mock(FreightStatementQueryService.class);
        FreightStatement statement = new FreightStatement();
        statement.setStatus(StatusConstants.AUDITED);
        statement.setId(31L);
        statement.setCarrierName("物流商A");
        statement.setTotalFreight(new BigDecimal("500.00"));
        when(freightStatementQueryService.requireActiveById(31L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(PaymentMapper.class),
                mock(SupplierStatementQueryService.class), freightStatementQueryService,
                mock(ApplicationEventPublisher.class), mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                "物流商", 31L, "物流商A", new BigDecimal("100.00"), "草稿",
                List.of(new PaymentAllocationRequest(null, 31L, new BigDecimal("120.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("核销金额合计不能超过付款金额");
    }

    @Test
    void shouldRejectFreightPaymentAmountNotMatchingAllocations() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        FreightStatementQueryService freightStatementQueryService = mock(FreightStatementQueryService.class);
        FreightStatement statement = new FreightStatement();
        statement.setStatus(StatusConstants.AUDITED);
        statement.setId(31L);
        statement.setCarrierName("物流商A");
        statement.setTotalFreight(new BigDecimal("500.00"));
        when(freightStatementQueryService.requireActiveById(31L)).thenReturn(statement);
        when(paymentRepository.existsByPaymentNoAndDeletedFlagFalse("FK-001")).thenReturn(false);

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(PaymentMapper.class),
                mock(SupplierStatementQueryService.class), freightStatementQueryService,
                mock(ApplicationEventPublisher.class), mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(
                "物流商", 31L, "物流商A", new BigDecimal("100.00"), "已付款",
                List.of(new PaymentAllocationRequest(null, 31L, new BigDecimal("80.00")))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("付款金额必须等于核销金额合计");
    }

    @Test
    void shouldRejectWhenEntityNotFound() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        PaymentService service = service(
                paymentRepository, mock(PaymentAllocationRepository.class),
                new SnowflakeIdGenerator(0L), mock(PaymentMapper.class),
                mock(SupplierStatementQueryService.class), mock(FreightStatementQueryService.class),
                mock(ApplicationEventPublisher.class), mock(ResourceRecordAccessGuard.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.detail(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("付款单不存在");
    }

    private PaymentService service(PaymentRepository paymentRepository,
                                   PaymentAllocationRepository paymentAllocationRepository,
                                   SnowflakeIdGenerator snowflakeIdGenerator,
                                   PaymentMapper paymentMapper,
                                   SupplierStatementQueryService supplierStatementQueryService,
                                   FreightStatementQueryService freightStatementQueryService,
                                   ApplicationEventPublisher eventPublisher,
                                   ResourceRecordAccessGuard resourceRecordAccessGuard,
                                   WorkflowTransitionGuard workflowTransitionGuard) {
        PaymentStatementAllocationValidator statementAllocationValidator = new PaymentStatementAllocationValidator(
                paymentAllocationRepository,
                supplierStatementQueryService,
                freightStatementQueryService,
                resourceRecordAccessGuard
        );
        PaymentAllocationService allocationService = new PaymentAllocationService(statementAllocationValidator);
        PaymentSettlementSyncService settlementSyncService = new PaymentSettlementSyncService(eventPublisher);
        return new PaymentService(
                paymentRepository,
                snowflakeIdGenerator,
                paymentMapper,
                new PaymentApplyService(workflowTransitionGuard, allocationService, settlementSyncService),
                allocationService,
                new PaymentAllocationResponseAssembler(
                        supplierStatementQueryService,
                        freightStatementQueryService
                ),
                settlementSyncService,
                mock(SourceAllocationLockService.class)
        );
    }

    private PaymentService lockAwareService(
            PaymentRepository paymentRepository,
            SnowflakeIdGenerator idGenerator,
            PaymentApplyService applyService,
            PaymentAllocationService allocationService,
            PaymentSettlementSyncService settlementSyncService,
            SourceAllocationLockService lockService
    ) {
        PaymentMapper mapper = mock(PaymentMapper.class);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Payment.class))).thenReturn(mock(PaymentResponse.class));
        return new PaymentService(
                paymentRepository,
                idGenerator,
                mapper,
                applyService,
                allocationService,
                mock(PaymentAllocationResponseAssembler.class),
                settlementSyncService,
                lockService
        );
    }

    private PaymentAllocation paymentAllocation(Payment payment, Long id, Long sourceStatementId) {
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setId(id);
        allocation.setPayment(payment);
        allocation.setSourceStatementId(sourceStatementId);
        allocation.setAllocatedAmount(new BigDecimal("50.00"));
        return allocation;
    }

    private Payment buildPaymentEntity(Long id, String paymentNo) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setPaymentNo(paymentNo);
        payment.setBusinessType("供应商");
        payment.setCounterpartyName("供应商A");
        payment.setPaymentDate(LocalDate.of(2026, 4, 26));
        payment.setPayType("银行转账");
        payment.setAmount(new BigDecimal("100.00"));
        payment.setStatus(StatusConstants.DRAFT);
        payment.setOperatorName("财务A");
        payment.setItems(new ArrayList<>());
        payment.setOriginalAllocationStatementIds(new java.util.LinkedHashSet<>());
        return payment;
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
