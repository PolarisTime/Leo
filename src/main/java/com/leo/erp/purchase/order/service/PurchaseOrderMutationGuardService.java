package com.leo.erp.purchase.order.service;

import com.leo.erp.purchase.api.PurchaseOrderPrepaymentReferenceGuard;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class PurchaseOrderMutationGuardService {

    private final PurchaseOrderDownstreamMutationGuard downstreamMutationGuard;
    private final PurchaseOrderPrepaymentReferenceGuard purchasePrepaymentReferenceGuard;

    public PurchaseOrderMutationGuardService(PurchaseOrderDownstreamMutationGuard downstreamMutationGuard,
                                             PurchaseOrderPrepaymentReferenceGuard purchasePrepaymentReferenceGuard) {
        this.downstreamMutationGuard = downstreamMutationGuard;
        this.purchasePrepaymentReferenceGuard = purchasePrepaymentReferenceGuard;
    }

    void assertUpdateAllowed(PurchaseOrder purchaseOrder, List<PurchaseOrderItemRequest> requestedItems) {
        if (purchaseOrder.getId() == null) {
            return;
        }
        if (downstreamMutationGuard != null
                && purchaseOrder.getItems().stream().anyMatch(item -> item.getId() != null)) {
            downstreamMutationGuard.assertSourceLineMutationAllowed(purchaseOrder, requestedItems, "修改");
        }
        assertNoActivePrepayment(purchaseOrder, "修改");
    }

    void assertMutable(PurchaseOrder purchaseOrder, String action) {
        if (downstreamMutationGuard != null) {
            downstreamMutationGuard.assertMutable(purchaseOrder, action);
        }
        assertNoActivePrepayment(purchaseOrder, action);
    }

    private void assertNoActivePrepayment(PurchaseOrder purchaseOrder, String action) {
        if (purchasePrepaymentReferenceGuard != null) {
            List<Long> sourceItemIds = purchaseOrder.getItems().stream()
                    .map(PurchaseOrderItem::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();
            purchasePrepaymentReferenceGuard.assertNoActivePrepayment(
                    purchaseOrder.getId(),
                    sourceItemIds,
                    action
            );
        }
    }
}
