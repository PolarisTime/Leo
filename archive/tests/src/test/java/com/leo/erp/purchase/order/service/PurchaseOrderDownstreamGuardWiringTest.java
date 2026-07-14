package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseOrderDownstreamGuardWiringTest {

    @Test
    void shouldCheckDownstreamBeforeReverseAuditingToDraft() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderDownstreamMutationGuard guard = mock(PurchaseOrderDownstreamMutationGuard.class);
        PurchaseOrder order = order(StatusConstants.AUDITED);
        PurchaseOrderService service = service(repository, mock(PurchaseOrderApplyService.class), guard);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        doThrow(blocked()).when(guard).assertMutable(order, "反审核");

        assertThatThrownBy(() -> service.updateStatus(1L, StatusConstants.DRAFT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下游占用");

        verify(repository, never()).save(any());
    }

    @Test
    void shouldCheckDownstreamBeforeDeletingDraftOrder() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderDownstreamMutationGuard guard = mock(PurchaseOrderDownstreamMutationGuard.class);
        PurchaseOrder order = order(StatusConstants.DRAFT);
        PurchaseOrderService service = service(repository, mock(PurchaseOrderApplyService.class), guard);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        doThrow(blocked()).when(guard).assertMutable(order, "删除");

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下游占用");

        verify(repository, never()).save(any());
    }

    @Test
    void shouldCheckDownstreamBeforeApplyingSourceLineChanges() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderApplyService applyService = mock(PurchaseOrderApplyService.class);
        PurchaseOrderDownstreamMutationGuard guard = mock(PurchaseOrderDownstreamMutationGuard.class);
        PurchaseOrder order = order(StatusConstants.DRAFT);
        PurchaseOrderRequest request = request(2);
        PurchaseOrderService service = service(repository, applyService, guard);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        doThrow(blocked()).when(guard)
                .assertSourceLineMutationAllowed(order, request.items(), "修改");

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下游占用");

        verify(applyService, never()).applyItems(any(), any(), any());
    }

    @Test
    void shouldNotCheckDownstreamBeforeNewOrderIsPersisted() {
        PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
        PurchaseOrderApplyService applyService = mock(PurchaseOrderApplyService.class);
        PurchaseOrderDownstreamMutationGuard guard = mock(PurchaseOrderDownstreamMutationGuard.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        PurchaseOrderRequest request = request(1);
        PurchaseOrderService service = service(repository, idGenerator, applyService, guard);
        when(idGenerator.nextId()).thenReturn(1L);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request);

        verify(guard, never()).assertSourceLineMutationAllowed(any(), any(), any());
    }

    private PurchaseOrderService service(
            PurchaseOrderRepository repository,
            PurchaseOrderApplyService applyService,
            PurchaseOrderDownstreamMutationGuard guard
    ) {
        return service(repository, mock(SnowflakeIdGenerator.class), applyService, guard);
    }

    private PurchaseOrderService service(
            PurchaseOrderRepository repository,
            SnowflakeIdGenerator idGenerator,
            PurchaseOrderApplyService applyService,
            PurchaseOrderDownstreamMutationGuard guard
    ) {
        PurchaseOrderSupplierResolver supplierResolver = mock(PurchaseOrderSupplierResolver.class);
        when(supplierResolver.requireMasterSupplier(any(), anyString(), anyString()))
                .thenReturn(new PurchaseOrderSupplierResolver.SupplierIdentity(301L, "SUP-1", "供应商A"));
        return new PurchaseOrderService(
                repository,
                idGenerator,
                mock(PurchaseOrderAvailabilityService.class),
                mock(PurchaseOrderResponseAssembler.class),
                supplierResolver,
                applyService,
                mock(PurchaseOrderPieceWeightQueryService.class),
                mock(WorkflowTransitionGuard.class),
                null,
                null,
                null,
                null,
                guard
        );
    }

    private PurchaseOrder order(String status) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setOrderNo("PO-001");
        order.setSupplierId(301L);
        order.setSupplierCode("SUP-1");
        order.setSupplierName("供应商A");
        order.setStatus(status);
        order.setSettlementCompanyId(401L);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(11L);
        item.setLineNo(1);
        item.setPurchaseOrder(order);
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }

    private PurchaseOrderRequest request(Integer quantity) {
        PurchaseOrderItemRequest item = new PurchaseOrderItemRequest(
                11L,
                101L,
                "M1",
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "12m",
                "吨",
                201L,
                "一号库",
                "B1",
                quantity,
                "件",
                new BigDecimal("0.10000000"),
                1,
                new BigDecimal("0.20000000"),
                new BigDecimal("4000.00"),
                new BigDecimal("800.00")
        );
        return new PurchaseOrderRequest(
                "PO-001",
                301L,
                "SUP-1",
                "供应商A",
                LocalDateTime.of(2026, 7, 13, 0, 0),
                "采购员",
                401L,
                StatusConstants.DRAFT,
                null,
                List.of(item)
        );
    }

    private BusinessException blocked() {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, "下游占用");
    }
}
