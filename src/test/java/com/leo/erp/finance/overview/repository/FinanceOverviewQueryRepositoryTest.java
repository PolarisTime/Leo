package com.leo.erp.finance.overview.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.finance.overview.web.dto.FinanceBalanceResponse;
import com.leo.erp.finance.overview.web.dto.FinanceOverviewSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FinanceOverviewQueryRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void overview_shouldUseDefaultSortWhenSortByIsMissing() {
        doReturn(emptySummary()).when(jdbcTemplate).queryForObject(
                anyString(),
                any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<FinanceOverviewSummaryResponse>>any());
        doReturn(1L).when(jdbcTemplate).queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Number.class));
        doReturn(List.of()).when(jdbcTemplate).query(
                anyString(),
                any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<FinanceBalanceResponse>>any());
        FinanceOverviewQueryRepository repository = new FinanceOverviewQueryRepository(jdbcTemplate);
        FinanceOverviewFilter filter = new FinanceOverviewFilter(
                332284010484989952L,
                LocalDate.of(2026, 8, 24),
                "RECEIVABLE",
                "客户",
                null,
                false
        );

        repository.overview(filter, new PageQuery(0, 30, null, null));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<FinanceBalanceResponse>>any());
        assertThat(sqlCaptor.getValue())
                .contains("ORDER BY outstanding_amount DESC, counterparty_name ASC, counterparty_id ASC");
    }

    private FinanceOverviewSummaryResponse emptySummary() {
        return new FinanceOverviewSummaryResponse(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
