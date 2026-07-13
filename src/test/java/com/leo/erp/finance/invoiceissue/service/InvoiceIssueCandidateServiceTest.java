package com.leo.erp.finance.invoiceissue.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.invoiceissue.repository.InvoiceIssueRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceIssueCandidateServiceTest {

    @Test
    void shouldReturnRemainingSalesOrderSnapshotExcludingCurrentIssue() {
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        InvoiceIssueRepository invoiceIssueRepository = mock(InvoiceIssueRepository.class);
        InvoiceIssueCandidateService service = new InvoiceIssueCandidateService(
                salesOrderRepository,
                invoiceIssueRepository,
                mock(ResourceRecordAccessGuard.class)
        );
        SalesOrder order = sourceOrder();
        InvoiceIssueRepository.SourceAllocationSummary summary =
                mock(InvoiceIssueRepository.SourceAllocationSummary.class);
        when(summary.getSourceSalesOrderItemId()).thenReturn(101L);
        when(summary.getTotalQuantity()).thenReturn(4L);
        when(summary.getTotalWeightTon()).thenReturn(new BigDecimal("8.00000000"));
        when(summary.getTotalAmount()).thenReturn(new BigDecimal("24000.00"));
        when(salesOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceIssueRepository.summarizeAllocatedBySourceSalesOrderItemIds(List.of(101L), 9001L))
                .thenReturn(List.of(summary));

        var result = service.sourceCandidates(
                PageQuery.of(0, 15, null, null),
                PageFilter.of(null, "客户A", "项目A", 88L, null, null, null)
                        .withIdentity(501L, 601L, null, null, 9001L)
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).singleElement().satisfies(candidate -> {
            assertThat(candidate.id()).isEqualTo(1L);
            assertThat(candidate.customerId()).isEqualTo(501L);
            assertThat(candidate.items()).singleElement().satisfies(item -> {
                assertThat(item.id()).isEqualTo(101L);
                assertThat(item.quantity()).isEqualTo(6);
                assertThat(item.weightTon()).isEqualByComparingTo("12.00000000");
                assertThat(item.amount()).isEqualByComparingTo("36000.00");
            });
        });
        verify(invoiceIssueRepository)
                .summarizeAllocatedBySourceSalesOrderItemIds(List.of(101L), 9001L);
    }

    private SalesOrder sourceOrder() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-001");
        order.setCustomerId(501L);
        order.setCustomerCode("CUS-001");
        order.setCustomerName("客户A");
        order.setProjectId(601L);
        order.setProjectName("项目A");
        order.setSettlementCompanyId(88L);
        order.setSettlementCompanyName("结算主体A");
        order.setDeliveryDate(LocalDate.of(2026, 7, 13));
        order.setStatus(StatusConstants.AUDITED);
        SalesOrderItem item = new SalesOrderItem();
        item.setId(101L);
        item.setSalesOrder(order);
        item.setLineNo(1);
        item.setQuantity(10);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("2.00000000"));
        item.setWeightTon(new BigDecimal("20.00000000"));
        item.setUnitPrice(new BigDecimal("3000.00"));
        item.setAmount(new BigDecimal("60000.00"));
        order.getItems().add(item);
        return order;
    }
}
