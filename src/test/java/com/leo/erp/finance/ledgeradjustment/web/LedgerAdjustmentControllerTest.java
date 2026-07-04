package com.leo.erp.finance.ledgeradjustment.web;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.finance.ledgeradjustment.service.LedgerAdjustmentService;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentRequest;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LedgerAdjustmentControllerTest {

    @Test
    void shouldSearchWithNormalizedKeywordAndCappedLimit() {
        LedgerAdjustmentService service = mock(LedgerAdjustmentService.class);
        LedgerAdjustmentResponse response = response(1L);
        when(service.search("", 500)).thenReturn(List.of(response));
        LedgerAdjustmentController controller = new LedgerAdjustmentController(service);

        assertThat(controller.search(null, 800).data()).containsExactly(response);

        verify(service).search("", 500);
    }

    @Test
    void shouldSearchWithProvidedKeywordAndUncappedLimit() {
        LedgerAdjustmentService service = mock(LedgerAdjustmentService.class);
        LedgerAdjustmentResponse response = response(2L);
        when(service.search(" LA ", 100)).thenReturn(List.of(response));
        LedgerAdjustmentController controller = new LedgerAdjustmentController(service);

        assertThat(controller.search(" LA ", 100).data()).containsExactly(response);

        verify(service).search(" LA ", 100);
    }

    @Test
    void shouldPageWithFilters() {
        LedgerAdjustmentService service = mock(LedgerAdjustmentService.class);
        PageQuery query = PageQuery.of(1, 20, "adjustmentDate", "desc");
        LedgerAdjustmentResponse response = response(2L);
        when(service.page(
                query,
                PageFilter.of("LA", StatusConstants.DRAFT, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)),
                "应收",
                "客户"
        )).thenReturn(new PageImpl<>(List.of(response), PageRequest.of(1, 20), 30));
        LedgerAdjustmentController controller = new LedgerAdjustmentController(service);

        var result = controller.page(
                query,
                "LA",
                "应收",
                "客户",
                StatusConstants.DRAFT,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30)
        ).data();

        assertThat(result.content()).containsExactly(response);
        assertThat(result.totalElements()).isEqualTo(21);
        assertThat(result.currentPage()).isEqualTo(1);
    }

    @Test
    void shouldDelegateDetailCreateUpdateStatusAndDelete() {
        LedgerAdjustmentService service = mock(LedgerAdjustmentService.class);
        LedgerAdjustmentRequest request = request();
        LedgerAdjustmentResponse response = response(9L);
        when(service.detail(9L)).thenReturn(response);
        when(service.create(request)).thenReturn(response);
        when(service.update(9L, request)).thenReturn(response);
        when(service.updateStatus(9L, StatusConstants.AUDITED)).thenReturn(response);
        LedgerAdjustmentController controller = new LedgerAdjustmentController(service);

        assertThat(controller.detail(9L).data()).isEqualTo(response);
        assertThat(controller.create(request).message()).isEqualTo("创建成功");
        assertThat(controller.update(9L, request).message()).isEqualTo("更新成功");
        assertThat(controller.updateStatus(9L, new StatusUpdateRequest(StatusConstants.AUDITED)).message())
                .isEqualTo("状态更新成功");
        assertThat(controller.delete(9L).message()).isEqualTo("删除成功");

        verify(service).delete(9L);
    }

    private LedgerAdjustmentRequest request() {
        return new LedgerAdjustmentRequest(
                "LA-001",
                "应收",
                "客户",
                "C-001",
                "客户A",
                null,
                null,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("10.00"),
                "其他调整",
                "增加余额",
                StatusConstants.DRAFT,
                "财务A",
                null
        );
    }

    private LedgerAdjustmentResponse response(Long id) {
        return new LedgerAdjustmentResponse(
                id,
                "LA-001",
                "应收",
                "客户",
                "C-001",
                "客户A",
                null,
                null,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("10.00"),
                "其他调整",
                "增加余额",
                StatusConstants.DRAFT,
                "财务A",
                null
        );
    }
}
