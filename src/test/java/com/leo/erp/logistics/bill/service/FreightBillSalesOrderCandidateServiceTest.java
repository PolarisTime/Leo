package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.logistics.bill.repository.FreightBillSalesOrderCandidateQueryRepository;
import com.leo.erp.sales.api.SalesOrderLogisticsSourceQuery;
import com.leo.erp.sales.api.SalesOrderSourceSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FreightBillSalesOrderCandidateService 极端情况测试。
 * <p>
 * 覆盖：空候选、候选 ID 与快照的顺序映射、快照缺失时的过滤与 totalElements 保留。
 */
@ExtendWith(MockitoExtension.class)
class FreightBillSalesOrderCandidateServiceTest {

    @Mock
    private SalesOrderLogisticsSourceQuery salesOrderSourceQuery;

    @Mock
    private FreightBillSalesOrderCandidateQueryRepository candidateQueryRepository;

    @InjectMocks
    private FreightBillSalesOrderCandidateService service;

    private SalesOrderSourceSnapshot snapshot(Long id, String orderNo) {
        return new SalesOrderSourceSnapshot(
                id, orderNo, null, null, "CUST001", 10L, "客户A", 20L, "项目A", 30L, "结算公司A",
                LocalDate.of(2026, 8, 1), "销售员A", new BigDecimal("100"), new BigDecimal("5000"),
                "SALES_COMPLETED", false, null, List.of());
    }

    @Test
    void page_shouldReturnEmptyWhenNoCandidates() {
        when(candidateQueryRepository.pageIds(any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        PageResponse<SalesOrderSourceSnapshot> result =
                service.page(mock(PageQuery.class), mock(PageFilter.class));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    void page_shouldMapCandidateIdsPreservingPageOrder() {
        when(candidateQueryRepository.pageIds(any(), any()))
                .thenReturn(new PageImpl<>(List.of(1L, 2L), PageRequest.of(0, 10), 2));
        // 快照顺序与候选 ID 顺序不一致
        when(salesOrderSourceQuery.findByOrderIds(any()))
                .thenReturn(List.of(snapshot(2L, "SO002"), snapshot(1L, "SO001")));

        PageResponse<SalesOrderSourceSnapshot> result =
                service.page(mock(PageQuery.class), mock(PageFilter.class));

        assertThat(result.content()).extracting(SalesOrderSourceSnapshot::id).containsExactly(1L, 2L);
        assertThat(result.totalElements()).isEqualTo(2L);
    }

    @Test
    void page_shouldDropMissingSnapshotsButKeepTotalElements() {
        when(candidateQueryRepository.pageIds(any(), any()))
                .thenReturn(new PageImpl<>(List.of(1L, 2L, 3L), PageRequest.of(0, 10), 3));
        // 2L 的快照缺失（如已删除）
        when(salesOrderSourceQuery.findByOrderIds(any()))
                .thenReturn(List.of(snapshot(1L, "SO001"), snapshot(3L, "SO003")));

        PageResponse<SalesOrderSourceSnapshot> result =
                service.page(mock(PageQuery.class), mock(PageFilter.class));

        assertThat(result.content()).extracting(SalesOrderSourceSnapshot::id).containsExactly(1L, 3L);
        assertThat(result.totalElements()).isEqualTo(3L); // 总数保留候选查询的总量
    }
}
