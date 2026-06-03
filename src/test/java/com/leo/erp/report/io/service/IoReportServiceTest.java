package com.leo.erp.report.io.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.report.io.repository.IoReportQueryRepository;
import com.leo.erp.report.io.web.dto.IoReportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IoReportServiceTest {

    private final IoReportQueryRepository repository = mock(IoReportQueryRepository.class);
    private final IoReportService service = new IoReportService(repository);

    @Test
    void shouldRejectUnknownBusinessTypeFilter() {
        assertThatThrownBy(() -> service.page(new PageQuery(0, 20, null, null), null, "未知类型", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("businessType 不合法");
    }

    @Test
    void shouldRejectInvertedDateRange() {
        assertThatThrownBy(() -> service.page(
                new PageQuery(0, 20, null, null),
                null,
                null,
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 4, 1)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("startDate 不能晚于 endDate");
    }

    @Test
    void shouldNormalizeKeywordToLowerCase() {
        PageQuery query = new PageQuery(0, 20, null, null);
        Page<IoReportResponse> expected = new PageImpl<>(List.of());
        when(repository.page(any(), eq("abc"), isNull(), isNull(), isNull())).thenReturn(expected);

        Page<IoReportResponse> result = service.page(query, " ABC ", null, null, null);

        assertThat(result).isEqualTo(expected);
        verify(repository).page(query, "abc", null, null, null);
    }

    @Test
    void shouldNormalizeNullAndBlankKeywordToNull() {
        PageQuery query = new PageQuery(0, 20, null, null);
        Page<IoReportResponse> expected = new PageImpl<>(List.of());
        when(repository.page(any(), isNull(), isNull(), isNull(), isNull())).thenReturn(expected);

        service.page(query, "   ", null, null, null);

        verify(repository).page(query, null, null, null, null);
    }

    @Test
    void shouldPassAllowedBusinessTypeAfterTrimming() {
        PageQuery query = new PageQuery(0, 20, null, null);
        Page<IoReportResponse> expected = new PageImpl<>(List.of());
        when(repository.page(any(), isNull(), eq("采购入库"), isNull(), isNull())).thenReturn(expected);

        Page<IoReportResponse> result = service.page(query, null, " 采购入库 ", null, null);

        assertThat(result).isEqualTo(expected);
        verify(repository).page(query, null, "采购入库", null, null);
    }

    @Test
    void shouldNormalizeBlankBusinessTypeToNull() {
        PageQuery query = new PageQuery(0, 20, null, null);
        Page<IoReportResponse> expected = new PageImpl<>(List.of());
        when(repository.page(any(), isNull(), isNull(), isNull(), isNull())).thenReturn(expected);

        service.page(query, null, "   ", null, null);

        verify(repository).page(query, null, null, null, null);
    }

    @Test
    void shouldAcceptValidDateRange() {
        PageQuery query = new PageQuery(0, 20, null, null);
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        Page<IoReportResponse> expected = new PageImpl<>(List.of());
        when(repository.page(any(), isNull(), isNull(), eq(start), eq(end))).thenReturn(expected);

        Page<IoReportResponse> result = service.page(query, null, null, start, end);

        assertThat(result).isEqualTo(expected);
        verify(repository).page(query, null, null, start, end);
    }

    @Test
    void shouldAcceptEqualStartDateAndEndDate() {
        PageQuery query = new PageQuery(0, 20, null, null);
        LocalDate date = LocalDate.of(2026, 6, 1);
        Page<IoReportResponse> expected = new PageImpl<>(List.of());
        when(repository.page(any(), isNull(), isNull(), eq(date), eq(date))).thenReturn(expected);

        service.page(query, null, null, date, date);

        verify(repository).page(query, null, null, date, date);
    }
}
