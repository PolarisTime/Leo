package com.leo.erp.sales.order.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
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

class SalesOrderDownstreamGuardWiringTest {

    @Test
    void shouldCheckDownstreamBeforeReverseAuditingToDraft() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderDownstreamMutationGuard guard = mock(SalesOrderDownstreamMutationGuard.class);
        SalesOrder order = order(StatusConstants.AUDITED);
        SalesOrderSaveService saveService = mock(SalesOrderSaveService.class);
        SalesOrderService service = service(
                repository,
                mock(SalesOrderApplyService.class),
                mock(SalesOrderAuditedPricingService.class),
                saveService,
                guard
        );
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        doThrow(blocked()).when(guard).assertMutable(order, "反审核");

        assertThatThrownBy(() -> service.updateStatus(1L, StatusConstants.DRAFT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下游占用");

        verify(saveService, never()).saveStatus(any());
    }

    @Test
    void shouldCheckDownstreamBeforeDeletingDraftOrder() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderDownstreamMutationGuard guard = mock(SalesOrderDownstreamMutationGuard.class);
        SalesOrder order = order(StatusConstants.DRAFT);
        SalesOrderSaveService saveService = mock(SalesOrderSaveService.class);
        SalesOrderService service = service(
                repository,
                mock(SalesOrderApplyService.class),
                mock(SalesOrderAuditedPricingService.class),
                saveService,
                guard
        );
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        doThrow(blocked()).when(guard).assertMutable(order, "删除");

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下游占用");

        verify(saveService, never()).save(any());
    }

    @Test
    void shouldCheckDownstreamBeforeApplyingSourceLineChanges() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderApplyService applyService = mock(SalesOrderApplyService.class);
        SalesOrderAuditedPricingService pricingService = mock(SalesOrderAuditedPricingService.class);
        SalesOrderDownstreamMutationGuard guard = mock(SalesOrderDownstreamMutationGuard.class);
        SalesOrder order = order(StatusConstants.DRAFT);
        SalesOrderRequest request = request(2);
        SalesOrderService service = service(
                repository,
                applyService,
                pricingService,
                mock(SalesOrderSaveService.class),
                guard
        );
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        doThrow(blocked()).when(guard)
                .assertSourceLineMutationAllowed(order, request.items(), "修改");

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下游占用");

        verify(applyService, never()).apply(any(), any(), any());
    }

    @Test
    void shouldNotCheckDownstreamBeforeNewOrderIsPersisted() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderApplyService applyService = mock(SalesOrderApplyService.class);
        SalesOrderDownstreamMutationGuard guard = mock(SalesOrderDownstreamMutationGuard.class);
        SalesOrderSaveService saveService = mock(SalesOrderSaveService.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        SalesOrderRequest request = request(1);
        SalesOrderService service = service(
                repository,
                idGenerator,
                applyService,
                mock(SalesOrderAuditedPricingService.class),
                saveService,
                guard
        );
        when(idGenerator.nextId()).thenReturn(1L);
        when(saveService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request);

        verify(guard, never()).assertSourceLineMutationAllowed(any(), any(), any());
    }

    @Test
    void shouldCheckDownstreamWhenGeneralUpdateReverseAuditsToDraft() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderApplyService applyService = mock(SalesOrderApplyService.class);
        SalesOrderAuditedPricingService pricingService = mock(SalesOrderAuditedPricingService.class);
        SalesOrderProtectedUpdatePolicy protectedUpdatePolicy = mock(SalesOrderProtectedUpdatePolicy.class);
        SalesOrderDownstreamMutationGuard guard = mock(SalesOrderDownstreamMutationGuard.class);
        SalesOrder order = order(StatusConstants.AUDITED);
        SalesOrderRequest request = request(1);
        SalesOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                applyService,
                pricingService,
                protectedUpdatePolicy,
                mock(SalesOrderSaveService.class),
                guard
        );
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(protectedUpdatePolicy.allowsProtectedUpdate(order, request)).thenReturn(true);
        doThrow(blocked()).when(guard).assertMutable(order, "反审核");

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下游占用");

        verify(applyService, never()).apply(any(), any(), any());
    }

    @Test
    void shouldKeepSynchronizedAuditedPricingUpdateOutsideSourceMutationGuard() {
        SalesOrderRepository repository = mock(SalesOrderRepository.class);
        SalesOrderApplyService applyService = mock(SalesOrderApplyService.class);
        SalesOrderAuditedPricingService pricingService = mock(SalesOrderAuditedPricingService.class);
        SalesOrderProtectedUpdatePolicy protectedUpdatePolicy = mock(SalesOrderProtectedUpdatePolicy.class);
        SalesOrderDownstreamMutationGuard guard = mock(SalesOrderDownstreamMutationGuard.class);
        SalesOrderSaveService saveService = mock(SalesOrderSaveService.class);
        SalesOrder order = order(StatusConstants.AUDITED);
        SalesOrderRequest request = request(StatusConstants.AUDITED, 1);
        SalesOrderService service = service(
                repository,
                mock(SnowflakeIdGenerator.class),
                applyService,
                pricingService,
                protectedUpdatePolicy,
                saveService,
                guard
        );
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(protectedUpdatePolicy.allowsProtectedUpdate(order, request)).thenReturn(true);
        when(pricingService.isAuditedPricingUpdate(order, request)).thenReturn(true);
        when(saveService.saveAuditedPricingUpdate(order)).thenReturn(order);

        service.update(1L, request);

        verify(guard, never()).assertMutable(any(), any());
        verify(guard, never()).assertSourceLineMutationAllowed(any(), any(), any());
        verify(pricingService).applyAuditedPricingUpdate(order, request);
    }

    private SalesOrderService service(
            SalesOrderRepository repository,
            SalesOrderApplyService applyService,
            SalesOrderAuditedPricingService pricingService,
            SalesOrderSaveService saveService,
            SalesOrderDownstreamMutationGuard guard
    ) {
        return service(
                repository,
                mock(SnowflakeIdGenerator.class),
                applyService,
                pricingService,
                mock(SalesOrderProtectedUpdatePolicy.class),
                saveService,
                guard
        );
    }

    private SalesOrderService service(
            SalesOrderRepository repository,
            SnowflakeIdGenerator idGenerator,
            SalesOrderApplyService applyService,
            SalesOrderAuditedPricingService pricingService,
            SalesOrderSaveService saveService,
            SalesOrderDownstreamMutationGuard guard
    ) {
        return service(
                repository,
                idGenerator,
                applyService,
                pricingService,
                mock(SalesOrderProtectedUpdatePolicy.class),
                saveService,
                guard
        );
    }

    private SalesOrderService service(
            SalesOrderRepository repository,
            SnowflakeIdGenerator idGenerator,
            SalesOrderApplyService applyService,
            SalesOrderAuditedPricingService pricingService,
            SalesOrderProtectedUpdatePolicy protectedUpdatePolicy,
            SalesOrderSaveService saveService,
            SalesOrderDownstreamMutationGuard guard
    ) {
        return new SalesOrderService(
                repository,
                idGenerator,
                mock(SalesOrderResponseAssembler.class),
                applyService,
                mock(SalesOrderPurchaseAllocationService.class),
                pricingService,
                protectedUpdatePolicy,
                saveService,
                mock(SalesOrderItemRepository.class),
                mock(SourceAllocationLockService.class),
                null,
                null,
                guard
        );
    }

    private SalesOrder order(String status) {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-001");
        order.setStatus(status);
        SalesOrderItem item = new SalesOrderItem();
        item.setId(11L);
        item.setLineNo(1);
        item.setSourcePurchaseOrderItemId(21L);
        item.setSalesOrder(order);
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }

    private SalesOrderRequest request(Integer quantity) {
        return request(StatusConstants.DRAFT, quantity);
    }

    private SalesOrderRequest request(String status, Integer quantity) {
        SalesOrderItemRequest item = new SalesOrderItemRequest(
                11L,
                101L,
                "M1",
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "12m",
                "吨",
                null,
                21L,
                201L,
                "一号库",
                "B1",
                quantity,
                "件",
                new BigDecimal("0.10000000"),
                1,
                new BigDecimal("0.20000000"),
                new BigDecimal("4500.00"),
                new BigDecimal("900.00")
        );
        return new SalesOrderRequest(
                "SO-001",
                null,
                "PO-001",
                "CUS-1",
                301L,
                "客户A",
                401L,
                "项目A",
                501L,
                "结算主体A",
                LocalDate.of(2026, 7, 13),
                "销售员",
                status,
                null,
                List.of(item)
        );
    }

    private BusinessException blocked() {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, "下游占用");
    }
}
