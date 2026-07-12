package com.leo.erp.finance.receivablepayable.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableResponse;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.testsupport.StableIdentityPostgresFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=false")
class ReceivablePayablePurchaseRefundPostgresTest {

    private static final long BASE_ID = 8_750_000_000_000_000_000L;
    private static final long SETTLEMENT_COMPANY_ID = BASE_ID + 100;
    private static final String SETTLEMENT_COMPANY_NAME = "采购结算主体甲";
    private static final String SUPPLIER_CODE = "TEST-REFUND-SUPPLIER";
    private static final String SUPPLIER_NAME = "采购退款测试供应商";
    private static final long SUPPLIER_ID = BASE_ID;
    private static final long MATERIAL_ID = BASE_ID + 101;
    private static final long WAREHOUSE_ID = BASE_ID + 102;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ReceivablePayableQueryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ReceivablePayableQueryRepository(new NamedParameterJdbcTemplate(jdbcTemplate));
        ReceivablePayablePostgresTestSchemaSupport.preparePurchaseLedgerSchema(jdbcTemplate);
        insertSupplier();
        StableIdentityPostgresFixtures.insertMaterial(
                jdbcTemplate, MATERIAL_ID, "TEST-REFUND-MATERIAL");
        StableIdentityPostgresFixtures.insertWarehouse(
                jdbcTemplate, WAREHOUSE_ID, "TEST-REFUND-WAREHOUSE", "退款测试仓");
        StableIdentityPostgresFixtures.insertSettlementCompany(
                jdbcTemplate, SETTLEMENT_COMPANY_ID, SETTLEMENT_COMPANY_NAME);
        StableIdentityPostgresFixtures.insertSettlementCompany(
                jdbcTemplate, SETTLEMENT_COMPANY_ID + 1, "采购结算主体乙");
        insertPurchaseOrder();
        insertInbound();
    }

    @Test
    void shouldAlwaysRecognizeAllEffectiveInboundAfterRefundAudit() {
        insertRefund("已审核");

        var result = pageUnreconciled();

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.settlementCompanyId()).isEqualTo(SETTLEMENT_COMPANY_ID);
            assertThat(row.recognizedAmount()).isEqualByComparingTo("114523.50");
            assertThat(row.settledAmount()).isEqualByComparingTo("0.00");
            assertThat(row.balanceAmount()).isEqualByComparingTo("114523.50");
            assertThat(row.entryCount()).isEqualTo(1L);
        });
        assertThat(detailUnreconciled())
                .extracting(item -> item.sourceType())
                .containsExactly("采购入库单");
    }

    @Test
    void shouldApplyInboundPrepaymentAndSupplierRefundReceiptAsIpr() {
        insertRefund("已审核");
        insertPurchasePrepayment();
        insertSupplierRefundReceipt("已收款");

        try {
            DataScopeContext.set(101L, "receivable-payable", "self", java.util.Set.of(101L));

            var result = pageUnreconciled();

            assertThat(result).singleElement().satisfies(row -> {
                assertThat(row.id()).isEqualTo("应付:供应商:未对账:" + SETTLEMENT_COMPANY_ID + ":" + SUPPLIER_ID);
                assertThat(row.counterpartyId()).isEqualTo(SUPPLIER_ID);
                assertThat(row.recognizedAmount()).isEqualByComparingTo("114523.50");
                assertThat(row.settledAmount()).isEqualByComparingTo("114523.50");
                assertThat(row.balanceAmount()).isEqualByComparingTo("0.00");
                assertThat(row.entryCount()).isEqualTo(3L);
                assertThat(row.status()).isEqualTo("已结清");
            });
            assertThat(detailUnreconciled())
                    .extracting(item -> item.entryRole() + ":" + item.sourceType())
                    .containsExactlyInAnyOrder(
                            "RECOGNITION:采购入库单",
                            "SETTLEMENT:采购预付款",
                            "SETTLEMENT_REVERSAL:供应商退款到账单"
                    );
        } finally {
            DataScopeContext.clear();
        }
    }

    @Test
    void shouldSplitPurchasePrepaymentIntoReconciledAllocationAndUnreconciledRemainder() {
        insertPurchasePrepayment();
        insertConfirmedSupplierStatement();
        insertPaymentAllocation("70000.00");

        var result = repository.page(
                PageQuery.of(0, 20, "reconciliationStatus", "asc"),
                "应付", "供应商", SETTLEMENT_COMPANY_ID, null, null, SUPPLIER_CODE
        );

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .filteredOn(row -> "已对账".equals(row.reconciliationStatus()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.recognizedAmount()).isEqualByComparingTo("114523.50");
                    assertThat(row.settledAmount()).isEqualByComparingTo("70000.00");
                    assertThat(row.entryCount()).isEqualTo(2L);
                });
        assertThat(result.getContent())
                .filteredOn(row -> "未对账".equals(row.reconciliationStatus()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.recognizedAmount()).isEqualByComparingTo("0.00");
                    assertThat(row.settledAmount()).isEqualByComparingTo("47000.00");
                    assertThat(row.entryCount()).isEqualTo(1L);
                });
        assertThat(result.getContent().stream()
                .map(ReceivablePayableResponse::settledAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
                .isEqualByComparingTo("117000.00");
        assertThat(repository.detailItems(
                "应付", "供应商", SUPPLIER_ID,
                SETTLEMENT_COMPANY_ID, "已对账"
        )).filteredOn(item -> "采购预付款".equals(item.sourceType()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.sourceNo()).isEqualTo("TEST-REFUND-SS");
                    assertThat(item.debitAmount()).isEqualByComparingTo("70000.00");
                });
        assertThat(detailUnreconciled())
                .singleElement()
                .satisfies(item -> assertThat(item.debitAmount()).isEqualByComparingTo("47000.00"));
    }

    @Test
    void shouldOmitZeroUnreconciledPrepaymentEventWhenFullyAllocated() {
        insertPurchasePrepayment();
        insertConfirmedSupplierStatement();
        insertPaymentAllocation("117000.00");

        var result = repository.page(
                PageQuery.of(0, 20, "reconciliationStatus", "asc"),
                "应付", "供应商", SETTLEMENT_COMPANY_ID, null, null, SUPPLIER_CODE
        );

        assertThat(result.getContent()).singleElement().satisfies(row -> {
            assertThat(row.reconciliationStatus()).isEqualTo("已对账");
            assertThat(row.recognizedAmount()).isEqualByComparingTo("114523.50");
            assertThat(row.settledAmount()).isEqualByComparingTo("117000.00");
            assertThat(row.entryCount()).isEqualTo(2L);
        });
        assertThat(repository.detailItems(
                "应付", "供应商", SUPPLIER_ID,
                SETTLEMENT_COMPANY_ID, "未对账"
        )).isEmpty();
    }

    @Test
    void shouldIgnoreDraftSupplierRefundReceipt() {
        insertRefund("已审核");
        insertPurchasePrepayment();
        insertSupplierRefundReceipt("草稿");

        var result = pageUnreconciled();

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.recognizedAmount()).isEqualByComparingTo("114523.50");
            assertThat(row.settledAmount()).isEqualByComparingTo("117000.00");
            assertThat(row.balanceAmount()).isEqualByComparingTo("-2476.50");
            assertThat(row.entryCount()).isEqualTo(2L);
        });
        assertThat(detailUnreconciled())
                .extracting(item -> item.sourceType())
                .doesNotContain("供应商退款到账单");
    }

    @Test
    void shouldKeepPrepaymentAndRefundReceiptUnreconciledAfterInboundStatementConfirmation() {
        insertRefund("已审核");
        insertPurchasePrepayment();
        insertSupplierRefundReceipt("已收款");
        insertConfirmedSupplierStatement();

        var result = repository.page(
                PageQuery.of(0, 20, "reconciliationStatus", "asc"),
                "应付",
                "供应商",
                SETTLEMENT_COMPANY_ID,
                null,
                null,
                SUPPLIER_CODE
        );

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .filteredOn(row -> "已对账".equals(row.reconciliationStatus()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.recognizedAmount()).isEqualByComparingTo("114523.50");
                    assertThat(row.settledAmount()).isEqualByComparingTo("0.00");
                    assertThat(row.balanceAmount()).isEqualByComparingTo("114523.50");
                });
        assertThat(result.getContent())
                .filteredOn(row -> "未对账".equals(row.reconciliationStatus()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.recognizedAmount()).isEqualByComparingTo("0.00");
                    assertThat(row.settledAmount()).isEqualByComparingTo("114523.50");
                    assertThat(row.balanceAmount()).isEqualByComparingTo("-114523.50");
                });
    }

    @Test
    void shouldNotOffsetPurchasePrepaymentAcrossSettlementCompanies() {
        insertRefund("已审核");
        insertPurchasePrepayment();
        insertSupplierRefundReceipt("已收款");
        insertStandaloneInboundForOtherCompany();

        var result = repository.page(
                PageQuery.of(0, 20, "counterpartyName", "asc"),
                "应付", "供应商", null, "未对账", null, SUPPLIER_CODE
        );

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .filteredOn(row -> SETTLEMENT_COMPANY_ID == row.settlementCompanyId())
                .singleElement()
                .satisfies(row -> assertThat(row.balanceAmount()).isEqualByComparingTo("0.00"));
        assertThat(result.getContent())
                .filteredOn(row -> SETTLEMENT_COMPANY_ID + 1 == row.settlementCompanyId())
                .singleElement()
                .satisfies(row -> assertThat(row.balanceAmount()).isEqualByComparingTo("200.00"));
    }

    private org.springframework.data.domain.Page<ReceivablePayableResponse> pageUnreconciled() {
        return repository.page(
                PageQuery.of(0, 20, "counterpartyName", "asc"),
                "应付",
                "供应商",
                SETTLEMENT_COMPANY_ID,
                "未对账",
                null,
                SUPPLIER_CODE
        );
    }

    private java.util.List<com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableDetailItemResponse> detailUnreconciled() {
        return repository.detailItems(
                "应付",
                "供应商",
                SUPPLIER_ID,
                SETTLEMENT_COMPANY_ID,
                "未对账"
        );
    }

    private void insertSupplier() {
        jdbcTemplate.update("""
                INSERT INTO md_supplier (id, supplier_code, supplier_name, status, deleted_flag)
                VALUES (?, ?, ?, '正常', FALSE)
                """, BASE_ID, SUPPLIER_CODE, SUPPLIER_NAME);
    }

    private void insertPurchaseOrder() {
        jdbcTemplate.update("""
                INSERT INTO po_purchase_order (
                    id, order_no, supplier_id, supplier_code, supplier_name, order_date,
                    settlement_company_id, settlement_company_name,
                    total_weight, total_amount, status, deleted_flag, created_by
                ) VALUES (?, 'TEST-REFUND-PO', ?, ?, ?, TIMESTAMP '2026-07-10 10:00:00', ?, ?,
                          36, 117000, '已审核', FALSE, 0)
                """, BASE_ID + 1, SUPPLIER_ID, SUPPLIER_CODE, SUPPLIER_NAME,
                SETTLEMENT_COMPANY_ID, SETTLEMENT_COMPANY_NAME);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_order_item (
                    id, order_id, line_no, material_id, material_code, brand, category, material, spec, unit,
                    quantity, quantity_unit, piece_weight_ton, pieces_per_bundle, weight_ton,
                    unit_price, amount, warehouse_id
                ) VALUES (?, ?, 1, ?, 'TEST-REFUND-MATERIAL', '新澎辉', '盘螺', 'HRB400E', '8', '吨',
                          18, '件', 2, 1, 36, 3250, 117000, ?)
                """, BASE_ID + 2, BASE_ID + 1, MATERIAL_ID, WAREHOUSE_ID);
    }

    private void insertInbound() {
        jdbcTemplate.update("""
                INSERT INTO po_purchase_inbound (
                    id, inbound_no, purchase_order_no, supplier_id, supplier_code, supplier_name,
                    warehouse_id, warehouse_name, inbound_date,
                    settlement_company_id, settlement_company_name,
                    settlement_mode, total_weight, total_amount, status, deleted_flag, created_by
                ) VALUES (?, 'TEST-REFUND-PI', 'TEST-REFUND-PO', ?, ?, ?, ?, '退款测试仓',
                          TIMESTAMP '2026-07-10 11:00:00', ?, ?, '过磅', 35.238, 114523.50,
                          '已审核', FALSE, 101)
                """, BASE_ID + 3, SUPPLIER_ID, SUPPLIER_CODE, SUPPLIER_NAME,
                WAREHOUSE_ID, SETTLEMENT_COMPANY_ID, SETTLEMENT_COMPANY_NAME);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_inbound_item (
                    id, inbound_id, line_no, material_id, material_code, brand, category, material, spec, unit,
                    quantity, quantity_unit, piece_weight_ton, pieces_per_bundle, weight_ton,
                    weigh_weight_ton, weight_adjustment_ton, weight_adjustment_amount,
                    unit_price, amount, warehouse_id, warehouse_name, source_purchase_order_item_id, settlement_mode
                ) VALUES (?, ?, 1, ?, 'TEST-REFUND-MATERIAL', '新澎辉', '盘螺', 'HRB400E', '8', '吨',
                          18, '件', 2, 1, 36, 35.238, -0.762, -2476.50,
                          3250, 117000, ?, '退款测试仓', ?, '过磅')
                """, BASE_ID + 4, BASE_ID + 3, MATERIAL_ID, WAREHOUSE_ID, BASE_ID + 2);
    }

    private void insertStandaloneInboundForOtherCompany() {
        jdbcTemplate.update("""
                INSERT INTO po_purchase_inbound (
                    id, inbound_no, supplier_id, supplier_code, supplier_name,
                    warehouse_id, warehouse_name, inbound_date,
                    settlement_company_id, settlement_company_name,
                    settlement_mode, total_weight, total_amount, status, deleted_flag, created_by
                ) VALUES (?, 'TEST-REFUND-PI-B', ?, ?, ?, ?, '退款测试仓',
                          TIMESTAMP '2026-07-10 12:00:00', ?, '采购结算主体乙',
                          '理计', 1, 200, '已审核', FALSE, 0)
                """, BASE_ID + 20, SUPPLIER_ID, SUPPLIER_CODE, SUPPLIER_NAME,
                WAREHOUSE_ID, SETTLEMENT_COMPANY_ID + 1);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_inbound_item (
                    id, inbound_id, line_no, material_id, material_code, brand, category, material, spec, unit,
                    quantity, quantity_unit, piece_weight_ton, pieces_per_bundle, weight_ton,
                    weigh_weight_ton, weight_adjustment_ton, weight_adjustment_amount,
                    unit_price, amount, warehouse_id, warehouse_name,
                    source_purchase_order_item_id, settlement_mode
                ) VALUES (?, ?, 1, ?, 'TEST-REFUND-MATERIAL-B', '新澎辉', '盘螺', 'HRB400E', '10', '吨',
                          1, '件', 1, 1, 1, 1, 0, 0, 200, 200, ?, '退款测试仓', ?, '理计')
                """, BASE_ID + 21, BASE_ID + 20, MATERIAL_ID, WAREHOUSE_ID, BASE_ID + 2);
    }

    private void insertRefund(String status) {
        jdbcTemplate.update("""
                INSERT INTO po_purchase_refund (
                    id, version, refund_no, source_purchase_order_id, purchase_order_no,
                    supplier_id, supplier_code, supplier_name, settlement_company_id, settlement_company_name,
                    refund_date, total_quantity, total_weight, total_amount,
                    status, operator_name, deleted_flag, created_by
                ) VALUES (?, 0, 'TEST-REFUND-PR', ?, 'TEST-REFUND-PO', ?, ?, ?, ?, ?, DATE '2026-07-10',
                          0, 0.762, 2476.50, ?, '测试员', FALSE, 0)
                """, BASE_ID + 5, BASE_ID + 1, SUPPLIER_ID, SUPPLIER_CODE, SUPPLIER_NAME,
                SETTLEMENT_COMPANY_ID, SETTLEMENT_COMPANY_NAME, status);
    }

    private void insertPurchasePrepayment() {
        jdbcTemplate.update("""
                INSERT INTO fm_payment (
                    id, payment_no, business_type, counterparty_type, counterparty_id,
                    counterparty_code, counterparty_name,
                    payment_purpose, source_purchase_order_id, purchase_order_no,
                    supplier_code, supplier_name, settlement_company_id, settlement_company_name,
                    payment_date, pay_type, amount, status, operator_name, deleted_flag, created_by
                ) VALUES (?, 'TEST-REFUND-PAY', '供应商', '供应商', ?, ?, ?, 'PURCHASE_PREPAYMENT', ?,
                          'TEST-REFUND-PO', ?, ?, ?, ?, TIMESTAMP '2026-07-09 10:00:00', '银行转账',
                          117000, '已付款', '测试员', FALSE, 202)
                """, BASE_ID + 8, SUPPLIER_ID, SUPPLIER_CODE, SUPPLIER_NAME, BASE_ID + 1,
                SUPPLIER_CODE, SUPPLIER_NAME, SETTLEMENT_COMPANY_ID, SETTLEMENT_COMPANY_NAME);
    }

    private void insertSupplierRefundReceipt(String status) {
        jdbcTemplate.update("""
                INSERT INTO fm_supplier_refund_receipt (
                    id, version, refund_receipt_no, purchase_refund_id,
                    supplier_id, supplier_code, supplier_name, settlement_company_id, settlement_company_name,
                    receipt_date, receipt_method, amount, status, operator_name, deleted_flag, created_by
                ) VALUES (?, 0, 'TEST-REFUND-RR', ?, ?, ?, ?, ?, ?, DATE '2026-07-11',
                          '银行转账', 2476.50, ?, '测试员', FALSE, 303)
                """, BASE_ID + 9, BASE_ID + 5, SUPPLIER_ID, SUPPLIER_CODE, SUPPLIER_NAME,
                SETTLEMENT_COMPANY_ID, SETTLEMENT_COMPANY_NAME, status);
    }

    private void insertConfirmedSupplierStatement() {
        jdbcTemplate.update("""
                INSERT INTO st_supplier_statement (
                    id, statement_no, supplier_id, supplier_code, supplier_name,
                    settlement_company_id, settlement_company_name,
                    start_date, end_date, purchase_amount, payment_amount,
                    closing_amount, status, deleted_flag
                ) VALUES (?, 'TEST-REFUND-SS', ?, ?, ?, ?, ?, TIMESTAMP '2026-07-01 00:00:00',
                          TIMESTAMP '2026-07-31 23:59:59', 114523.50, 0, 114523.50, '已确认', FALSE)
                """, BASE_ID + 6, SUPPLIER_ID, SUPPLIER_CODE, SUPPLIER_NAME,
                SETTLEMENT_COMPANY_ID, SETTLEMENT_COMPANY_NAME);
        jdbcTemplate.update("""
                INSERT INTO st_supplier_statement_item (
                    id, statement_id, line_no, source_no, source_inbound_item_id,
                    material_id, material_code, brand, category, material, spec, unit,
                    quantity, quantity_unit, piece_weight_ton, pieces_per_bundle,
                    weight_ton, weigh_weight_ton, weight_adjustment_ton,
                    weight_adjustment_amount, unit_price, amount, warehouse_id
                ) VALUES (?, ?, 1, 'TEST-REFUND-PI', ?, ?, 'TEST-REFUND-MATERIAL',
                          '新澎辉', '盘螺', 'HRB400E', '8', '吨', 18, '件', 2, 1,
                          36, 35.238, -0.762, -2476.50, 3250, 117000, ?)
                """, BASE_ID + 7, BASE_ID + 6, BASE_ID + 4, MATERIAL_ID, WAREHOUSE_ID);
    }

    private void insertPaymentAllocation(String allocatedAmount) {
        jdbcTemplate.update("""
                INSERT INTO fm_payment_allocation (
                    id, payment_id, line_no, source_statement_id,
                    source_supplier_statement_id, allocated_amount
                ) VALUES (?, ?, 1, ?, ?, ?)
                """, BASE_ID + 10, BASE_ID + 8, BASE_ID + 6, BASE_ID + 6,
                new java.math.BigDecimal(allocatedAmount));
    }
}
