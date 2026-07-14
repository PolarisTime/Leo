package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.mapper.PurchaseInboundMapper;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseInboundStatementGuardWiringTest {

    @Test
    void shouldCheckStatementBeforeDeletingDraftInbound() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundStatementGuard guard = mock(PurchaseInboundStatementGuard.class);
        PurchaseInbound inbound = inbound();
        PurchaseInboundService service = service(repository, mock(PurchaseInboundApplyService.class), guard);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(inbound));
        doThrow(blocked()).when(guard).assertMutable(inbound, "删除");

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下游占用");

        verify(repository, never()).save(any());
    }

    @Test
    void shouldCheckStatementBeforeApplyingSourceLineChanges() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundApplyService applyService = mock(PurchaseInboundApplyService.class);
        PurchaseInboundStatementGuard guard = mock(PurchaseInboundStatementGuard.class);
        PurchaseInbound inbound = inbound();
        PurchaseInboundRequest request = request(2);
        PurchaseInboundService service = service(repository, applyService, guard);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(inbound));
        doThrow(blocked()).when(guard)
                .assertSourceLineMutationAllowed(inbound, request.items(), "修改");

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下游占用");

        verify(applyService, never()).applyItems(any(), any(), any());
    }

    @Test
    void shouldNotCheckStatementBeforeNewInboundIsPersisted() {
        PurchaseInboundRepository repository = mock(PurchaseInboundRepository.class);
        PurchaseInboundApplyService applyService = mock(PurchaseInboundApplyService.class);
        PurchaseInboundStatementGuard guard = mock(PurchaseInboundStatementGuard.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PurchaseInboundRequest request = request(1);
        PurchaseInboundService service = service(repository, idGenerator, applyService, guard);
        when(idGenerator.nextId()).thenReturn(1L);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request);

        verify(guard, never()).assertSourceLineMutationAllowed(any(), any(), any());
    }

    private PurchaseInboundService service(
            PurchaseInboundRepository repository,
            PurchaseInboundApplyService applyService,
            PurchaseInboundStatementGuard guard
    ) {
        return service(repository, mock(SnowflakeIdGenerator.class), applyService, guard);
    }

    private PurchaseInboundService service(
            PurchaseInboundRepository repository,
            SnowflakeIdGenerator idGenerator,
            PurchaseInboundApplyService applyService,
            PurchaseInboundStatementGuard guard
    ) {
        return new PurchaseInboundService(
                repository,
                idGenerator,
                mock(PurchaseInboundMapper.class),
                applyService,
                mock(PurchaseInboundDeleteService.class),
                mock(PurchaseInboundCompletionSyncService.class),
                mock(PurchaseInboundResponseAssembler.class),
                mock(PurchaseInboundPieceWeightService.class),
                mock(WorkflowTransitionGuard.class),
                mock(SourceAllocationLockService.class),
                mock(PurchaseInboundWeightSettlementService.class),
                mock(PurchaseInboundRefundGuard.class),
                guard
        );
    }

    private PurchaseInbound inbound() {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(1L);
        inbound.setInboundNo("PI-001");
        inbound.setStatus(StatusConstants.DRAFT);
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(11L);
        item.setLineNo(1);
        item.setSourcePurchaseOrderItemId(21L);
        item.setPurchaseInbound(inbound);
        inbound.setItems(new ArrayList<>(List.of(item)));
        return inbound;
    }

    private PurchaseInboundRequest request(Integer quantity) {
        PurchaseInboundItemRequest item = new PurchaseInboundItemRequest(
                11L,
                101L,
                "M1",
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "12m",
                "吨",
                21L,
                201L,
                "一号库",
                "理算",
                "B1",
                quantity,
                "件",
                new BigDecimal("0.10000000"),
                1,
                new BigDecimal("0.20000000"),
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("4000.00"),
                new BigDecimal("800.00")
        );
        return new PurchaseInboundRequest(
                "PI-001",
                "PO-001",
                301L,
                "SUP-1",
                "供应商A",
                201L,
                "一号库",
                LocalDate.of(2026, 7, 13),
                "理算",
                StatusConstants.DRAFT,
                null,
                List.of(item)
        );
    }

    private BusinessException blocked() {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, "下游占用");
    }
}
