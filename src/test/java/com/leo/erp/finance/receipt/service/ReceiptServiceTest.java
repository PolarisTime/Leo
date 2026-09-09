package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.common.service.SupplierPrepaymentBalanceService;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.mapper.ReceiptMapper;
import com.leo.erp.finance.receipt.repository.ReceiptRepository;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReceiptService 显式状态断言序列边界测试：非法迁移、终态拒绝、空状态、
 * 等值短路、锁→守卫→保存→对账同步 InOrder。
 */
@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    private static final Long ID = 9L;

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private ReceiptMapper receiptMapper;

    @Mock
    private ReceiptApplyService applyService;

    @Mock
    private ReceiptAllocationService receiptAllocationService;

    @Mock
    private ReceiptAllocationResponseAssembler allocationResponseAssembler;

    @Mock
    private ReceiptSettlementSyncService settlementSyncService;

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    @Mock
    private SupplierPrepaymentBalanceService supplierPrepaymentBalanceService;

    @InjectMocks
    private ReceiptService service;

    private Receipt entity(String status) {
        Receipt receipt = new Receipt();
        receipt.setId(ID);
        receipt.setReceiptNo("RC001");
        receipt.setCounterpartyName("客户A");
        receipt.setReceiptDate(LocalDate.of(2026, 9, 1));
        receipt.setPayType("银行转账");
        receipt.setAmount(new BigDecimal("100.00"));
        receipt.setOperatorName("操作员");
        receipt.setStatus(status);
        return receipt;
    }

    private void givenExistingEntity(Receipt receipt) {
        when(receiptRepository.findByIdAndDeletedFlagFalse(ID)).thenReturn(Optional.of(receipt));
    }

    private ReceiptResponse response() {
        return new ReceiptResponse(
                ID, "RC001", "客户", 10L, "C001", "客户A",
                "CUSTOMER_STATEMENT_SETTLEMENT", 10L, "C001", "客户A",
                20L, "项目A", 1L, "主体A", 2L, null,
                LocalDate.of(2026, 9, 1), "银行转账", new BigDecimal("100.00"),
                StatusConstants.DRAFT, false, "操作员", null, List.of()
        );
    }

    // ---------- 空状态 ----------

    @Test
    void updateStatus_shouldRejectBlankStatus() {
        givenExistingEntity(entity(StatusConstants.DRAFT));

        assertThatThrownBy(() -> service.updateStatus(ID, "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");

        verify(receiptRepository, never()).save(any());
    }

    @Test
    void updateStatus_shouldRejectNullStatus() {
        givenExistingEntity(entity(StatusConstants.DRAFT));

        assertThatThrownBy(() -> service.updateStatus(ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");
    }

    // ---------- 非法迁移 ----------

    @Test
    void updateStatus_shouldRejectTransitionOutsideTransitionTable() {
        Receipt receipt = entity(StatusConstants.DRAFT);
        givenExistingEntity(receipt);

        assertThatThrownBy(() -> service.updateStatus(ID, StatusConstants.COMPLETED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从「草稿」变更为「已完成」");

        verify(receiptRepository, never()).save(any());
        verify(settlementSyncService, never()).syncCustomerStatements(any());
        verify(sourceAllocationLockService, never()).lockStatementSources(anyList(), anyList());
        verify(receiptAllocationService, never()).validateExistingAllocationsForSettlement(any(), any());
    }

    @Test
    void updateStatus_shouldRejectUnauditFromAuditedTerminalStatus() {
        Receipt receipt = entity(StatusConstants.AUDITED);
        givenExistingEntity(receipt);

        assertThatThrownBy(() -> service.updateStatus(ID, StatusConstants.DRAFT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从「已审核」变更为「草稿」");

        verify(receiptRepository, never()).save(any());
    }

    // ---------- 终态拒绝 ----------

    @Test
    void delete_shouldRejectAuditedTerminalStatus() {
        Receipt receipt = entity(StatusConstants.AUDITED);
        givenExistingEntity(receipt);

        assertThatThrownBy(() -> service.delete(ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前单据状态为「已审核」，不能删除");

        verify(receiptRepository).findByIdAndDeletedFlagFalseForUpdate(ID);
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void delete_shouldRejectLegacyReceivedReadOnlyStatus() {
        Receipt receipt = entity(StatusConstants.LEGACY_RECEIVED);
        givenExistingEntity(receipt);

        assertThatThrownBy(() -> service.delete(ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("历史已收款单据仅供查询，不允许删除");

        verify(receiptRepository, never()).save(any());
        verify(sourceAllocationLockService, never()).lockStatementSources(anyList(), anyList());
    }

    // ---------- 守卫拒绝：供应商收款缺少身份 ----------

    @Test
    void updateStatus_shouldRejectSupplierReceiptWithoutCounterpartyIdentity() {
        Receipt receipt = entity(StatusConstants.DRAFT);
        receipt.setReceiptPurpose("SUPPLIER_PREPAYMENT_REFUND");
        receipt.setCounterpartyId(null);
        receipt.setSettlementCompanyId(null);
        givenExistingEntity(receipt);

        assertThatThrownBy(() -> service.updateStatus(ID, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商收款缺少供应商或结算主体身份");

        verify(receiptRepository, never()).save(any());
        verify(supplierPrepaymentBalanceService, never()).validateSupplierReceipt(any(), any());
        verify(sourceAllocationLockService, never()).lockStatementSources(anyList(), anyList());
    }

    // ---------- 等值短路 ----------

    @Test
    void updateStatus_shouldShortCircuitWhenStatusEqual() {
        Receipt receipt = entity(StatusConstants.DRAFT);
        givenExistingEntity(receipt);
        when(receiptMapper.toResponse(receipt)).thenReturn(response());

        ReceiptResponse result = service.updateStatus(ID, StatusConstants.DRAFT);

        assertThat(result.receiptNo()).isEqualTo("RC001");
        assertThat(result.status()).isEqualTo(StatusConstants.DRAFT);
        verify(receiptRepository).findByIdAndDeletedFlagFalseForUpdate(ID);
        verify(receiptRepository, never()).save(any());
        verify(receiptAllocationService, never()).validateExistingAllocationsForSettlement(any(), any());
        verify(supplierPrepaymentBalanceService, never()).validateSupplierReceipt(any(), any());
        verify(settlementSyncService, never()).syncCustomerStatements(any());
    }

    // ---------- 锁 → 守卫 → 保存 → 同步 InOrder ----------

    @Test
    void updateStatus_shouldRunLockGuardsSaveSyncInOrder() {
        Receipt receipt = entity(StatusConstants.DRAFT);
        givenExistingEntity(receipt);
        when(receiptMapper.toResponse(receipt)).thenReturn(response());
        when(receiptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateStatus(ID, StatusConstants.AUDITED);

        assertThat(receipt.getStatus()).isEqualTo(StatusConstants.AUDITED);
        InOrder inOrder = inOrder(
                receiptRepository,
                sourceAllocationLockService,
                settlementSyncService,
                receiptAllocationService
        );
        inOrder.verify(receiptRepository).findByIdAndDeletedFlagFalseForUpdate(ID);
        inOrder.verify(sourceAllocationLockService).lockStatementSources(List.of(), List.of());
        inOrder.verify(settlementSyncService).captureOriginalAllocationStatementIds(receipt);
        inOrder.verify(receiptAllocationService).validateExistingAllocationsForSettlement(
                receipt, StatusConstants.AUDITED);
        inOrder.verify(receiptRepository).save(receipt);
        inOrder.verify(settlementSyncService).syncCustomerStatements(receipt);
    }
}
