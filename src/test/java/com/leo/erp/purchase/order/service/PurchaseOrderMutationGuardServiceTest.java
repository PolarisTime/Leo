package com.leo.erp.purchase.order.service;

import com.leo.erp.purchase.api.PurchaseOrderPrepaymentReferenceGuard;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderMutationGuardServiceTest {

    @Mock
    private PurchaseOrderDownstreamMutationGuard downstreamMutationGuard;

    @Mock
    private PurchaseOrderPrepaymentReferenceGuard purchasePrepaymentReferenceGuard;

    @Test
    void assertUpdateAllowed_newOrder_shouldSkipGuards() {
        PurchaseOrderMutationGuardService service = new PurchaseOrderMutationGuardService(
                downstreamMutationGuard, purchasePrepaymentReferenceGuard);
        PurchaseOrder order = new PurchaseOrder();
        order.getItems().add(item(1L));

        service.assertUpdateAllowed(order, List.of());

        verifyNoInteractions(downstreamMutationGuard, purchasePrepaymentReferenceGuard);
    }

    @Test
    void assertUpdateAllowed_existingOrder_shouldInvokeBothGuardsWithSortedDistinctItemIds() {
        PurchaseOrderMutationGuardService service = new PurchaseOrderMutationGuardService(
                downstreamMutationGuard, purchasePrepaymentReferenceGuard);
        PurchaseOrder order = new PurchaseOrder();
        order.setId(9L);
        order.getItems().add(item(3L));
        order.getItems().add(item(1L));
        order.getItems().add(item(3L));
        List<PurchaseOrderItemRequest> requestedItems = List.of();

        service.assertUpdateAllowed(order, requestedItems);

        verify(downstreamMutationGuard).assertSourceLineMutationAllowed(order, requestedItems, "修改");
        verify(purchasePrepaymentReferenceGuard).assertNoActivePrepayment(
                eq(order.getId()), eq(List.of(1L, 3L)), eq("修改"));
    }

    @Test
    void assertUpdateAllowed_existingOrderWithoutPersistedItemIds_shouldOnlyCheckPrepayment() {
        PurchaseOrderMutationGuardService service = new PurchaseOrderMutationGuardService(
                downstreamMutationGuard, purchasePrepaymentReferenceGuard);
        PurchaseOrder order = new PurchaseOrder();
        order.setId(9L);
        order.getItems().add(item(null));

        service.assertUpdateAllowed(order, List.of());

        verifyNoInteractions(downstreamMutationGuard);
        verify(purchasePrepaymentReferenceGuard).assertNoActivePrepayment(9L, List.of(), "修改");
    }

    @Test
    void assertUpdateAllowed_missingDownstreamGuard_shouldOnlyCheckPrepayment() {
        PurchaseOrderMutationGuardService service = new PurchaseOrderMutationGuardService(
                null, purchasePrepaymentReferenceGuard);
        PurchaseOrder order = new PurchaseOrder();
        order.setId(9L);
        order.getItems().add(item(2L));

        service.assertUpdateAllowed(order, List.of());

        verify(purchasePrepaymentReferenceGuard).assertNoActivePrepayment(9L, List.of(2L), "修改");
    }

    @Test
    void assertUpdateAllowed_missingPrepaymentGuard_shouldOnlyCheckDownstream() {
        PurchaseOrderMutationGuardService service = new PurchaseOrderMutationGuardService(
                downstreamMutationGuard, null);
        PurchaseOrder order = new PurchaseOrder();
        order.setId(9L);
        order.getItems().add(item(2L));

        service.assertUpdateAllowed(order, List.of());

        verify(downstreamMutationGuard).assertSourceLineMutationAllowed(order, List.of(), "修改");
    }

    @Test
    void assertMutable_shouldInvokeBothGuardsWithAction() {
        PurchaseOrderMutationGuardService service = new PurchaseOrderMutationGuardService(
                downstreamMutationGuard, purchasePrepaymentReferenceGuard);
        PurchaseOrder order = new PurchaseOrder();
        order.setId(9L);
        order.getItems().add(item(4L));

        service.assertMutable(order, "删除");

        verify(downstreamMutationGuard).assertMutable(order, "删除");
        verify(purchasePrepaymentReferenceGuard).assertNoActivePrepayment(9L, List.of(4L), "删除");
    }

    private PurchaseOrderItem item(Long id) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        return item;
    }
}
