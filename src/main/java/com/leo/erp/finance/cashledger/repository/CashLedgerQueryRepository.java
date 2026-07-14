package com.leo.erp.finance.cashledger.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerLineResponse;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerPageResponse;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerSummaryResponse;
import com.leo.erp.security.permission.DataScopeContext;
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
import java.util.Set;

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

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CashLedgerQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CashLedgerPageResponse page(CashLedgerFilter filter, PageQuery query) {
        MapSqlParameterSource parameters = parameters(filter);
        CashLedgerSummaryResponse summary = querySummary(filter, parameters);
        String basePredicate = basePredicate(filter, parameters);
        String periodPredicate = periodPredicate(filter, parameters);
        String whereClause = whereClause(basePredicate, periodPredicate);

        Number totalNumber = jdbcTemplate.queryForObject(
                LEDGER_CTE + "SELECT COUNT(1) FROM ledger\n" + whereClause,
                parameters,
                Number.class
        );
        long total = totalNumber == null ? 0L : totalNumber.longValue();
        Page<CashLedgerLineResponse> page;
        if (total == 0L) {
            page = new PageImpl<>(List.of(), PageRequest.of(query.page(), query.size()), 0L);
        } else {
            parameters.addValue("openingBalance", summary.openingBalance());
            parameters.addValue("limit", query.size());
            parameters.addValue("offset", (long) query.page() * query.size());
            String dataSql = ledgerDataSql(whereClause)
                    + stableOrder(query.direction())
                    + " LIMIT :limit OFFSET :offset";
            List<CashLedgerLineResponse> rows = jdbcTemplate.query(dataSql, parameters, LINE_ROW_MAPPER);
            page = new PageImpl<>(rows, PageRequest.of(query.page(), query.size()), total);
        }
        return new CashLedgerPageResponse(summary, PageResponse.from(page));
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
        String openingExpression = filter.startDate() == null
                ? "CAST(0 AS NUMERIC)"
                : "COALESCE(SUM(ledger.balance_change) FILTER "
                + "(WHERE ledger.business_date < :startDate), 0)";
        String summarySql = LEDGER_CTE + """
                SELECT
                    %s AS opening_balance,
                    COALESCE(SUM(ledger.income_amount) FILTER (WHERE %s), 0) AS period_income,
                    COALESCE(SUM(ledger.expense_amount) FILTER (WHERE %s), 0) AS period_expense
                FROM ledger
                %s
                """.formatted(
                openingExpression,
                periodPredicate,
                periodPredicate,
                whereClause(basePredicate)
        );
        return jdbcTemplate.queryForObject(summarySql, parameters, (resultSet, rowNum) -> {
            BigDecimal openingBalance = safe(resultSet.getBigDecimal("opening_balance"));
            BigDecimal periodIncome = safe(resultSet.getBigDecimal("period_income"));
            BigDecimal periodExpense = safe(resultSet.getBigDecimal("period_expense"));
            return new CashLedgerSummaryResponse(
                    openingBalance,
                    periodIncome,
                    periodExpense,
                    openingBalance.add(periodIncome).subtract(periodExpense)
            );
        });
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
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("settlementCompanyId", filter.settlementCompanyId(), Types.BIGINT);
        if (filter.startDate() != null) {
            parameters.addValue("startDate", filter.startDate(), Types.DATE);
        }
        if (filter.endDate() != null) {
            parameters.addValue("endDate", filter.endDate(), Types.DATE);
        }
        return parameters;
    }

    private String basePredicate(CashLedgerFilter filter, MapSqlParameterSource parameters) {
        List<String> predicates = new ArrayList<>();
        addDataScopePredicate(parameters, predicates);
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
        if (filter.startDate() != null) {
            parameters.addValue("startDate", filter.startDate(), Types.DATE);
            predicates.add("ledger.business_date >= :startDate");
        }
        if (filter.endDate() != null) {
            parameters.addValue("endDate", filter.endDate(), Types.DATE);
            predicates.add("ledger.business_date <= :endDate");
        }
        return predicates.isEmpty() ? "TRUE" : String.join(" AND ", predicates);
    }

    private void addDataScopePredicate(
            MapSqlParameterSource parameters,
            List<String> predicates
    ) {
        Set<Long> ownerUserIds = DataScopeContext.allowedOwnerUserIds();
        if (ownerUserIds == null) {
            return;
        }
        if (ownerUserIds.isEmpty()) {
            predicates.add("1 = 0");
            return;
        }
        parameters.addValue("dataScopeOwnerUserIds", ownerUserIds);
        predicates.add("ledger.created_by IN (:dataScopeOwnerUserIds)");
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

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
