package com.leo.erp.finance.cashledger.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerLineResponse;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerPageResponse;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class CashLedgerQueryRepository {

    private static final String LEDGER_CTE = """
            WITH ledger AS (
                SELECT
                    receipt.receipt_date::date AS business_date,
                    'RECEIPT' AS flow_type,
                    1 AS flow_order,
                    receipt.id AS document_id,
                    receipt.receipt_no AS document_no,
                    receipt.counterparty_type,
                    receipt.counterparty_id,
                    receipt.counterparty_code,
                    receipt.counterparty_name,
                    receipt.receipt_purpose AS purpose,
                    receipt.amount AS income_amount,
                    CAST(0 AS NUMERIC) AS expense_amount,
                    receipt.amount AS balance_change,
                    receipt.operator_name,
                    receipt.remark,
                    receipt.created_by
                FROM fm_receipt receipt
                WHERE receipt.deleted_flag = FALSE
                  AND receipt.status = '已审核'
                  AND receipt.settlement_company_id = :settlementCompanyId

                UNION ALL

                SELECT
                    payment.payment_date::date AS business_date,
                    'PAYMENT' AS flow_type,
                    2 AS flow_order,
                    payment.id AS document_id,
                    payment.payment_no AS document_no,
                    COALESCE(payment.counterparty_type, payment.business_type) AS counterparty_type,
                    payment.counterparty_id,
                    payment.counterparty_code,
                    payment.counterparty_name,
                    payment.payment_purpose AS purpose,
                    CAST(0 AS NUMERIC) AS income_amount,
                    payment.amount AS expense_amount,
                    -payment.amount AS balance_change,
                    payment.operator_name,
                    payment.remark,
                    payment.created_by
                FROM fm_payment payment
                WHERE payment.deleted_flag = FALSE
                  AND payment.status = '已审核'
                  AND payment.settlement_company_id = :settlementCompanyId

                UNION ALL

                SELECT
                    reversal.reversal_date AS business_date,
                    'PAYMENT_REVERSAL' AS flow_type,
                    3 AS flow_order,
                    reversal.id AS document_id,
                    reversal.reversal_no AS document_no,
                    reversal.counterparty_type,
                    reversal.counterparty_id,
                    reversal.counterparty_code,
                    reversal.counterparty_name,
                    reversal.reason AS purpose,
                    reversal.amount AS income_amount,
                    CAST(0 AS NUMERIC) AS expense_amount,
                    reversal.amount AS balance_change,
                    reversal.operator_name,
                    reversal.remark,
                    reversal.created_by
                FROM fm_cash_reversal reversal
                WHERE reversal.deleted_flag = FALSE
                  AND reversal.status = '已审核'
                  AND reversal.original_payment_id IS NOT NULL
                  AND reversal.settlement_company_id = :settlementCompanyId

                UNION ALL

                SELECT
                    reversal.reversal_date AS business_date,
                    'RECEIPT_REVERSAL' AS flow_type,
                    4 AS flow_order,
                    reversal.id AS document_id,
                    reversal.reversal_no AS document_no,
                    reversal.counterparty_type,
                    reversal.counterparty_id,
                    reversal.counterparty_code,
                    reversal.counterparty_name,
                    reversal.reason AS purpose,
                    CAST(0 AS NUMERIC) AS income_amount,
                    reversal.amount AS expense_amount,
                    -reversal.amount AS balance_change,
                    reversal.operator_name,
                    reversal.remark,
                    reversal.created_by
                FROM fm_cash_reversal reversal
                WHERE reversal.deleted_flag = FALSE
                  AND reversal.status = '已审核'
                  AND reversal.original_receipt_id IS NOT NULL
                  AND reversal.settlement_company_id = :settlementCompanyId
            )
            """;

    private static final String TOTAL_COLUMN = "__total";

    private static final RowMapper<CashLedgerLineResponse> LINE_ROW_MAPPER = (resultSet, rowNum) ->
            new CashLedgerLineResponse(
                    resultSet.getObject("business_date", LocalDate.class),
                    resultSet.getString("flow_type"),
                    resultSet.getObject("document_id", Long.class),
                    resultSet.getString("document_no"),
                    resultSet.getString("counterparty_type"),
                    resultSet.getObject("counterparty_id", Long.class),
                    resultSet.getString("counterparty_name"),
                    resultSet.getString("purpose"),
                    resultSet.getBigDecimal("income_amount"),
                    resultSet.getBigDecimal("expense_amount"),
                    resultSet.getBigDecimal("running_balance"),
                    resultSet.getString("operator_name"),
                    resultSet.getString("remark")
            );

    /**
     * 分页查询同时携带全量窗口聚合（total + 期间收支 + 期初余额），
     * 所以每一行都是一份「流水行 + 全量汇总」。
     */
    private static final RowMapper<LedgerPageRow> LEDGER_PAGE_ROW_MAPPER = (resultSet, rowNum) ->
            new LedgerPageRow(
                    LINE_ROW_MAPPER.mapRow(resultSet, rowNum),
                    resultSet.getLong(TOTAL_COLUMN),
                    safe(resultSet.getBigDecimal("opening_balance")),
                    safe(resultSet.getBigDecimal("period_income")),
                    safe(resultSet.getBigDecimal("period_expense"))
            );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CashLedgerQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CashLedgerPageResponse page(CashLedgerFilter filter, PageQuery query) {
        MapSqlParameterSource parameters = parameters(filter);
        String basePredicate = basePredicate(filter, parameters);
        String periodPredicate = periodPredicate(filter, parameters);

        parameters.addValue("limit", query.size());
        parameters.addValue("offset", (long) query.page() * query.size());
        // 单条查询：窗口函数在 LIMIT/OFFSET 之前对整个过滤结果集求值，
        // 因此一次 ledger CTE 扫描即可拿到「当前页数据 + total + 全量 summary + running_balance」。
        List<LedgerPageRow> rows = jdbcTemplate.query(
                ledgerPageSql(basePredicate, periodPredicate, query.direction()),
                parameters,
                LEDGER_PAGE_ROW_MAPPER
        );

        if (!rows.isEmpty()) {
            LedgerPageRow first = rows.get(0);
            List<CashLedgerLineResponse> content = rows.stream().map(LedgerPageRow::line).toList();
            Page<CashLedgerLineResponse> page = new PageImpl<>(
                    content,
                    PageRequest.of(query.page(), query.size()),
                    first.total()
            );
            return new CashLedgerPageResponse(
                    summaryOf(first.openingBalance(), first.periodIncome(), first.periodExpense()),
                    PageResponse.from(page)
            );
        }

        // 空页有两种可能：过滤结果本身为空，或 offset 越界（结果集非空）。
        // 单条窗口查询在这两种情况下都拿不到全量聚合，因此用一条合并的聚合查询补齐。
        // 注意：期初余额取自「基础筛选（不含期间）」的集合，期间过滤为空时它仍可能非 0，
        // 所以不能用「全 0 summary」代替回退查询。
        LedgerSummaryAndTotal fallback = querySummaryAndTotal(filter, parameters, basePredicate, periodPredicate);
        Page<CashLedgerLineResponse> emptyPage = new PageImpl<>(
                List.of(),
                PageRequest.of(query.page(), query.size()),
                fallback.total()
        );
        return new CashLedgerPageResponse(fallback.summary(), PageResponse.from(emptyPage));
    }

    public List<CashLedgerLineResponse> listForExport(CashLedgerFilter filter) {
        MapSqlParameterSource parameters = parameters(filter);
        CashLedgerSummaryResponse summary = querySummary(filter, parameters);
        parameters.addValue("openingBalance", summary.openingBalance());
        String whereClause = whereClause(
                basePredicate(filter, parameters),
                periodPredicate(filter, parameters)
        );
        return jdbcTemplate.query(
                ledgerDataSql(whereClause) + stableOrder("asc"),
                parameters,
                LINE_ROW_MAPPER
        );
    }

    private CashLedgerSummaryResponse querySummary(
            CashLedgerFilter filter,
            MapSqlParameterSource parameters
    ) {
        String basePredicate = basePredicate(filter, parameters);
        String periodPredicate = periodPredicate(filter, parameters);
        return querySummaryAndTotal(filter, parameters, basePredicate, periodPredicate).summary();
    }

    private LedgerSummaryAndTotal querySummaryAndTotal(
            CashLedgerFilter filter,
            MapSqlParameterSource parameters,
            String basePredicate,
            String periodPredicate
    ) {
        String openingExpression = filter.startDate() == null
                ? "CAST(0 AS NUMERIC)"
                : "COALESCE(SUM(ledger.balance_change) FILTER "
                + "(WHERE ledger.business_date < :startDate), 0)";
        String summarySql = LEDGER_CTE + """
                SELECT
                    %s AS opening_balance,
                    COALESCE(SUM(ledger.income_amount) FILTER (WHERE %s), 0) AS period_income,
                    COALESCE(SUM(ledger.expense_amount) FILTER (WHERE %s), 0) AS period_expense,
                    COUNT(1) FILTER (WHERE %s) AS %s
                FROM ledger
                %s
                """.formatted(
                openingExpression,
                periodPredicate,
                periodPredicate,
                periodPredicate,
                TOTAL_COLUMN,
                whereClause(basePredicate)
        );
        return jdbcTemplate.queryForObject(summarySql, parameters, (resultSet, rowNum) -> new LedgerSummaryAndTotal(
                summaryOf(
                        safe(resultSet.getBigDecimal("opening_balance")),
                        safe(resultSet.getBigDecimal("period_income")),
                        safe(resultSet.getBigDecimal("period_expense"))
                ),
                resultSet.getLong(TOTAL_COLUMN)
        ));
    }

    /**
     * 资金流水分页单查询 SQL。
     *
     * <p>层级说明：</p>
     * <ol>
     *     <li>{@code base_ledger}：只应用基础筛选（往来方/流水类型/关键字），不含期间，
     *         期初余额必须基于它计算；显式 MATERIALIZED 以保证 ledger CTE 只被扫描一次。</li>
     *     <li>{@code filtered_ledger}：在 base_ledger 上再应用期间筛选，即分页与期间汇总的数据源。</li>
     *     <li>{@code opening}：基于 base_ledger 计算期初余额，恒为 1 行。</li>
     *     <li>{@code ledger_with_balance}：窗口函数一次性算出 total、期间收入/支出与 running_balance。</li>
     * </ol>
     */
    private String ledgerPageSql(String basePredicate, String periodPredicate, String direction) {
        return LEDGER_CTE + """
                , base_ledger AS MATERIALIZED (
                    SELECT ledger.*
                    FROM ledger
                    %s
                ), filtered_ledger AS (
                    SELECT base_ledger.*
                    FROM base_ledger
                    WHERE %s
                ), opening AS (
                    SELECT COALESCE(SUM(base_ledger.balance_change) FILTER (
                        WHERE base_ledger.business_date < :startDate
                    ), 0) AS opening_balance
                    FROM base_ledger
                ), ledger_with_balance AS (
                    SELECT
                        filtered_ledger.*,
                        COUNT(1) OVER () AS %s,
                        COALESCE(SUM(filtered_ledger.income_amount) OVER (), 0) AS period_income,
                        COALESCE(SUM(filtered_ledger.expense_amount) OVER (), 0) AS period_expense,
                        opening.opening_balance AS opening_balance,
                        opening.opening_balance + SUM(filtered_ledger.balance_change) OVER (
                            ORDER BY filtered_ledger.business_date ASC,
                                     filtered_ledger.flow_order ASC,
                                     filtered_ledger.document_id ASC
                        ) AS running_balance
                    FROM filtered_ledger
                    CROSS JOIN opening
                )
                SELECT
                    ledger_with_balance.business_date,
                    ledger_with_balance.flow_type,
                    ledger_with_balance.flow_order,
                    ledger_with_balance.document_id,
                    ledger_with_balance.document_no,
                    ledger_with_balance.counterparty_type,
                    ledger_with_balance.counterparty_id,
                    ledger_with_balance.counterparty_name,
                    ledger_with_balance.purpose,
                    ledger_with_balance.income_amount,
                    ledger_with_balance.expense_amount,
                    ledger_with_balance.running_balance,
                    ledger_with_balance.operator_name,
                    ledger_with_balance.remark,
                    ledger_with_balance.%s,
                    ledger_with_balance.period_income,
                    ledger_with_balance.period_expense,
                    ledger_with_balance.opening_balance
                FROM ledger_with_balance
                """.formatted(
                whereClause(basePredicate),
                periodPredicate,
                TOTAL_COLUMN,
                TOTAL_COLUMN
        ) + stableOrder(direction) + " LIMIT :limit OFFSET :offset";
    }

    private String ledgerDataSql(String whereClause) {
        return LEDGER_CTE + """
                , filtered_ledger AS (
                    SELECT ledger.*
                    FROM ledger
                    %s
                ), ledger_with_balance AS (
                    SELECT
                        filtered_ledger.*,
                        :openingBalance + SUM(filtered_ledger.balance_change) OVER (
                            ORDER BY filtered_ledger.business_date ASC,
                                     filtered_ledger.flow_order ASC,
                                     filtered_ledger.document_id ASC
                        ) AS running_balance
                    FROM filtered_ledger
                )
                SELECT
                    ledger_with_balance.business_date,
                    ledger_with_balance.flow_type,
                    ledger_with_balance.flow_order,
                    ledger_with_balance.document_id,
                    ledger_with_balance.document_no,
                    ledger_with_balance.counterparty_type,
                    ledger_with_balance.counterparty_id,
                    ledger_with_balance.counterparty_name,
                    ledger_with_balance.purpose,
                    ledger_with_balance.income_amount,
                    ledger_with_balance.expense_amount,
                    ledger_with_balance.running_balance,
                    ledger_with_balance.operator_name,
                    ledger_with_balance.remark
                FROM ledger_with_balance
                """.formatted(whereClause);
    }

    private MapSqlParameterSource parameters(CashLedgerFilter filter) {
        // startDate/endDate 始终注册：分页单查询里的期初余额窗口表达式总会引用 :startDate，
        // 未传期间时绑定为 NULL，`business_date < NULL` 恒为 NULL，等同于原实现的 `CAST(0 AS NUMERIC)`。
        return new MapSqlParameterSource()
                .addValue("settlementCompanyId", filter.settlementCompanyId(), Types.BIGINT)
                .addValue("startDate", filter.startDate(), Types.DATE)
                .addValue("endDate", filter.endDate(), Types.DATE);
    }

    private String basePredicate(CashLedgerFilter filter, MapSqlParameterSource parameters) {
        List<String> predicates = new ArrayList<>();
        if (filter.counterpartyType() != null) {
            parameters.addValue("counterpartyType", filter.counterpartyType());
            predicates.add("ledger.counterparty_type = :counterpartyType");
        }
        if (filter.counterpartyId() != null) {
            parameters.addValue("counterpartyId", filter.counterpartyId(), Types.BIGINT);
            predicates.add("ledger.counterparty_id = :counterpartyId");
        }
        if (filter.flowType() != null) {
            parameters.addValue("flowType", filter.flowType());
            predicates.add("ledger.flow_type = :flowType");
        }
        String keyword = normalizeKeyword(filter.keyword());
        if (keyword != null) {
            parameters.addValue("keyword", keyword);
            predicates.add("""
                    (
                        POSITION(:keyword IN LOWER(COALESCE(ledger.document_no, ''))) > 0
                        OR POSITION(:keyword IN LOWER(COALESCE(ledger.counterparty_code, ''))) > 0
                        OR POSITION(:keyword IN LOWER(COALESCE(ledger.counterparty_name, ''))) > 0
                        OR POSITION(:keyword IN LOWER(COALESCE(ledger.purpose, ''))) > 0
                        OR POSITION(:keyword IN LOWER(COALESCE(ledger.operator_name, ''))) > 0
                        OR POSITION(:keyword IN LOWER(COALESCE(ledger.remark, ''))) > 0
                    )
                    """.stripIndent().trim());
        }
        return String.join(" AND ", predicates);
    }

    private String periodPredicate(CashLedgerFilter filter, MapSqlParameterSource parameters) {
        List<String> predicates = new ArrayList<>();
        // 期间谓词不限定别名：它同时用于 `FROM ledger`（汇总查询）
        // 与 `FROM base_ledger`（分页单查询，列名与 ledger 完全一致）。
        if (filter.startDate() != null) {
            parameters.addValue("startDate", filter.startDate(), Types.DATE);
            predicates.add("business_date >= :startDate");
        }
        if (filter.endDate() != null) {
            parameters.addValue("endDate", filter.endDate(), Types.DATE);
            predicates.add("business_date <= :endDate");
        }
        return predicates.isEmpty() ? "TRUE" : String.join(" AND ", predicates);
    }

    private String whereClause(String... predicates) {
        List<String> nonEmptyPredicates = java.util.Arrays.stream(predicates)
                .filter(predicate -> predicate != null && !predicate.isBlank())
                .toList();
        return nonEmptyPredicates.isEmpty()
                ? ""
                : "WHERE " + String.join(" AND ", nonEmptyPredicates);
    }

    private String stableOrder(String direction) {
        String normalizedDirection = "asc".equalsIgnoreCase(direction) ? "ASC" : "DESC";
        return " ORDER BY ledger_with_balance.business_date " + normalizedDirection
                + ", ledger_with_balance.flow_order " + normalizedDirection
                + ", ledger_with_balance.document_id " + normalizedDirection;
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank()
                ? null
                : keyword.trim().toLowerCase(Locale.ROOT);
    }

    private static BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static CashLedgerSummaryResponse summaryOf(
            BigDecimal openingBalance,
            BigDecimal periodIncome,
            BigDecimal periodExpense
    ) {
        return new CashLedgerSummaryResponse(
                openingBalance,
                periodIncome,
                periodExpense,
                openingBalance.add(periodIncome).subtract(periodExpense)
        );
    }

    record LedgerSummaryAndTotal(CashLedgerSummaryResponse summary, long total) {
    }

    record LedgerPageRow(
            CashLedgerLineResponse line,
            long total,
            BigDecimal openingBalance,
            BigDecimal periodIncome,
            BigDecimal periodExpense
    ) {
    }
}
