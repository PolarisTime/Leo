package com.leo.erp.sales.order.service;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SalesOrderSaveService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderSaveServiceTest {

    @Mock
    private SalesOrderRepository repository;

    @Mock
    private SalesOrderCompletionSyncService completionSyncService;

    @Mock
    private SalesOrderCompletionPolicy completionPolicy;

    @InjectMocks
    private SalesOrderSaveService service;

    private SalesOrder order() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        return order;
    }

    @Test
    void save_shouldFlushAndSyncWhenPolicyApplies() {
        SalesOrder order = order();
        when(repository.saveAndFlush(order)).thenReturn(order);
        when(completionPolicy.shouldSyncAfterSave(order)).thenReturn(true);

        assertThat(service.save(order)).isSameAs(order);
        verify(completionSyncService).syncBySalesOrderId(1L);
    }

    @Test
    void save_shouldNotSyncWhenPolicyRejects() {
        SalesOrder order = order();
        when(repository.saveAndFlush(order)).thenReturn(order);
        when(completionPolicy.shouldSyncAfterSave(order)).thenReturn(false);

        service.save(order);

        verifyNoInteractions(completionSyncService);
    }

    @Test
    void saveStatus_shouldSaveAndSyncWhenPolicyApplies() {
        SalesOrder order = order();
        when(repository.save(order)).thenReturn(order);
        when(completionPolicy.shouldSyncAfterSave(order)).thenReturn(true);

        assertThat(service.saveStatus(order)).isSameAs(order);
        verify(completionSyncService).syncBySalesOrderId(1L);
    }

    @Test
    void saveAuditedPricingUpdate_shouldSaveWithoutSync() {
        SalesOrder order = order();
        when(repository.save(order)).thenReturn(order);

        assertThat(service.saveAuditedPricingUpdate(order)).isSameAs(order);
        verifyNoInteractions(completionSyncService, completionPolicy);
    }

    @Test
    void save_shouldNotSyncWhenCompletionSyncServiceNull() {
        SalesOrderSaveService svc = new SalesOrderSaveService(repository, null, completionPolicy);
        SalesOrder order = order();
        when(repository.saveAndFlush(order)).thenReturn(order);

        assertThat(svc.save(order)).isSameAs(order);
        verifyNoInteractions(completionSyncService);
    }
}
