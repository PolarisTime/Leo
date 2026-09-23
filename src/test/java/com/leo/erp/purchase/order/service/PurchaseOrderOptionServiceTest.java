package com.leo.erp.purchase.order.service;

import com.leo.erp.purchase.api.PurchaseOrderOptionQuery;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderOptionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderOptionServiceTest {

    @Mock
    private PurchaseOrderRepository repository;

    @Test
    void listOptions_mapsOrderFields() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(88L);
        order.setOrderNo("88");
        order.setSupplierName("沙钢");
        order.setTotalWeight(new BigDecimal("40.5"));
        order.setStatus("正常");
        order.setOrderDate(LocalDateTime.of(2026, 9, 9, 10, 0));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));

        List<PurchaseOrderOptionResponse> options = new PurchaseOrderOptionService(repository).listOptions(null, null);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).orderNo()).isEqualTo("88");
        assertThat(options.get(0).supplierName()).isEqualTo("沙钢");
        assertThat(options.get(0).totalWeight()).isEqualByComparingTo("40.5");
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(repository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void listActiveByIds_mapsByIdAndDeduplicates() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(88L);
        order.setOrderNo("PO-88");
        order.setSupplierName("沙钢");
        order.setTotalWeight(new BigDecimal("40.5"));
        order.setStatus("正常");
        when(repository.findByIdInAndDeletedFlagFalse(any())).thenReturn(List.of(order));

        List<PurchaseOrderOptionQuery.PurchaseOrderOptionSnapshot> snapshots =
                new PurchaseOrderOptionService(repository).listActiveByIds(List.of(88L, 88L));

        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).orderNo()).isEqualTo("PO-88");
        assertThat(snapshots.get(0).totalWeight()).isEqualByComparingTo("40.5");
    }

    @Test
    void listActiveByIds_emptyOrNullIdsReturnsEmptyWithoutQuerying() {
        PurchaseOrderOptionService service = new PurchaseOrderOptionService(repository);

        assertThat(service.listActiveByIds(List.of())).isEmpty();
        assertThat(service.listActiveByIds(null)).isEmpty();
        verify(repository, never()).findByIdInAndDeletedFlagFalse(any());
    }
}
