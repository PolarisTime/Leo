package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 采购入库写侧工作流：完成状态同步保存、过磅重量回写、
 * 来源采购订单完成度同步与操作事件发布。
 * 事务边界由 PurchaseInboundService 的门面方法控制，本类不再声明事务。
 */
@Service
public class PurchaseInboundWorkflowService {

    private final PurchaseInboundRepository repository;
    private final PurchaseInboundCompletionSyncService completionSyncService;
    private final PurchaseInboundWeightWriteBackService weightWriteBackService;
    private final PurchaseInboundDeleteService deleteService;
    private final BusinessOperationEventPublisher businessOperationEventPublisher;

    public PurchaseInboundWorkflowService(PurchaseInboundRepository repository,
                                          PurchaseInboundCompletionSyncService completionSyncService,
                                          PurchaseInboundWeightWriteBackService weightWriteBackService,
                                          PurchaseInboundDeleteService deleteService,
                                          BusinessOperationEventPublisher businessOperationEventPublisher) {
        this.repository = repository;
        this.completionSyncService = completionSyncService;
        this.weightWriteBackService = weightWriteBackService;
        this.deleteService = deleteService;
        this.businessOperationEventPublisher = businessOperationEventPublisher;
    }

    PurchaseInbound save(PurchaseInbound entity) {
        boolean completedByServer = completionSyncService.shouldCompleteInbound(entity);
        if (completedByServer) {
            entity.setStatus(StatusConstants.INBOUND_COMPLETED);
        }
        PurchaseInbound saved = repository.save(entity);
        repository.flush();
        weightWriteBackService.synchronizeAfterSave(saved);
        completionSyncService.synchronizeSourcePurchaseOrders(
                saved,
                saved.isSourcePurchaseOrderReopenAllowed()
        );
        saved.setSourcePurchaseOrderReopenAllowed(false);
        return saved;
    }

    PurchaseInbound saveCreated(PurchaseInbound entity, PurchaseInboundRequest request) {
        PurchaseInbound saved = save(entity);
        publishEvent(saved, "PURCHASE_INBOUND_CREATED", "新增", "新增采购入库 " + saved.getInboundNo());
        return saved;
    }

    PurchaseInbound saveUpdated(PurchaseInbound entity, PurchaseInboundRequest request) {
        PurchaseInbound saved = save(entity);
        publishEvent(saved, "PURCHASE_INBOUND_UPDATED", "编辑", "编辑采购入库 " + saved.getInboundNo());
        return saved;
    }

    PurchaseInbound saveStatus(PurchaseInbound entity) {
        return save(entity);
    }

    void publishStatusChanged(PurchaseInbound inbound, String currentStatus, String nextStatus) {
        String actionType = StatusConstants.DRAFT.equals(nextStatus) ? "反审核" : "状态变更";
        publishEvent(inbound, "PURCHASE_INBOUND_STATUS_CHANGED", actionType,
                "采购入库状态 " + currentStatus + " -> " + nextStatus);
    }

    void afterDelete(PurchaseInbound inbound) {
        repository.flush();
        deleteService.afterDelete(inbound);
        publishEvent(inbound, "PURCHASE_INBOUND_DELETED", "删除", "删除采购入库 " + inbound.getInboundNo());
    }

    private void publishEvent(PurchaseInbound inbound, String eventType, String actionType, String remark) {
        businessOperationEventPublisher.publish(
                eventType,
                "purchase-inbound",
                "采购入库",
                actionType,
                "PurchaseInbound",
                inbound.getId(),
                inbound.getInboundNo(),
                remark
        );
    }
}
