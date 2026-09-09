package com.leo.erp.finance.payment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PaymentService 显式状态断言序列边界测试：非法迁移、终态拒绝、空状态、
 * 等值短路、守卫→保存→对账同步 InOrder。
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Long ID = 7L;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private PaymentApplyService applyService;

    @Mock
    private PaymentMutationGuardService mutationGuardService;

    @Mock
    private PaymentResponseAssembler responseAssembler;

    @Mock
    private PaymentSettlementSyncService settlementSyncService;

    @InjectMocks
    private PaymentService service;

    private Payment entity(String status) {
        Payment payment = new Payment();
        payment.setId(ID);
        payment.setPaymentNo("PAY001");
        payment.setBusinessType("供应商");
        payment.setCounterpartyName("供应商A");
        payment.setPaymentPurpose("STATEMENT_SETTLEMENT");
        payment.setPaymentDate(LocalDate.of(2026, 9, 1));
        payment.setPayType("银行转账");
        payment.setAmount(new BigDecimal("100.00"));
        payment.setOperatorName("操作员");
        payment.setStatus(status);
        return payment;
    }

    private PaymentRequest request() {
        return new PaymentRequest(
                "PAY001",
                "供应商",
                10L,
                "STATEMENT_SETTLEMENT",
                "S001",
                "供应商A",
                null,
                null,
                null,
                "S001",
                "供应商A",
                1L,
                "主体A",
                2L,
                LocalDate.of(2026, 9, 1),
                "银行转账",
                new BigDecimal("100.00"),
                StatusConstants.DRAFT,
                "操作员",
                null,
                List.of(),
                false
        );
    }

    private void givenExistingEntity(Payment payment) {
        when(paymentRepository.findByIdAndDeletedFlagFalse(ID)).thenReturn(Optional.of(payment));
    }

    // ---------- 非法迁移 ----------

    @Test
    void updateStatus_shouldRejectTransitionOutsideTransitionTable() {
        Payment payment = entity(StatusConstants.DRAFT);
        givenExistingEntity(payment);

        assertThatThrownBy(() -> service.updateStatus(ID, StatusConstants.COMPLETED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从「草稿」变更为「已完成」");

        verify(paymentRepository, never()).save(any());
        verify(settlementSyncService, never()).syncLinkedStatements(any());
        verify(mutationGuardService, never()).assertStatusTransitionAllowed(any(), any(), any());
    }

    @Test
    void updateStatus_shouldRejectUnauditBeforeGuardBecauseTransitionTableForbidsIt() {
        Payment payment = entity(StatusConstants.AUDITED);
        givenExistingEntity(payment);

        assertThatThrownBy(() -> service.updateStatus(ID, StatusConstants.DRAFT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从「已审核」变更为「草稿」");

        verify(mutationGuardService, never()).assertStatusTransitionAllowed(any(), any(), any());
        verify(paymentRepository, never()).save(any());
    }

    // ---------- 终态拒绝 ----------

    @Test
    void delete_shouldRejectAuditedTerminalStatus() {
        Payment payment = entity(StatusConstants.AUDITED);
        givenExistingEntity(payment);

        assertThatThrownBy(() -> service.delete(ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前单据状态为「已审核」，不能删除");

        verify(mutationGuardService).lockRoot(ID);
        verify(mutationGuardService, never()).assertDeletable(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void update_shouldRejectEditInAuditedTerminalStatus() {
        Payment payment = entity(StatusConstants.AUDITED);
        givenExistingEntity(payment);

        assertThatThrownBy(() -> service.update(ID, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前单据状态为「已审核」，不能编辑");

        verify(applyService, never()).apply(any(), any(), any());
        verify(paymentRepository, never()).save(any());
    }

    // ---------- 空状态 ----------

    @Test
    void updateStatus_shouldRejectBlankStatus() {
        givenExistingEntity(entity(StatusConstants.DRAFT));

        assertThatThrownBy(() -> service.updateStatus(ID, "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");

        verify(paymentRepository, never()).save(any());
        verify(mutationGuardService, never()).assertStatusTransitionAllowed(any(), any(), any());
    }

    @Test
    void updateStatus_shouldRejectNullStatus() {
        givenExistingEntity(entity(StatusConstants.DRAFT));

        assertThatThrownBy(() -> service.updateStatus(ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");
    }

    // ---------- 等值短路 ----------

    @Test
    void updateStatus_shouldShortCircuitWhenStatusEqual() {
        Payment payment = entity(StatusConstants.DRAFT);
        givenExistingEntity(payment);
        PaymentResponse response = mock(PaymentResponse.class);
        when(responseAssembler.toDetailResponse(payment)).thenReturn(response);

        PaymentResponse result = service.updateStatus(ID, StatusConstants.DRAFT);

        assertThat(result).isSameAs(response);
        verify(mutationGuardService).lockRoot(ID);
        verify(mutationGuardService, never()).assertStatusTransitionAllowed(any(), any(), any());
        verify(paymentRepository, never()).save(any());
        verify(settlementSyncService, never()).syncLinkedStatements(any());
    }

    // ---------- 守卫 → 保存 → 同步 InOrder ----------

    @Test
    void updateStatus_shouldRunLockGuardSaveSyncInOrder() {
        Payment payment = entity(StatusConstants.DRAFT);
        givenExistingEntity(payment);
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateStatus(ID, StatusConstants.AUDITED);

        assertThat(payment.getStatus()).isEqualTo(StatusConstants.AUDITED);
        InOrder inOrder = inOrder(mutationGuardService, paymentRepository, settlementSyncService);
        inOrder.verify(mutationGuardService).lockRoot(ID);
        inOrder.verify(mutationGuardService).assertStatusTransitionAllowed(
                eq(payment), eq(StatusConstants.DRAFT), eq(StatusConstants.AUDITED));
        inOrder.verify(paymentRepository).save(payment);
        inOrder.verify(settlementSyncService).syncLinkedStatements(payment);
    }
}
