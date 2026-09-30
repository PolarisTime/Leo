package com.leo.erp.finance.cashledger.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.finance.cashledger.repository.CashLedgerQueryRepository.LedgerPageRow;
import com.leo.erp.finance.cashledger.repository.CashLedgerQueryRepository.LedgerSummaryAndTotal;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerLineResponse;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerPageResponse;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * 覆盖「资金流水」把 summary/total 合并进单次分页查询后的行为契约。
 *
 * <p>关键断言：分页请求只执行一次 ledger CTE；空页（期间过滤为空或 offset 越界）
 * 必须与原实现返回完全一致的 total 与 summary —— 尤其是期初余额来自「不含期间」的基础筛选，
 * 期间过滤为空时它仍然可能非 0，不能用全 0 summary 代替。</p>
 */
@ExtendWith(MockitoExtension.class)
class CashLedgerQueryRepositoryTest {

    private static final long COMPANY_ID = 332601703884922880L;
    private static final LocalDate START_DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 9, 30);

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void page_shouldUseSingleWindowQueryWhenPageHasRows() {
        stubPageRows(new LedgerPageRow(line(1L), 6L, BigDecimal.ZERO, BigDecimal.valueOf(3000), BigDecimal.valueOf(1500)));

        // size 必须与桩行数保持一致：Spring Data 的 PageImpl 在「content 非空且 offset + size > total」
        // 时会把 total 改写为 offset + content.size()（真实查询中该修正恒等，但不一致的桩会被改写）。
        CashLedgerPageResponse result = repository().page(filter(), new PageQuery(0, 1, null, null));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), pageRowMapper());
        // 只允许这一条 SQL：不再有独立的 summary 查询与 COUNT 查询
        verifyNoMoreInteractions(jdbcTemplate);

        assertThat(sqlCaptor.getValue())
                .contains("base_ledger AS MATERIALIZED")
                .contains("COUNT(1) OVER () AS __total")
                .contains("OVER (), 0) AS period_income")
                .contains("OVER (), 0) AS period_expense")
                .contains("opening.opening_balance AS opening_balance")
                .contains("opening.opening_balance + SUM(filtered_ledger.balance_change) OVER (")
                .contains("ORDER BY filtered_ledger.business_date ASC")
                .contains("ORDER BY ledger_with_balance.business_date DESC")
                .contains("LIMIT :limit OFFSET :offset")
                .doesNotContain(":openingBalance")
                .doesNotContain("SELECT COUNT(1) FROM ledger");
        assertThat(result.page().totalElements()).isEqualTo(6L);
    }

    @Test
    void page_shouldBindLimitAndOffsetFromPageQuery() {
        stubPageRows(new LedgerPageRow(line(1L), 12L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        repository().page(filter(), new PageQuery(3, 5, null, "asc"));

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(anyString(), paramsCaptor.capture(), pageRowMapper());
        assertThat(paramsCaptor.getValue().getValue("limit")).isEqualTo(5);
        assertThat(paramsCaptor.getValue().getValue("offset")).isEqualTo(15L);
    }

    @Test
    void page_shouldAlwaysBindPeriodParametersSoOpeningWindowResolves() {
        stubPageRows(new LedgerPageRow(line(1L), 6L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        CashLedgerFilter filterWithoutPeriod = new CashLedgerFilter(COMPANY_ID, null, null, null, null, null, null);

        repository().page(filterWithoutPeriod, new PageQuery(0, 30, null, null));

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(anyString(), paramsCaptor.capture(), pageRowMapper());
        MapSqlParameterSource parameters = paramsCaptor.getValue();
        // 单查询里的期初余额窗口始终引用 :startDate，因此即使未传期间也必须存在该绑定
        assertThat(parameters.hasValue("startDate")).isTrue();
        assertThat(parameters.getValue("startDate")).isNull();
        assertThat(parameters.getSqlType("startDate")).isEqualTo(Types.DATE);
        assertThat(parameters.hasValue("endDate")).isTrue();
        assertThat(parameters.getValue("endDate")).isNull();
    }

    @Test
    void page_summaryShouldComeFromTheSameWindowResultAsThePage() {
        stubPageRows(new LedgerPageRow(
                line(1L), 4L, BigDecimal.valueOf(1500), BigDecimal.valueOf(3000), BigDecimal.valueOf(1500)));

        CashLedgerPageResponse result = repository().page(filter(), new PageQuery(0, 30, null, null));

        assertThat(result.summary()).isEqualTo(new CashLedgerSummaryResponse(
                BigDecimal.valueOf(1500),
                BigDecimal.valueOf(3000),
                BigDecimal.valueOf(1500),
                BigDecimal.valueOf(3000)
        ));
    }

    @Test
    void page_multiRowPagingShouldExposePageMetadataFromWindowTotal() {
        stubPageRows(
                new LedgerPageRow(line(1L), 7L, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE),
                new LedgerPageRow(line(2L), 7L, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE),
                new LedgerPageRow(line(3L), 7L, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE)
        );

        CashLedgerPageResponse result = repository().page(filter(), new PageQuery(1, 3, null, null));

        assertThat(result.page().content()).hasSize(3);
        assertThat(result.page().totalElements()).isEqualTo(7L);
        assertThat(result.page().totalPages()).isEqualTo(3);
        assertThat(result.page().currentPage()).isEqualTo(1);
        assertThat(result.page().pageSize()).isEqualTo(3);
        assertThat(result.page().hasMore()).isTrue();
    }

    @Test
    void page_singleRowShouldReportTotalOne() {
        stubPageRows(new LedgerPageRow(line(1L), 1L, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO));

        CashLedgerPageResponse result = repository().page(filter(), new PageQuery(0, 30, null, null));

        assertThat(result.page().content()).hasSize(1);
        assertThat(result.page().totalElements()).isEqualTo(1L);
        assertThat(result.page().totalPages()).isEqualTo(1);
        assertThat(result.page().hasMore()).isFalse();
    }

    @Test
    void page_offsetBeyondEndShouldKeepTotalAndSummaryFromFallback() {
        doReturn(List.of()).when(jdbcTemplate).query(
                anyString(), any(MapSqlParameterSource.class), pageRowMapper());
        doReturn(new LedgerSummaryAndTotal(
                summary(BigDecimal.ZERO, BigDecimal.valueOf(3000), BigDecimal.valueOf(1500)), 6L))
                .when(jdbcTemplate).queryForObject(
                        anyString(), any(MapSqlParameterSource.class), summaryMapper());

        CashLedgerPageResponse result = repository().page(filter(), new PageQuery(9, 10, null, null));

        assertThat(result.page().content()).isEmpty();
        assertThat(result.page().totalElements()).isEqualTo(6L);
        assertThat(result.page().totalPages()).isEqualTo(1);
        assertThat(result.summary().openingBalance()).isEqualByComparingTo("0");
        assertThat(result.summary().periodIncome()).isEqualByComparingTo("3000");
        assertThat(result.summary().closingBalance()).isEqualByComparingTo("1500");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), any(MapSqlParameterSource.class), summaryMapper());
        assertThat(sqlCaptor.getValue())
                .contains("COUNT(1) FILTER (WHERE business_date >= :startDate AND business_date <= :endDate) AS __total")
                .contains("COALESCE(SUM(ledger.balance_change) FILTER (WHERE ledger.business_date < :startDate), 0) AS opening_balance")
                .doesNotContain("LIMIT :limit");
    }

    @Test
    void page_periodEmptyButOpeningNonZeroShouldPreserveOpeningBalance() {
        // 对应线上场景：startDate 晚于全部流水 -> total=0、内容为空，但期初余额非 0
        doReturn(List.of()).when(jdbcTemplate).query(
                anyString(), any(MapSqlParameterSource.class), pageRowMapper());
        doReturn(new LedgerSummaryAndTotal(
                summary(BigDecimal.valueOf(1500), BigDecimal.ZERO, BigDecimal.ZERO), 0L))
                .when(jdbcTemplate).queryForObject(
                        anyString(), any(MapSqlParameterSource.class), summaryMapper());

        CashLedgerPageResponse result = repository().page(filter(), new PageQuery(0, 30, null, null));

        assertThat(result.page().content()).isEmpty();
        assertThat(result.page().totalElements()).isZero();
        assertThat(result.summary().openingBalance()).isEqualByComparingTo("1500");
        assertThat(result.summary().closingBalance()).isEqualByComparingTo("1500");
    }

    @Test
    void page_emptyResultShouldReturnZeroSummary() {
        doReturn(List.of()).when(jdbcTemplate).query(
                anyString(), any(MapSqlParameterSource.class), pageRowMapper());
        doReturn(new LedgerSummaryAndTotal(summary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), 0L))
                .when(jdbcTemplate).queryForObject(
                        anyString(), any(MapSqlParameterSource.class), summaryMapper());

        CashLedgerPageResponse result = repository().page(filter(), new PageQuery(0, 30, null, null));

        assertThat(result.page().content()).isEmpty();
        assertThat(result.page().totalElements()).isZero();
        assertThat(result.page().totalPages()).isZero();
        assertThat(result.summary()).isEqualTo(summary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void page_shouldNotRunFallbackWhenPageHasRows() {
        stubPageRows(new LedgerPageRow(line(1L), 1L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        repository().page(filter(), new PageQuery(0, 30, null, null));

        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void page_fallbackAndDataQueryShouldShareTheSamePredicates() {
        doReturn(List.of()).when(jdbcTemplate).query(
                anyString(), any(MapSqlParameterSource.class), pageRowMapper());
        doReturn(new LedgerSummaryAndTotal(summary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), 0L))
                .when(jdbcTemplate).queryForObject(
                        anyString(), any(MapSqlParameterSource.class), summaryMapper());

        CashLedgerFilter filter = new CashLedgerFilter(
                COMPANY_ID, START_DATE, END_DATE, "供应商", 42L, "PAYMENT", "KW");
        repository().page(filter, new PageQuery(0, 30, null, null));

        ArgumentCaptor<String> pagedSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(pagedSql.capture(), any(MapSqlParameterSource.class), pageRowMapper());
        ArgumentCaptor<String> fallbackSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                fallbackSql.capture(), any(MapSqlParameterSource.class), summaryMapper());

        // 期间谓词在单查询与回退查询中必须完全一致，且不限定别名
        // （单查询作用在 base_ledger 上，回退查询作用在 ledger 上，列名相同）
        String periodPredicate = "business_date >= :startDate AND business_date <= :endDate";
        assertThat(pagedSql.getValue()).contains("WHERE " + periodPredicate);
        assertThat(pagedSql.getValue()).contains("WHERE ledger.counterparty_type = :counterpartyType");
        assertThat(fallbackSql.getValue()).contains("FILTER (WHERE " + periodPredicate + ")");
        assertThat(fallbackSql.getValue()).contains("WHERE ledger.counterparty_type = :counterpartyType");
        assertThat(fallbackSql.getValue()).doesNotContain("ledger.business_date >=");
    }

    @Test
    void page_withoutStartDateShouldUseConstantZeroOpeningInFallback() {
        doReturn(List.of()).when(jdbcTemplate).query(
                anyString(), any(MapSqlParameterSource.class), pageRowMapper());
        doReturn(new LedgerSummaryAndTotal(summary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), 0L))
                .when(jdbcTemplate).queryForObject(
                        anyString(), any(MapSqlParameterSource.class), summaryMapper());
        CashLedgerFilter filter = new CashLedgerFilter(COMPANY_ID, null, null, null, null, null, null);

        repository().page(filter, new PageQuery(0, 30, null, null));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), any(MapSqlParameterSource.class), summaryMapper());
        assertThat(sqlCaptor.getValue())
                .contains("CAST(0 AS NUMERIC) AS opening_balance")
                .doesNotContain("ledger.business_date < :startDate");
    }

    @Test
    void listForExport_shouldKeepOriginalSummaryAndOpeningBalanceBinding() {
        doReturn(new LedgerSummaryAndTotal(
                summary(BigDecimal.valueOf(1500), BigDecimal.valueOf(3000), BigDecimal.valueOf(1500)), 6L))
                .when(jdbcTemplate).queryForObject(
                        anyString(), any(MapSqlParameterSource.class), summaryMapper());
        doReturn(List.of(line(1L))).when(jdbcTemplate).query(
                anyString(), any(MapSqlParameterSource.class), lineMapper());

        List<CashLedgerLineResponse> rows = repository().listForExport(filter());

        assertThat(rows).hasSize(1);
        ArgumentCaptor<String> dataSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(dataSql.capture(), paramsCaptor.capture(), lineMapper());

        // 导出路径保持原状：仍然绑定 openingBalance 且使用原 ORDER BY（asc）
        assertThat(dataSql.getValue())
                .contains(":openingBalance + SUM(filtered_ledger.balance_change) OVER (")
                .contains("ORDER BY ledger_with_balance.business_date ASC")
                .doesNotContain("COUNT(1) OVER ()");
        assertThat(paramsCaptor.getValue().getValue("openingBalance")).isEqualTo(BigDecimal.valueOf(1500));
    }

    private CashLedgerQueryRepository repository() {
        return new CashLedgerQueryRepository(jdbcTemplate);
    }

    private CashLedgerFilter filter() {
        return new CashLedgerFilter(COMPANY_ID, START_DATE, END_DATE, null, null, null, null);
    }

    private void stubPageRows(LedgerPageRow... rows) {
        doReturn(List.of(rows)).when(jdbcTemplate).query(
                anyString(), any(MapSqlParameterSource.class), pageRowMapper());
    }

    private RowMapper<LedgerPageRow> pageRowMapper() {
        return org.mockito.ArgumentMatchers.<RowMapper<LedgerPageRow>>any();
    }

    private RowMapper<LedgerSummaryAndTotal> summaryMapper() {
        return org.mockito.ArgumentMatchers.<RowMapper<LedgerSummaryAndTotal>>any();
    }

    private RowMapper<CashLedgerLineResponse> lineMapper() {
        return org.mockito.ArgumentMatchers.<RowMapper<CashLedgerLineResponse>>any();
    }

    private CashLedgerLineResponse line(long id) {
        return new CashLedgerLineResponse(
                LocalDate.of(2026, 9, 16),
                "PAYMENT",
                id,
                String.valueOf(id),
                "供应商",
                9L,
                "测试供应商",
                "SUPPLIER_PAYMENT",
                BigDecimal.ZERO,
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(1500),
                "系统管理员",
                "E2E"
        );
    }

    private CashLedgerSummaryResponse summary(BigDecimal opening, BigDecimal income, BigDecimal expense) {
        return new CashLedgerSummaryResponse(
                opening,
                income,
                expense,
                opening.add(income).subtract(expense)
        );
    }
}
