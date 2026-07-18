package com.leo.erp.finance.overview.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.finance.overview.web.dto.FinanceBalanceResponse;
import com.leo.erp.finance.overview.web.dto.FinanceOverviewSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Repository
public class FinanceOverviewQueryRepository {

    private static final String BALANCE_CTE = """
            WITH finance_facts AS (
                SELECT
                    'RECEIVABLE'::text AS direction,
                    '客户'::text AS counterparty_type,
                    outbound.customer_id AS counterparty_id,
                    MAX(customer.customer_code) AS counterparty_code,
                    MAX(outbound.customer_name) AS counterparty_name,
                    SUM(item.amount) AS recognized_amount,
                    CAST(0 AS NUMERIC) AS settled_amount
                FROM so_sales_outbound outbound
                JOIN so_sales_outbound_item item ON item.outbound_id = outbound.id
                LEFT JOIN md_customer customer ON customer.id = outbound.customer_id
                WHERE outbound.deleted_flag = FALSE
                  AND outbound.status = '已审核'
                  AND outbound.settlement_company_id = :settlementCompanyId
                  AND outbound.outbound_date < :asOfDate + INTERVAL '1 day'
                GROUP BY outbound.customer_id

                UNION ALL

                SELECT
                    'PAYABLE'::text,
                    '供应商'::text,
                    inbound.supplier_id,
                    MAX(inbound.supplier_code),
                    MAX(inbound.supplier_name),
                    SUM(item.amount + COALESCE(item.weight_adjustment_amount, 0)),
                    CAST(0 AS NUMERIC)
                FROM po_purchase_inbound inbound
                JOIN po_purchase_inbound_item item ON item.inbound_id = inbound.id
                WHERE inbound.deleted_flag = FALSE
                  AND inbound.status IN ('已审核', '完成入库')
                  AND inbound.settlement_company_id = :settlementCompanyId
                  AND inbound.inbound_date < :asOfDate + INTERVAL '1 day'
                GROUP BY inbound.supplier_id

                UNION ALL

                SELECT
                    'PAYABLE'::text,
                    '物流商'::text,
                    bill.carrier_id,
                    MAX(bill.carrier_code),
                    MAX(bill.carrier_name),
                    SUM(bill.total_freight),
                    CAST(0 AS NUMERIC)
                FROM lg_freight_bill bill
                WHERE bill.deleted_flag = FALSE
                  AND bill.status = '已审核'
                  AND bill.settlement_company_id = :settlementCompanyId
                  AND bill.bill_time < :asOfDate + INTERVAL '1 day'
                GROUP BY bill.carrier_id

                UNION ALL

                SELECT
                    'RECEIVABLE'::text,
                    '客户'::text,
                    receipt.counterparty_id,
                    MAX(receipt.counterparty_code),
                    MAX(receipt.counterparty_name),
                    CAST(0 AS NUMERIC),
                    SUM(receipt.amount)
                FROM fm_receipt receipt
                WHERE receipt.deleted_flag = FALSE
                  AND receipt.status = '已审核'
                  AND receipt.counterparty_type = '客户'
                  AND receipt.settlement_company_id = :settlementCompanyId
                  AND receipt.receipt_date < :asOfDate + INTERVAL '1 day'
                GROUP BY receipt.counterparty_id

                UNION ALL

                SELECT
                    'PAYABLE'::text,
                    payment.counterparty_type,
                    payment.counterparty_id,
                    MAX(payment.counterparty_code),
                    MAX(payment.counterparty_name),
                    CAST(0 AS NUMERIC),
                    SUM(payment.amount)
                FROM fm_payment payment
                WHERE payment.deleted_flag = FALSE
                  AND payment.status = '已审核'
                  AND payment.counterparty_type IN ('供应商', '物流商')
                  AND payment.settlement_company_id = :settlementCompanyId
                  AND payment.payment_date < :asOfDate + INTERVAL '1 day'
                GROUP BY payment.counterparty_type, payment.counterparty_id

                UNION ALL

                SELECT
                    'PAYABLE'::text,
                    '供应商'::text,
                    receipt.counterparty_id,
                    MAX(receipt.counterparty_code),
                    MAX(receipt.counterparty_name),
                    CAST(0 AS NUMERIC),
                    -SUM(receipt.amount)
                FROM fm_receipt receipt
                WHERE receipt.deleted_flag = FALSE
                  AND receipt.status = '已审核'
                  AND receipt.counterparty_type = '供应商'
                  AND receipt.settlement_company_id = :settlementCompanyId
                  AND receipt.receipt_date < :asOfDate + INTERVAL '1 day'
                GROUP BY receipt.counterparty_id

                UNION ALL

                SELECT
                    'PAYABLE'::text,
                    '供应商'::text,
                    reversal.counterparty_id,
                    MAX(reversal.counterparty_code),
                    MAX(reversal.counterparty_name),
                    CAST(0 AS NUMERIC),
                    -SUM(reversal.amount)
                FROM fm_cash_reversal reversal
                WHERE reversal.deleted_flag = FALSE
                  AND reversal.status = '已审核'
                  AND reversal.original_payment_id IS NOT NULL
                  AND reversal.settlement_company_id = :settlementCompanyId
                  AND reversal.reversal_date <= :asOfDate
                GROUP BY reversal.counterparty_id

                UNION ALL

                SELECT
                    'PAYABLE'::text,
                    '供应商'::text,
                    reversal.counterparty_id,
                    MAX(reversal.counterparty_code),
                    MAX(reversal.counterparty_name),
                    CAST(0 AS NUMERIC),
                    SUM(reversal.amount)
                FROM fm_cash_reversal reversal
                WHERE reversal.deleted_flag = FALSE
                  AND reversal.status = '已审核'
                  AND reversal.original_receipt_id IS NOT NULL
                  AND reversal.settlement_company_id = :settlementCompanyId
                  AND reversal.reversal_date <= :asOfDate
                GROUP BY reversal.counterparty_id
            ), party_balances AS (
                SELECT
                    direction,
                    counterparty_type,
                    counterparty_id,
                    MAX(counterparty_code) AS counterparty_code_snapshot,
                    MAX(counterparty_name) AS counterparty_name_snapshot,
                    SUM(recognized_amount) AS recognized_amount,
                    SUM(settled_amount) AS settled_amount
                FROM finance_facts
                GROUP BY direction, counterparty_type, counterparty_id
            ), calculated AS (
                SELECT
                    balance.direction,
                    balance.counterparty_type,
                    balance.counterparty_id,
                    COALESCE(
                        CASE balance.counterparty_type
                            WHEN '客户' THEN customer.customer_code
                            WHEN '供应商' THEN supplier.supplier_code
                            WHEN '物流商' THEN carrier.carrier_code
                        END,
                        balance.counterparty_code_snapshot
                    ) AS counterparty_code,
                    COALESCE(
                        CASE balance.counterparty_type
                            WHEN '客户' THEN customer.customer_name
                            WHEN '供应商' THEN supplier.supplier_name
                            WHEN '物流商' THEN carrier.carrier_name
                        END,
                        balance.counterparty_name_snapshot
                    ) AS counterparty_name,
                    CAST(:settlementCompanyId AS BIGINT) AS settlement_company_id,
                    company.company_name AS settlement_company_name,
                    balance.recognized_amount,
                    balance.settled_amount,
                    GREATEST(balance.recognized_amount - balance.settled_amount, 0) AS outstanding_amount,
                    GREATEST(balance.settled_amount - balance.recognized_amount, 0) AS advance_amount
                FROM party_balances balance
                LEFT JOIN md_customer customer
                  ON balance.counterparty_type = '客户' AND customer.id = balance.counterparty_id
                LEFT JOIN md_supplier supplier
                  ON balance.counterparty_type = '供应商' AND supplier.id = balance.counterparty_id
                LEFT JOIN md_carrier carrier
                  ON balance.counterparty_type = '物流商' AND carrier.id = balance.counterparty_id
                LEFT JOIN sys_company_setting company ON company.id = :settlementCompanyId
            )
            """;

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "counterpartyName", "counterparty_name",
            "recognizedAmount", "recognized_amount",
            "settledAmount", "settled_amount",
            "outstandingAmount", "outstanding_amount",
            "advanceAmount", "advance_amount"
    );

    private static final RowMapper<FinanceBalanceResponse> BALANCE_ROW_MAPPER = (resultSet, rowNum) ->
            new FinanceBalanceResponse(
                    resultSet.getString("direction"),
                    resultSet.getString("counterparty_type"),
                    resultSet.getObject("counterparty_id", Long.class),
                    resultSet.getString("counterparty_code"),
                    resultSet.getString("counterparty_name"),
                    resultSet.getObject("settlement_company_id", Long.class),
                    resultSet.getString("settlement_company_name"),
                    resultSet.getBigDecimal("recognized_amount"),
                    resultSet.getBigDecimal("settled_amount"),
                    resultSet.getBigDecimal("outstanding_amount"),
                    resultSet.getBigDecimal("advance_amount")
            );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FinanceOverviewQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OverviewResult overview(FinanceOverviewFilter filter, PageQuery query) {
        MapSqlParameterSource parameters = parameters(filter);
        String whereClause = whereClause(filter);
        FinanceOverviewSummaryResponse summary = querySummary(parameters, whereClause);
        Number totalNumber = jdbcTemplate.queryForObject(
                BALANCE_CTE + "SELECT COUNT(1) FROM calculated\n" + whereClause,
                parameters,
                Number.class
        );
        long total = totalNumber == null ? 0L : totalNumber.longValue();
        Page<FinanceBalanceResponse> page;
        if (total == 0L) {
            page = new PageImpl<>(List.of(), PageRequest.of(query.page(), query.size()), 0L);
        } else {
            parameters.addValue("limit", query.size());
            parameters.addValue("offset", (long) query.page() * query.size());
            List<FinanceBalanceResponse> rows = jdbcTemplate.query(
                    BALANCE_CTE
                            + "SELECT * FROM calculated\n"
                            + whereClause
                            + orderBy(query)
                            + " LIMIT :limit OFFSET :offset",
                    parameters,
                    BALANCE_ROW_MAPPER
            );
            page = new PageImpl<>(rows, PageRequest.of(query.page(), query.size()), total);
        }
        return new OverviewResult(summary, page);
    }

    private FinanceOverviewSummaryResponse querySummary(MapSqlParameterSource parameters, String whereClause) {
        return jdbcTemplate.queryForObject(
                BALANCE_CTE + """
                        SELECT
                            COALESCE(SUM(recognized_amount) FILTER (WHERE direction = 'RECEIVABLE'), 0) AS receivable_amount,
                            COALESCE(SUM(settled_amount) FILTER (WHERE direction = 'RECEIVABLE'), 0) AS received_amount,
                            COALESCE(SUM(outstanding_amount) FILTER (WHERE direction = 'RECEIVABLE'), 0) AS unreceived_amount,
                            COALESCE(SUM(advance_amount) FILTER (WHERE direction = 'RECEIVABLE'), 0) AS advance_receipt_amount,
                            COALESCE(SUM(recognized_amount) FILTER (WHERE direction = 'PAYABLE'), 0) AS payable_amount,
                            COALESCE(SUM(settled_amount) FILTER (WHERE direction = 'PAYABLE'), 0) AS paid_amount,
                            COALESCE(SUM(outstanding_amount) FILTER (WHERE direction = 'PAYABLE'), 0) AS unpaid_amount,
                            COALESCE(SUM(advance_amount) FILTER (WHERE direction = 'PAYABLE'), 0) AS advance_payment_amount
                        FROM calculated
                        """ + whereClause,
                parameters,
                (resultSet, rowNum) -> new FinanceOverviewSummaryResponse(
                        amount(resultSet.getBigDecimal("receivable_amount")),
                        amount(resultSet.getBigDecimal("received_amount")),
                        amount(resultSet.getBigDecimal("unreceived_amount")),
                        amount(resultSet.getBigDecimal("advance_receipt_amount")),
                        amount(resultSet.getBigDecimal("payable_amount")),
                        amount(resultSet.getBigDecimal("paid_amount")),
                        amount(resultSet.getBigDecimal("unpaid_amount")),
                        amount(resultSet.getBigDecimal("advance_payment_amount"))
                )
        );
    }

    private MapSqlParameterSource parameters(FinanceOverviewFilter filter) {
        return new MapSqlParameterSource()
                .addValue("settlementCompanyId", filter.settlementCompanyId())
                .addValue("asOfDate", filter.asOfDate())
                .addValue("direction", filter.direction(), Types.VARCHAR)
                .addValue("counterpartyType", filter.counterpartyType(), Types.VARCHAR)
                .addValue("keyword", normalizeKeyword(filter.keyword()), Types.VARCHAR);
    }

    private String whereClause(FinanceOverviewFilter filter) {
        StringBuilder predicate = new StringBuilder("WHERE 1 = 1\n");
        if (filter.direction() != null) {
            predicate.append("  AND direction = :direction\n");
        }
        if (filter.counterpartyType() != null) {
            predicate.append("  AND counterparty_type = :counterpartyType\n");
        }
        if (filter.keyword() != null) {
            predicate.append("""
                      AND (
                          LOWER(COALESCE(counterparty_code, '')) LIKE '%' || :keyword || '%'
                          OR LOWER(COALESCE(counterparty_name, '')) LIKE '%' || :keyword || '%'
                          OR CAST(counterparty_id AS TEXT) LIKE '%' || :keyword || '%'
                      )
                    """);
        }
        if (filter.onlyOpen()) {
            predicate.append("  AND (outstanding_amount > 0 OR advance_amount > 0)\n");
        }
        return predicate.toString();
    }

    private String orderBy(PageQuery query) {
        String column = SORT_COLUMNS.getOrDefault(query.sortBy(), "outstanding_amount");
        String direction = "asc".equalsIgnoreCase(query.direction()) ? "ASC" : "DESC";
        return " ORDER BY " + column + " " + direction
                + ", counterparty_name ASC, counterparty_id ASC";
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank()
                ? null
                : keyword.trim().toLowerCase(Locale.ROOT);
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record OverviewResult(
            FinanceOverviewSummaryResponse summary,
            Page<FinanceBalanceResponse> balances
    ) {
    }
}
