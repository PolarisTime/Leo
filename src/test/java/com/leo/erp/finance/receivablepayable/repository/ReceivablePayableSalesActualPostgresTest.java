package com.leo.erp.finance.receivablepayable.repository;

import com.leo.erp.common.api.PageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=false")
class ReceivablePayableSalesActualPostgresTest {

    private static final long BASE_ID = 8_740_000_000_000_000_000L;
    private static final long SETTLEMENT_COMPANY_ID = BASE_ID + 100;
    private static final String CUSTOMER_CODE = "TEST-RECEIVABLE-CUSTOMER";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ReceivablePayableQueryRepository repository;

    @BeforeEach
    void setUp() {
        ReceivablePayablePostgresTestSchemaSupport.preparePurchaseLedgerSchema(jdbcTemplate);
        repository = new ReceivablePayableQueryRepository(new NamedParameterJdbcTemplate(jdbcTemplate));
        insertAuditedSalesOrder();
    }

    @Test
    void shouldRecognizeEachAuditedOutboundForAuditedButNotCompletedOrder() {
        insertOutbound(BASE_ID + 10, BASE_ID + 2, "TEST-RECEIVABLE-OUT-1", "已审核", false,
                LocalDate.of(2026, 4, 1), "400.00");
        insertOutbound(BASE_ID + 20, BASE_ID + 3, "TEST-RECEIVABLE-OUT-2", "已审核", false,
                LocalDate.of(2026, 7, 10), "560.00");
        insertOutbound(BASE_ID + 30, BASE_ID + 4, "TEST-RECEIVABLE-OUT-DRAFT", "草稿", false,
                LocalDate.of(2026, 7, 10), "30.00");
        insertOutbound(BASE_ID + 40, BASE_ID + 5, "TEST-RECEIVABLE-OUT-DELETED", "已审核", true,
                LocalDate.of(2026, 7, 10), "10.00");

        var result = repository.page(
                PageQuery.of(0, 20, "counterpartyName", "asc"),
                "应收",
                "客户",
                SETTLEMENT_COMPANY_ID,
                "未对账",
                null,
                CUSTOMER_CODE
        );

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.recognizedAmount()).isEqualByComparingTo("960.00");
            assertThat(row.settledAmount()).isEqualByComparingTo("0.00");
            assertThat(row.balanceAmount()).isEqualByComparingTo("960.00");
            assertThat(row.days0To30Amount()
                    .add(row.days31To60Amount())
                    .add(row.days61To90Amount())
                    .add(row.daysOver90Amount())).isEqualByComparingTo("960.00");
            assertThat(row.entryCount()).isEqualTo(2L);
        });
        assertThat(repository.findSummary(
                "应收", "客户", CUSTOMER_CODE,
                String.valueOf(SETTLEMENT_COMPANY_ID), "未对账"
        ).recognizedAmount()).isEqualByComparingTo("960.00");
        assertThat(repository.listForExport(
                "应收", "客户", SETTLEMENT_COMPANY_ID, "未对账", null, CUSTOMER_CODE
        )).singleElement().satisfies(row ->
                assertThat(row.recognizedAmount()).isEqualByComparingTo("960.00")
        );
        assertThat(repository.detailItems(
                "应收",
                "客户",
                CUSTOMER_CODE,
                String.valueOf(SETTLEMENT_COMPANY_ID),
                "未对账"
        )).satisfiesExactlyInAnyOrder(
                item -> {
                    assertThat(item.sourceType()).isEqualTo("销售出库单");
                    assertThat(item.documentNo()).isEqualTo("TEST-RECEIVABLE-OUT-1");
                    assertThat(item.accountingDate()).isEqualTo(LocalDate.of(2026, 4, 1));
                    assertThat(item.dueDate()).isEqualTo(LocalDate.of(2026, 4, 1));
                    assertThat(item.debitAmount()).isEqualByComparingTo("400.00");
                },
                item -> {
                    assertThat(item.sourceType()).isEqualTo("销售出库单");
                    assertThat(item.documentNo()).isEqualTo("TEST-RECEIVABLE-OUT-2");
                    assertThat(item.accountingDate()).isEqualTo(LocalDate.of(2026, 7, 10));
                    assertThat(item.dueDate()).isEqualTo(LocalDate.of(2026, 7, 10));
                    assertThat(item.debitAmount()).isEqualByComparingTo("560.00");
                }
        );
    }

    @Test
    void shouldNotRecognizeAuditedOrderWithoutAuditedOutbound() {
        insertOutbound(BASE_ID + 50, BASE_ID + 2, "TEST-RECEIVABLE-OUT-DRAFT-ONLY", "草稿", false,
                LocalDate.of(2026, 7, 10), "1000.00");

        var result = repository.page(
                PageQuery.of(0, 20, "counterpartyName", "asc"),
                "应收",
                "客户",
                SETTLEMENT_COMPANY_ID,
                "未对账",
                null,
                CUSTOMER_CODE
        );

        assertThat(result).isEmpty();
    }

    private void insertAuditedSalesOrder() {
        jdbcTemplate.update("""
                INSERT INTO so_sales_order (
                    id, order_no, customer_code, customer_name, project_name, delivery_date,
                    sales_name, settlement_company_id, settlement_company_name,
                    total_weight, total_amount, status, deleted_flag, created_by
                ) VALUES (?, 'TEST-RECEIVABLE-SO', ?, '应收实绩测试客户', '应收实绩测试项目',
                          TIMESTAMP '2026-07-10 10:00:00', '测试销售', ?, '销售结算主体甲',
                          100, 1000, '已审核', FALSE, 101)
                """, BASE_ID + 1, CUSTOMER_CODE, SETTLEMENT_COMPANY_ID);
        insertSalesOrderItem(BASE_ID + 2, 1, "400.00");
        insertSalesOrderItem(BASE_ID + 3, 2, "560.00");
        insertSalesOrderItem(BASE_ID + 4, 3, "30.00");
        insertSalesOrderItem(BASE_ID + 5, 4, "10.00");
    }

    private void insertSalesOrderItem(long itemId, int lineNo, String amount) {
        jdbcTemplate.update("""
                INSERT INTO so_sales_order_item (
                    id, order_id, line_no, material_code, brand, category, material, spec, unit,
                    quantity, quantity_unit, piece_weight_ton, pieces_per_bundle, weight_ton,
                    unit_price, amount
                ) VALUES (?, ?, ?, 'TEST-RECEIVABLE-MATERIAL', '测试品牌', '测试品类',
                          '测试材质', '10', '吨', 1, '件', 1, 1, 1, 10, ?)
                """, itemId, BASE_ID + 1, lineNo, new BigDecimal(amount));
    }

    private void insertOutbound(long outboundId,
                                long sourceSalesOrderItemId,
                                String outboundNo,
                                String status,
                                boolean deleted,
                                LocalDate outboundDate,
                                String amount) {
        BigDecimal actualAmount = new BigDecimal(amount);
        jdbcTemplate.update("""
                INSERT INTO so_sales_outbound (
                    id, outbound_no, sales_order_no, customer_name, project_name, warehouse_name,
                    outbound_date, settlement_company_id, settlement_company_name,
                    total_weight, total_amount, status, deleted_flag, created_by
                ) VALUES (?, ?, 'TEST-RECEIVABLE-SO', '应收实绩测试客户', '应收实绩测试项目',
                          '应收实绩测试仓', ?, ?, '销售结算主体甲',
                          ?, ?, ?, ?, 202)
                """, outboundId, outboundNo, java.sql.Date.valueOf(outboundDate), SETTLEMENT_COMPANY_ID,
                actualAmount.movePointLeft(1),
                actualAmount, status, deleted);
        jdbcTemplate.update("""
                INSERT INTO so_sales_outbound_item (
                    id, outbound_id, line_no, source_sales_order_item_id,
                    material_code, brand, category, material, spec, unit,
                    quantity, quantity_unit, piece_weight_ton, pieces_per_bundle, weight_ton,
                    unit_price, amount, warehouse_name
                ) VALUES (?, ?, 1, ?, 'TEST-RECEIVABLE-MATERIAL', '测试品牌', '测试品类',
                          '测试材质', '10', '吨', 1, '件', 1, 1, 1, 10, ?, '应收实绩测试仓')
                """, outboundId + 1, outboundId, sourceSalesOrderItemId, actualAmount);
    }
}
