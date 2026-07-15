package com.leo.erp.purchase.order.audit;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.system.operationlog.event.BusinessOperationEvent;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class PurchaseOrderAuditPublisher {

    private final BusinessOperationEventPublisher businessOperationEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    public PurchaseOrderAuditPublisher(BusinessOperationEventPublisher businessOperationEventPublisher,
                                       ApplicationEventPublisher applicationEventPublisher) {
        this.businessOperationEventPublisher = businessOperationEventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publish(PurchaseOrder order, String eventType, String actionType, String remark) {
        BusinessOperationEvent operation = businessOperationEventPublisher.publish(
                eventType,
                "purchase-order",
                "采购订单",
                actionType,
                "PurchaseOrder",
                order.getId(),
                order.getOrderNo(),
                remark
        );
        applicationEventPublisher.publishEvent(new PurchaseOrderSnapshotEvent(
                operation,
                PurchaseOrderAuditSnapshot.from(order)
        ));
    }
}
