package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.service.SalesOrderDownstreamMutationGuard;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SalesOutboundDeleteRollbackService 边界测试：
 * 已审核拒绝、下游引用守卫、来源订单回退与空依赖容错。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalesOutboundDeleteRollbackServiceTest {

    @Mock
    private SalesOutboundRepository repository;

    @Mock
    private SalesOutboundApplyService applyService;

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    @Mock
    private SalesOutboundDownstreamMutationGuard downstreamMutationGuard;

    @Mock
    private BusinessOperationEventPublisher businessOperationEventPublisher;

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private SalesOrderDownstreamMutationGuard salesOrderDownstreamMutationGuard;

    @InjectMocks
    private SalesOutboundDeleteRollbackService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // @InjectMocks 构造注入后不会自动调用包级 setter，手动补齐可空协作对象
        service.setSalesOrderRepository(salesOrderRepository);
        service.setSalesOrderDownstreamMutationGuard(salesOrderDownstreamMutationGuard);
    }

    private SalesOutbound outbound(String status) {
        SalesOutbound entity = new SalesOutbound();
        entity.setId(5L);
        entity.setOutboundNo("OB001");
        entity.setStatus(status);
        return entity;
    }

    private SalesOrder auditedOrder(Long id, List<Long> itemIds) {
        SalesOrder order = new SalesOrder();
        order.setId(id);
        order.setOrderNo("SO001");
        order.setStatus(StatusConstants.AUDITED);
        order.setItems(itemIds.stream().map(itemId -> {
            com.leo.erp.sales.order.domain.entity.SalesOrderItem item =
                    new com.leo.erp.sales.order.domain.entity.SalesOrderItem();
            item.setId(itemId);
            return item;
        }).toList());
        return order;
    }

    @Test
    void beforeDelete_shouldRejectAuditedOutbound() {
        SalesOutbound entity = outbound(StatusConstants.AUDITED);

        assertThatThrownBy(() -> service.beforeDelete(entity))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须先反审核");
        verify(sourceAllocationLockService, never()).lockDocumentSources(anyList(), anyList(), anyList(), anyList());
    }

    @Test
    void beforeDelete_shouldGuardDownstreamAndLockSources() {
        SalesOutbound entity = outbound(StatusConstants.DRAFT);
        doReturn(List.of()).when(applyService).sourceSalesOrderIds(entity);

        service.beforeDelete(entity);

        verify(downstreamMutationGuard).assertDeleteAllowed(entity);
        verify(sourceAllocationLockService).lockDocumentSources(
                List.of(), List.of(), List.of(entity.getId()), List.of());
    }

    @Test
    void beforeDelete_shouldRollbackSourceOrderToDraft() {
        SalesOutbound entity = outbound(StatusConstants.DRAFT);
        SalesOutboundItem item = new SalesOutboundItem();
        item.setSourceSalesOrderItemId(11L);
        entity.setItems(List.of(item));
        doReturn(List.of(1L)).when(applyService).sourceSalesOrderIds(entity);
        SalesOrder order = auditedOrder(1L, List.of(101L));
        when(salesOrderRepository.findForUpdateByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(repository.countActiveBySourceSalesOrderItemIdsExcludingOutbound(List.of(101L), 5L)).thenReturn(0L);

        service.beforeDelete(entity);

        assertThat(order.getStatus()).isEqualTo(StatusConstants.DRAFT);
        verify(salesOrderRepository).save(order);
        verify(businessOperationEventPublisher).publish(
                eq("SALES_ORDER_REOPENED_AFTER_OUTBOUND_DELETED"), any(), any(), any(), any(),
                eq(1L), eq("SO001"), any());
    }

    @Test
    void beforeDelete_shouldSkipRollbackWhenOtherOutboundsRemain() {
        SalesOutbound entity = outbound(StatusConstants.DRAFT);
        doReturn(List.of(1L)).when(applyService).sourceSalesOrderIds(entity);
        SalesOrder order = auditedOrder(1L, List.of(101L));
        when(salesOrderRepository.findForUpdateByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        when(repository.countActiveBySourceSalesOrderItemIdsExcludingOutbound(List.of(101L), 5L)).thenReturn(2L);

        service.beforeDelete(entity);

        assertThat(order.getStatus()).isEqualTo(StatusConstants.AUDITED);
        verify(salesOrderRepository, never()).save(order);
        verify(businessOperationEventPublisher, never()).publish(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void beforeDelete_shouldSkipRollbackWhenOrderHasNoItems() {
        SalesOutbound entity = outbound(StatusConstants.DRAFT);
        doReturn(List.of(1L)).when(applyService).sourceSalesOrderIds(entity);
        SalesOrder order = auditedOrder(1L, List.of());
        when(salesOrderRepository.findForUpdateByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));

        service.beforeDelete(entity);

        assertThat(order.getStatus()).isEqualTo(StatusConstants.DRAFT);
        verify(repository, never()).countActiveBySourceSalesOrderItemIdsExcludingOutbound(anyList(), eq(5L));
    }

    @Test
    void beforeDelete_shouldRejectNonAuditedSourceOrder() {
        SalesOutbound entity = outbound(StatusConstants.DRAFT);
        doReturn(List.of(1L)).when(applyService).sourceSalesOrderIds(entity);
        SalesOrder order = auditedOrder(1L, List.of());
        order.setStatus(StatusConstants.DRAFT);
        when(salesOrderRepository.findForUpdateByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.beforeDelete(entity))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单当前状态不是已审核");
    }

    @Test
    void beforeDelete_shouldRejectMissingSourceOrder() {
        SalesOutbound entity = outbound(StatusConstants.DRAFT);
        doReturn(List.of(1L)).when(applyService).sourceSalesOrderIds(entity);
        when(salesOrderRepository.findForUpdateByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.beforeDelete(entity))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单不存在或已删除");
    }

    @Test
    void beforeDelete_shouldGuardFreightReferenceBeforeReopen() {
        SalesOutbound entity = outbound(StatusConstants.DRAFT);
        doReturn(List.of(1L)).when(applyService).sourceSalesOrderIds(entity);
        SalesOrder order = auditedOrder(1L, List.of());
        when(salesOrderRepository.findForUpdateByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));

        service.beforeDelete(entity);

        verify(salesOrderDownstreamMutationGuard).assertNoFreightReference(order, "删除销售出库");
        assertThat(order.getStatus()).isEqualTo(StatusConstants.DRAFT);
    }

    @Test
    void beforeDelete_shouldTolerateNullOptionalCollaborators() {
        SalesOutboundDeleteRollbackService bareService = new SalesOutboundDeleteRollbackService(
                repository, applyService, sourceAllocationLockService, downstreamMutationGuard,
                businessOperationEventPublisher);
        SalesOutbound entity = outbound(StatusConstants.DRAFT);
        doReturn(List.of(1L)).when(applyService).sourceSalesOrderIds(entity);
        SalesOrder order = auditedOrder(1L, List.of());
        when(salesOrderRepository.findForUpdateByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(order));
        bareService.setSalesOrderRepository(salesOrderRepository);

        assertThatCode(() -> bareService.beforeDelete(entity)).doesNotThrowAnyException();

        assertThat(order.getStatus()).isEqualTo(StatusConstants.DRAFT);
    }

    @Test
    void beforeDelete_shouldSkipRollbackEntirelyWhenSalesOrderRepositoryMissing() {
        SalesOutboundDeleteRollbackService bareService = new SalesOutboundDeleteRollbackService(
                repository, applyService, sourceAllocationLockService, downstreamMutationGuard,
                businessOperationEventPublisher);
        SalesOutbound entity = outbound(StatusConstants.DRAFT);
        doReturn(List.of(1L)).when(applyService).sourceSalesOrderIds(entity);

        assertThatCode(() -> bareService.beforeDelete(entity)).doesNotThrowAnyException();

        verify(salesOrderRepository, never()).findForUpdateByIdAndDeletedFlagFalse(1L);
    }
}
