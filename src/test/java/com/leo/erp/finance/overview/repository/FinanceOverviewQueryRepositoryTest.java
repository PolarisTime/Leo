package com.leo.erp.finance.overview.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.finance.overview.web.dto.FinanceBalanceResponse;
import com.leo.erp.finance.overview.web.dto.FinanceOverviewSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 财务概览查询仓储测试。
 *
 * <p>重点覆盖「一次执行同时取回分页明细、总数与汇总」这一优化的行为契约，
 * 以及两种必须回退到独立统计查询的边界：过滤后无数据、请求页码越界。
 * 后者尤其重要——窗口汇总列只存在于返回行上，越界页若无回退会丢失汇总与总数。</p>
 */
@ExtendWith(MockitoExtension.class)
class FinanceOverviewQueryRepositoryTest {

    private static final long COMPANY_ID = 332284010484989952L;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private FinanceOverviewQueryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new FinanceOverviewQueryRepository(jdbcTemplate);
    }

    private FinanceOverviewFilter filter() {
        return new FinanceOverviewFilter(
                COMPANY_ID,
                LocalDate.of(2026, 8, 24),
                "RECEIVABLE",
                "客户",
                null,
                false
        );
    }

    /** 让 jdbcTemplate.query(..., ResultSetExtractor) 真正调用提取器，从而连提取逻辑一并测到。 */
    private void stubPageQueryWithResultSet(ResultSet resultSet) {
        if (resultSet == null) {
            // 结果集为空：提取器返回 null，仓储应走统计回退路径
            doReturn(null).when(jdbcTemplate).query(
                    anyString(),
                    any(MapSqlParameterSource.class),
                    any(ResultSetExtractor.class));
            return;
        }
        doAnswer(invocation -> ((ResultSetExtractor<?>) invocation.getArgument(2)).extractData(resultSet))
                .when(jdbcTemplate).query(
                        anyString(),
                        any(MapSqlParameterSource.class),
                        any(ResultSetExtractor.class));
    }

    private ResultSet pageResultSet(long total, BigDecimal receivableAmount) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        // 窗口汇总列（整段结果集共用同一组值）
        when(resultSet.getLong("query_total_count")).thenReturn(total);
        when(resultSet.getBigDecimal("query_receivable_amount")).thenReturn(receivableAmount);
        when(resultSet.getBigDecimal("query_received_amount")).thenReturn(new BigDecimal("40.00"));
        when(resultSet.getBigDecimal("query_unreceived_amount")).thenReturn(new BigDecimal("60.00"));
        when(resultSet.getBigDecimal("query_advance_receipt_amount")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("query_payable_amount")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("query_paid_amount")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("query_unpaid_amount")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("query_advance_payment_amount")).thenReturn(BigDecimal.ZERO);
        // BALANCE_ROW_MAPPER 读取的全部明细列（严格 stub 模式下必须逐个覆盖）
        when(resultSet.getString("direction")).thenReturn("RECEIVABLE");
        when(resultSet.getString("counterparty_type")).thenReturn("客户");
        when(resultSet.getObject("counterparty_id", Long.class)).thenReturn(1L);
        when(resultSet.getString("counterparty_code")).thenReturn("C001");
        when(resultSet.getString("counterparty_name")).thenReturn("测试客户");
        when(resultSet.getObject("settlement_company_id", Long.class)).thenReturn(COMPANY_ID);
        when(resultSet.getString("settlement_company_name")).thenReturn("测试主体");
        when(resultSet.getBigDecimal("recognized_amount")).thenReturn(receivableAmount);
        when(resultSet.getBigDecimal("settled_amount")).thenReturn(new BigDecimal("40.00"));
        when(resultSet.getBigDecimal("outstanding_amount")).thenReturn(new BigDecimal("60.00"));
        when(resultSet.getBigDecimal("advance_amount")).thenReturn(BigDecimal.ZERO);
        return resultSet;
    }

    private ArgumentCaptor<String> capturePageSql() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                any(MapSqlParameterSource.class),
                any(ResultSetExtractor.class));
        return sqlCaptor;
    }

    @Test
    void overview_shouldUseDefaultSortWhenSortByIsMissing() throws Exception {
        stubPageQueryWithResultSet(pageResultSet(1L, new BigDecimal("100.00")));

        repository.overview(filter(), new PageQuery(0, 30, null, null));

        assertThat(capturePageSql().getValue())
                .contains("ORDER BY outstanding_amount DESC, counterparty_name ASC, counterparty_id ASC");
    }

    /** 核心优化：常规路径下汇总与总数由同一次查询的窗口列提供，不再额外执行统计查询。 */
    @Test
    void overview_shouldReturnSummaryAndTotalFromSingleQuery() throws Exception {
        stubPageQueryWithResultSet(pageResultSet(7L, new BigDecimal("100.00")));

        // pageSize=1 且总计 7 行：offset+pageSize 不超过 total，PageImpl 不会修正总数，
        // 因此可确证总数确实来自查询结果的窗口列而非回退统计查询。
        FinanceOverviewQueryRepository.OverviewResult result =
                repository.overview(filter(), new PageQuery(0, 1, null, null));

        assertThat(result.balances().getTotalElements()).isEqualTo(7L);
        assertThat(result.balances().getContent()).hasSize(1);
        assertThat(result.balances().getContent().get(0).counterpartyName()).isEqualTo("测试客户");
        assertThat(result.summary().receivableAmount()).isEqualByComparingTo("100.00");
        assertThat(result.summary().receivedAmount()).isEqualByComparingTo("40.00");
        assertThat(result.summary().unreceivedAmount()).isEqualByComparingTo("60.00");

        // 单次执行即拿到全部信息，不得再回退查询
        verify(jdbcTemplate, never()).queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Number.class));
    }

    @Test
    void overview_shouldReturnEmptyPageWhenNoDataMatches() {
        // 提取器返回 null 表示结果集为空
        stubPageQueryWithResultSet(null);
        doReturn(zeroSummary()).when(jdbcTemplate).queryForObject(
                anyString(),
                any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<FinanceOverviewSummaryResponse>>any());
        doReturn(0L).when(jdbcTemplate).queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Number.class));

        FinanceOverviewQueryRepository.OverviewResult result =
                repository.overview(filter(), new PageQuery(0, 30, null, null));

        assertThat(result.balances().getTotalElements()).isZero();
        assertThat(result.balances().getContent()).isEmpty();
        assertThat(result.summary().receivableAmount()).isEqualByComparingTo("0");
    }

    /**
     * 页码越界：窗口汇总列随空结果集一起消失，必须回退到独立统计查询，
     * 否则会丢失汇总与总数——这正是本次改动最容易引入的回归。
     */
    @Test
    void overview_shouldKeepSummaryAndTotalWhenPageIsBeyondLastPage() {
        stubPageQueryWithResultSet(null);
        doReturn(summaryOf(new BigDecimal("500.00"))).when(jdbcTemplate).queryForObject(
                anyString(),
                any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<FinanceOverviewSummaryResponse>>any());
        doReturn(5L).when(jdbcTemplate).queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Number.class));

        FinanceOverviewQueryRepository.OverviewResult result =
                repository.overview(filter(), new PageQuery(99, 30, null, null));

        assertThat(result.balances().getContent()).isEmpty();
        assertThat(result.balances().getTotalElements()).isEqualTo(5L);
        assertThat(result.summary().receivableAmount()).isEqualByComparingTo("500.00");
    }

    private FinanceOverviewSummaryResponse summaryOf(BigDecimal receivable) {
        return new FinanceOverviewSummaryResponse(
                receivable,
                BigDecimal.ZERO,
                receivable,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    private FinanceOverviewSummaryResponse zeroSummary() {
        return summaryOf(BigDecimal.ZERO);
    }
}
