package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.LongSupplier;

/**
 * 销售出库写侧工作流：来源锁定、明细应用、审核校验、保存与操作事件发布。
 * 事务边界由 SalesOutboundService 的门面方法控制，本类不再声明事务。
 */
@Service
public class SalesOutboundWorkflowService {

    private final SalesOutboundApplyService applyService;
    private final SalesOutboundSaveService saveService;
    private final SalesOutboundPurchaseInboundGuard purchaseInboundGuard;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final SalesOutboundDownstreamMutationGuard downstreamMutationGuard;
    private final BusinessOperationEventPublisher businessOperationEventPublisher;
    private SalesOutboundCoverageValidator coverageValidator;

    public SalesOutboundWorkflowService(SalesOutboundApplyService applyService,
                                        SalesOutboundSaveService saveService,
                                        SalesOutboundPurchaseInboundGuard purchaseInboundGuard,
                                        SourceAllocationLockService sourceAllocationLockService,
                                        SalesOutboundDownstreamMutationGuard downstreamMutationGuard,
                                        BusinessOperationEventPublisher businessOperationEventPublisher) {
        this.applyService = applyService;
        this.saveService = saveService;
        this.purchaseInboundGuard = purchaseInboundGuard;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.downstreamMutationGuard = downstreamMutationGuard;
        this.businessOperationEventPublisher = businessOperationEventPublisher;
    }

    @Autowired(required = false)
    void setCoverageValidator(SalesOutboundCoverageValidator coverageValidator) {
        this.coverageValidator = coverageValidator;
    }

    void lockSourceSalesOrderItems(List<SalesOutboundItem> existingItems,
                                   List<SalesOutboundItemRequest> requestedItems) {
        TreeSet<Long> sourceIds = new TreeSet<>();
        existingItems.stream()
                .map(SalesOutboundItem::getSourceSalesOrderItemId)
                .filter(Objects::nonNull)
                .forEach(sourceIds::add);
        requestedItems.stream()
                .map(SalesOutboundItemRequest::sourceSalesOrderItemId)
                .filter(Objects::nonNull)
                .forEach(sourceIds::add);
        sourceAllocationLockService.lockTradeItemSources(List.of(), List.of(), List.copyOf(sourceIds));
    }

    void apply(SalesOutbound entity, SalesOutboundRequest request, LongSupplier nextIdSupplier) {
        lockSourceSalesOrderItems(entity.getItems(), request.items());
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "销售出库状态",
                StatusConstants.ALLOWED_SALES_OUTBOUND_STATUS
        );
        entity.setOutboundNo(entity.getOutboundNo() == null ? request.outboundNo() : entity.getOutboundNo());
        entity.setSalesOrderNo(request.salesOrderNo());
        entity.setCustomerId(request.customerId());
        entity.setCustomerName(request.customerName());
        entity.setProjectId(request.projectId());
        entity.setProjectName(request.projectName());
        entity.setWarehouseId(request.warehouseId());
        entity.setOutboundDate(request.outboundDate());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());
        applyService.applyItems(entity, request, nextIdSupplier);
        if (coverageValidator != null) {
            coverageValidator.assertExactCoverage(entity);
        }
        if (StatusConstants.AUDITED.equals(nextStatus)) {
            purchaseInboundGuard.assertPurchaseInboundCompletedBeforeAudit(entity);
        }
    }

    void beforeStatusUpdate(SalesOutbound entity, String currentStatus, String nextStatus) {
        lockSourceSalesOrderItems(entity.getItems(), List.of());
        if (StatusConstants.AUDITED.equals(currentStatus) && StatusConstants.DRAFT.equals(nextStatus)) {
            sourceAllocationLockService.lockDocumentSources(
                    List.of(),
                    List.of(),
                    List.of(entity.getId()),
                    List.of()
            );
            downstreamMutationGuard.assertReverseAuditAllowed(entity);
        }
        if (StatusConstants.AUDITED.equals(nextStatus)) {
            purchaseInboundGuard.assertPurchaseInboundCompletedBeforeAudit(entity);
            if (coverageValidator != null) {
                coverageValidator.assertExactCoverage(entity);
            }
        }
    }

    SalesOutbound save(SalesOutbound entity) {
        return saveService.save(entity);
    }

    SalesOutbound saveCreated(SalesOutbound entity, SalesOutboundRequest request) {
        SalesOutbound saved = save(entity);
        publishEvent(saved, "SALES_OUTBOUND_CREATED", "新增", "新增销售出库 " + saved.getOutboundNo());
        return saved;
    }

    SalesOutbound saveUpdated(SalesOutbound entity, SalesOutboundRequest request) {
        SalesOutbound saved = save(entity);
        publishEvent(saved, "SALES_OUTBOUND_UPDATED", "编辑", "编辑销售出库 " + saved.getOutboundNo());
        return saved;
    }

    void publishStatusChanged(SalesOutbound outbound, String currentStatus, String nextStatus) {
        String actionType = StatusConstants.DRAFT.equals(nextStatus) ? "反审核" : "审核";
        publishEvent(outbound, "SALES_OUTBOUND_STATUS_CHANGED", actionType,
                "销售出库状态 " + currentStatus + " -> " + nextStatus);
    }

    void publishDeleted(SalesOutbound entity) {
        publishEvent(entity, "SALES_OUTBOUND_DELETED", "删除", "删除销售出库 " + entity.getOutboundNo());
    }

    private void publishEvent(SalesOutbound outbound, String eventType, String actionType, String remark) {
        businessOperationEventPublisher.publish(
                eventType,
                "sales-outbound",
                "销售出库",
                actionType,
                "SalesOutbound",
                outbound.getId(),
                outbound.getOutboundNo(),
                remark
        );
    }
}
