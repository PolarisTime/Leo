package com.leo.erp.statement.customer.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerStatementSourceServiceTest {

    @Test
    void shouldReturnSalesOrderCandidates() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrder sourceOrder = sourceOrder();
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());
        when(salesOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sourceOrder)));

        CustomerStatementSourceService service = new CustomerStatementSourceService(
                repository,
                salesOrderRepository,
                mock(SalesOrderItemQueryService.class),
                null
        );

        List<CustomerStatementCandidateResponse> candidates = service
                .candidatePage(PageQuery.of(0, 20, null, null), PageFilter.of("SO", "完成销售", null, null))
                .getContent();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).orderNo()).isEqualTo("SO-001");
        assertThat(candidates.get(0).customerName()).isEqualTo("客户甲");
        assertThat(candidates.get(0).projectName()).isEqualTo("项目A");
        assertThat(candidates.get(0).status()).isEqualTo(StatusConstants.SALES_COMPLETED);
    }

    private SalesOrder sourceOrder() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-001");
        order.setCustomerName("客户甲");
        order.setProjectName("项目A");
        order.setDeliveryDate(LocalDate.of(2026, 5, 6));
        order.setSalesName("张三");
        order.setTotalWeight(new BigDecimal("1.000"));
        order.setTotalAmount(new BigDecimal("1000.00"));
        order.setStatus(StatusConstants.SALES_COMPLETED);
        return order;
    }
}
