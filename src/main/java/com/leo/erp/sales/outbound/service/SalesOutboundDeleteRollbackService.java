package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
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
            order.setStatus(StatusConstants.DRAFT);
            salesOrderRepository.save(order);
            publishSalesOrderRollbackEvent(order);
        }
    }

    private void publishSalesOrderRollbackEvent(SalesOrder order) {
        businessOperationEventPublisher.publish(
                "SALES_ORDER_REOPENED_AFTER_OUTBOUND_DELETED",
                ModuleKeys.SALES_ORDER,
                "销售订单",
                "退回草稿",
                "SalesOrder",
                order.getId(),
                order.getOrderNo(),
                "删除销售出库后，销售订单状态 已审核 -> 草稿"
        );
    }
}
