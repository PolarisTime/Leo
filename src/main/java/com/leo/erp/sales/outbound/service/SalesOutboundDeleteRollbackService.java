package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.service.SalesOrderDownstreamMutationGuard;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 销售出库删除守卫与来源销售订单回退：删除前校验下游引用，
 * 删除时把不再被其他出库引用的已审核来源订单退回草稿并发布事件。
 */
@Service
public class SalesOutboundDeleteRollbackService {

    /**
     * 来源销售订单状态回退的唯一写入口。
     *
     * <p>直接 {@code SalesOrder#setStatus} 属「跨聚合旁路写」：状态门禁
     * （{@code ModuleBoundaryVerifier} 的 sanctioned 白名单）只允许 {@code *ApplyService}、
     * {@code *CompletionSyncService}、{@code *ForceCloseService} 与 {@link CrudStatusGuard} 写实体状态。
     * 本类是销售出库域的删除回退服务，不属于上述任何一类，因此统一走框架守卫写状态，
     * 行为与改造前完全一致（已审核 → 草稿，前置条件由 {@link #rollbackSourceSalesOrders} 校验）。</p>
     */
    private static final CrudStatusGuard<SalesOrder> SALES_ORDER_STATUS_GUARD =
            CrudStatusGuard.forStatusAwareEntities();

    private final SalesOutboundRepository repository;
    private final SalesOutboundApplyService applyService;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final SalesOutboundDownstreamMutationGuard downstreamMutationGuard;
    private final BusinessOperationEventPublisher businessOperationEventPublisher;
    private SalesOrderRepository salesOrderRepository;
    private SalesOrderDownstreamMutationGuard salesOrderDownstreamMutationGuard;

    public SalesOutboundDeleteRollbackService(SalesOutboundRepository repository,
                                              SalesOutboundApplyService applyService,
                                              SourceAllocationLockService sourceAllocationLockService,
                                              SalesOutboundDownstreamMutationGuard downstreamMutationGuard,
                                              BusinessOperationEventPublisher businessOperationEventPublisher) {
        this.repository = repository;
        this.applyService = applyService;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.downstreamMutationGuard = downstreamMutationGuard;
        this.businessOperationEventPublisher = businessOperationEventPublisher;
    }

    @Autowired
    void setSalesOrderRepository(SalesOrderRepository salesOrderRepository) {
        this.salesOrderRepository = salesOrderRepository;
    }

    @Autowired
    void setSalesOrderDownstreamMutationGuard(SalesOrderDownstreamMutationGuard salesOrderDownstreamMutationGuard) {
        this.salesOrderDownstreamMutationGuard = salesOrderDownstreamMutationGuard;
    }

    void beforeDelete(SalesOutbound entity) {
        if (StatusConstants.AUDITED.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核销售出库必须先反审核为草稿才能删除");
        }
        downstreamMutationGuard.assertDeleteAllowed(entity);
        List<Long> sourceSalesOrderIds = applyService.sourceSalesOrderIds(entity).stream()
                .sorted()
                .toList();
        sourceAllocationLockService.lockDocumentSources(
                List.of(),
                sourceSalesOrderIds,
                List.of(entity.getId()),
                List.of()
        );
        rollbackSourceSalesOrders(entity, sourceSalesOrderIds);
    }

    private void rollbackSourceSalesOrders(SalesOutbound entity, List<Long> sourceSalesOrderIds) {
        if (salesOrderRepository == null) {
            return;
        }
        for (Long sourceSalesOrderId : sourceSalesOrderIds) {
            var order = salesOrderRepository.findForUpdateByIdAndDeletedFlagFalse(sourceSalesOrderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "来源销售订单不存在或已删除"));
            List<Long> itemIds = order.getItems().stream()
                    .map(com.leo.erp.sales.order.domain.entity.SalesOrderItem::getId)
                    .filter(Objects::nonNull)
                    .toList();
            long remainingOutbounds = itemIds.isEmpty() ? 0
                    : repository.countActiveBySourceSalesOrderItemIdsExcludingOutbound(itemIds, entity.getId());
            if (remainingOutbounds > 0) {
                continue;
            }
            if (!StatusConstants.AUDITED.equals(order.getStatus())) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "来源销售订单当前状态不是已审核，不能删除销售出库"
                );
            }
            if (salesOrderDownstreamMutationGuard != null) {
                salesOrderDownstreamMutationGuard.assertNoFreightReference(order, "删除销售出库");
            }
            // 走 sanctioned 状态写入口（见 SALES_ORDER_STATUS_GUARD），不做跨聚合直接 setStatus
            SALES_ORDER_STATUS_GUARD.writeStatus(order, StatusConstants.DRAFT);
            salesOrderRepository.save(order);
            publishSalesOrderRollbackEvent(order);
        }
    }

    private void publishSalesOrderRollbackEvent(SalesOrder order) {
        businessOperationEventPublisher.publish(
                "SALES_ORDER_REOPENED_AFTER_OUTBOUND_DELETED",
                ModuleKeys.SALES_ORDER,
                ModuleCatalog.moduleName(ModuleKeys.SALES_ORDER),
                "退回草稿",
                "SalesOrder",
                order.getId(),
                order.getOrderNo(),
                "删除销售出库后，销售订单状态 已审核 -> 草稿"
        );
    }
}
