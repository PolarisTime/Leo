package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemRepository;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.mockito.Mockito.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseOrderItemQueryServiceTest {

    @Test
    void findActiveByIdInShouldReturnEmptyForNullOrEmptyIds() {
        PurchaseOrderItemRepository repository = mock(PurchaseOrderItemRepository.class);
        PurchaseOrderItemQueryService service = new PurchaseOrderItemQueryService(
                repository, mock(ResourceRecordAccessGuard.class)
        );

        assertThat(service.findActiveByIdIn(null)).isEmpty();
        assertThat(service.findActiveByIdIn(List.of())).isEmpty();
    }

    @Test
    void findActiveByIdInShouldReturnItemsAndCheckAccess() {
        PurchaseOrderItemRepository repository = mock(PurchaseOrderItemRepository.class);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(1L);
        when(repository.findActiveByIdIn(List.of(1L))).thenReturn(List.of(item));

        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);

        PurchaseOrderItemQueryService service = new PurchaseOrderItemQueryService(repository, accessGuard);

        List<PurchaseOrderItem> result = service.findActiveByIdIn(List.of(1L));

        assertThat(result).containsExactly(item);
    }

    @Test
    void requireActiveByIdShouldThrowWhenNotFound() {
        PurchaseOrderItemRepository repository = mock(PurchaseOrderItemRepository.class);
        when(repository.findActiveByIdIn(List.of(99L))).thenReturn(List.of());

        PurchaseOrderItemQueryService service = new PurchaseOrderItemQueryService(
                repository, mock(ResourceRecordAccessGuard.class)
        );

        assertThatThrownBy(() -> service.requireActiveById(99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void requireActiveByIdShouldReturnItem() {
        PurchaseOrderItemRepository repository = mock(PurchaseOrderItemRepository.class);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(1L);
        when(repository.findActiveByIdIn(List.of(1L))).thenReturn(List.of(item));

        PurchaseOrderItemQueryService service = new PurchaseOrderItemQueryService(
                repository, mock(ResourceRecordAccessGuard.class)
        );

        assertThat(service.requireActiveById(1L)).isEqualTo(item);
    }

    @Test
    void findActiveByIdInShouldFilterOutAccessDeniedItems() {
        PurchaseOrderItemRepository repository = mock(PurchaseOrderItemRepository.class);
        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);

        PurchaseOrderItem item1 = new PurchaseOrderItem();
        item1.setId(1L);
        com.leo.erp.purchase.order.domain.entity.PurchaseOrder order1 = new com.leo.erp.purchase.order.domain.entity.PurchaseOrder();
        order1.setId(100L);
        item1.setPurchaseOrder(order1);

        PurchaseOrderItem item2 = new PurchaseOrderItem();
        item2.setId(2L);
        com.leo.erp.purchase.order.domain.entity.PurchaseOrder order2 = new com.leo.erp.purchase.order.domain.entity.PurchaseOrder();
        order2.setId(200L);
        item2.setPurchaseOrder(order2);

        when(repository.findActiveByIdIn(List.of(1L, 2L))).thenReturn(List.of(item1, item2));

        PurchaseOrderItemQueryService service = new PurchaseOrderItemQueryService(repository, accessGuard);

        List<PurchaseOrderItem> result = service.findActiveByIdIn(List.of(1L, 2L));

        assertThat(result).hasSize(2);
        verify(accessGuard).assertCurrentUserCanAccess("purchase-order", "read", order1);
        verify(accessGuard).assertCurrentUserCanAccess("purchase-order", "read", order2);
    }

    @Test
    void findActiveByIdInShouldSkipAccessCheckWhenParentIsNull() {
        PurchaseOrderItemRepository repository = mock(PurchaseOrderItemRepository.class);
        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(1L);
        item.setPurchaseOrder(null);

        when(repository.findActiveByIdIn(List.of(1L))).thenReturn(List.of(item));

        PurchaseOrderItemQueryService service = new PurchaseOrderItemQueryService(repository, accessGuard);

        List<PurchaseOrderItem> result = service.findActiveByIdIn(List.of(1L));

        assertThat(result).singleElement().isEqualTo(item);
    }
}
