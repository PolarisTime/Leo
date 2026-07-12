package com.leo.erp.report.pendinginvoicereceipt.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.report.pendinginvoicereceipt.web.dto.PendingInvoiceReceiptReportResponse;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.testsupport.StableIdentityPostgresFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=false")
class PendingInvoiceReceiptReportPostgresTest {

    private static final long BASE_ID = 8_600_000_000_000_000_000L;
    private static final String MATRIX_CODE = "TEST-PENDING-MATRIX";
    private static final String SUPPLIER_CODE = "TEST-PENDING-SUPPLIER";
    private static final long SUPPLIER_ID = BASE_ID + 50_001;
    private static final long SETTLEMENT_COMPANY_ID = BASE_ID + 50_002;
    private static final long MATERIAL_ID_BASE = BASE_ID + 60_000;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private PendingInvoiceReceiptReportQueryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PendingInvoiceReceiptReportQueryRepository(
                new NamedParameterJdbcTemplate(jdbcTemplate));
        StableIdentityPostgresFixtures.insertSupplier(
                jdbcTemplate,
                SUPPLIER_ID,
                SUPPLIER_CODE,
                "测试供应商"
        );
        StableIdentityPostgresFixtures.insertSettlementCompany(
                jdbcTemplate,
                SETTLEMENT_COMPANY_ID,
                "测试结算主体"
        );
    }

    @AfterEach
    void clearDataScope() {
        DataScopeContext.clear();
    }

    @Test
    void shouldAggregateOnlyEffectiveOrdersAndReceivedInvoices() {
        insertPurchaseOrder(1, "草稿", 10, "100.00", 11L, MATRIX_CODE + "-DRAFT");
        insertPurchaseOrder(2, "已审核", 10, "100.00", 11L, MATRIX_CODE + "-AUDITED");
        insertPurchaseOrder(3, "完成采购", 5, "50.00", 11L, MATRIX_CODE + "-COMPLETED");
        insertPurchaseOrder(4, "已审核", 2, "20.00", 12L, MATRIX_CODE + "-OTHER-OWNER");

        insertInvoiceReceipt(21, "草稿", false, 2, 4, "40.00");
        insertInvoiceReceipt(22, "已收票", false, 2, 3, "30.00");
        insertInvoiceReceipt(23, "已收票", true, 2, 2, "20.00");
        insertInvoiceReceipt(24, "已收票", false, 3, 5, "20.00");

        DataScopeContext.set(11L, "pending-invoice-receipt-report", "self");
        Page<PendingInvoiceReceiptReportResponse> result = repository.page(
                PageQuery.of(0, 20, "orderNo", "asc"), MATRIX_CODE, null, null, null);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result).filteredOn(row -> row.materialCode().endsWith("AUDITED"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.invoiceTitle()).isEqualTo("测试结算主体");
                    assertThat(row.receivedInvoiceWeightTon()).isEqualByComparingTo("3.00000000");
                    assertThat(row.pendingInvoiceWeightTon()).isEqualByComparingTo("7.00000000");
                    assertThat(row.receivedInvoiceAmount()).isEqualByComparingTo("30.00");
                    assertThat(row.pendingInvoiceAmount()).isEqualByComparingTo("70.00");
                });
        assertThat(result).filteredOn(row -> row.materialCode().endsWith("COMPLETED"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.pendingInvoiceWeightTon()).isEqualByComparingTo("0.00000000");
                    assertThat(row.pendingInvoiceAmount()).isEqualByComparingTo("30.00");
                });
        assertThat(result).noneMatch(row -> row.materialCode().endsWith("DRAFT"));
        assertThat(result).noneMatch(row -> row.materialCode().endsWith("OTHER-OWNER"));
    }

    @Test
    void shouldSubtractOnlyAuditedPurchaseRefundsFromPendingInvoiceBalance() {
        insertPurchaseOrder(5, "已审核", 10, "100.00", 11L, MATRIX_CODE + "-AUDITED-REFUND");
        insertPurchaseOrder(6, "已审核", 10, "100.00", 11L, MATRIX_CODE + "-DRAFT-REFUND");
        insertPurchaseOrder(7, "已审核", 10, "100.00", 11L, MATRIX_CODE + "-DELETED-REFUND");
        insertInvoiceReceipt(25, "已收票", false, 5, 3, "30.00");
        insertInvoiceReceipt(26, "已收票", false, 6, 3, "30.00");
        insertInvoiceReceipt(27, "已收票", false, 7, 3, "30.00");
        insertPurchaseRefund(35, "已审核", false, 5, 4, "40.00");
        insertPurchaseRefund(36, "草稿", false, 6, 4, "40.00");
        insertPurchaseRefund(37, "已审核", true, 7, 4, "40.00");

        DataScopeContext.set(11L, "pending-invoice-receipt-report", "self");
        Page<PendingInvoiceReceiptReportResponse> result = repository.page(
                PageQuery.of(0, 20, "orderNo", "asc"), MATRIX_CODE, null, null, null);

        assertThat(result).filteredOn(row -> row.materialCode().endsWith("AUDITED-REFUND"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.receivedInvoiceQuantity()).isEqualTo(3);
                    assertThat(row.refundedQuantity()).isEqualTo(4);
                    assertThat(row.pendingInvoiceQuantity()).isEqualTo(3);
                    assertThat(row.refundedWeightTon()).isEqualByComparingTo("4.00000000");
                    assertThat(row.pendingInvoiceWeightTon()).isEqualByComparingTo("3.00000000");
                    assertThat(row.refundedAmount()).isEqualByComparingTo("40.00");
                    assertThat(row.pendingInvoiceAmount()).isEqualByComparingTo("30.00");
                });
        assertThat(result).filteredOn(row -> row.materialCode().endsWith("DRAFT-REFUND"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.refundedQuantity()).isZero();
                    assertThat(row.pendingInvoiceQuantity()).isEqualTo(7);
                    assertThat(row.refundedWeightTon()).isEqualByComparingTo("0");
                    assertThat(row.pendingInvoiceWeightTon()).isEqualByComparingTo("7.00000000");
                    assertThat(row.refundedAmount()).isEqualByComparingTo("0");
                    assertThat(row.pendingInvoiceAmount()).isEqualByComparingTo("70.00");
                });
        assertThat(result).filteredOn(row -> row.materialCode().endsWith("DELETED-REFUND"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.refundedQuantity()).isZero();
                    assertThat(row.pendingInvoiceQuantity()).isEqualTo(7);
                    assertThat(row.refundedWeightTon()).isEqualByComparingTo("0");
                    assertThat(row.pendingInvoiceWeightTon()).isEqualByComparingTo("7.00000000");
                    assertThat(row.refundedAmount()).isEqualByComparingTo("0");
                    assertThat(row.pendingInvoiceAmount()).isEqualByComparingTo("70.00");
                });
    }

    @Test
    void shouldRebuildOriginalOrderCapacityWhenStoredTotalsWereWrittenBack() {
        insertPurchaseOrder(8, "已审核", 10, "100.00", 11L, MATRIX_CODE + "-ORIGINAL-BASIS");
        jdbcTemplate.update("""
                UPDATE po_purchase_order_item
                SET weight_ton = 7,
                    amount = 70
                WHERE id = ?
                """, BASE_ID + 108);

        DataScopeContext.set(11L, "pending-invoice-receipt-report", "self");
        Page<PendingInvoiceReceiptReportResponse> result = repository.page(
                PageQuery.of(0, 20, "orderNo", "asc"),
                MATRIX_CODE + "-ORIGINAL-BASIS",
                null,
                null,
                null
        );

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.orderWeightTon()).isEqualByComparingTo("10.00000000");
            assertThat(row.pendingInvoiceWeightTon()).isEqualByComparingTo("10.00000000");
            assertThat(row.orderAmount()).isEqualByComparingTo("100.00");
            assertThat(row.pendingInvoiceAmount()).isEqualByComparingTo("100.00");
        });
    }

    @Test
    void shouldPageAllRowsBeyondFormerCandidateLimitWithoutDuplicates() {
        jdbcTemplate.update("""
                INSERT INTO md_material (
                    id, material_code, brand, material, category, spec, unit,
                    piece_weight_ton, pieces_per_bundle, unit_price, deleted_flag
                )
                SELECT ? + value,
                       'TEST-PENDING-PAGE-' || LPAD(value::text, 4, '0'),
                       '测试品牌', '测试材质', '测试类别',
                       'TEST-SPEC-' || LPAD(value::text, 4, '0'), '吨',
                       1, 1, 1, FALSE
                FROM generate_series(1, 1005) AS value
                """, MATERIAL_ID_BASE);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_order (
                    id, order_no, supplier_id, supplier_code, supplier_name,
                    order_date, total_weight, total_amount,
                    status, deleted_flag, created_by
                )
                SELECT ? + value,
                       'TEST-PENDING-PAGE-' || LPAD(value::text, 4, '0'),
                       ?, 'TEST-PENDING-SUPPLIER', '批量供应商',
                       TIMESTAMP '2026-01-01 00:00:00', 1, 1,
                       '已审核', FALSE, 77
                FROM generate_series(1, 1005) AS value
                """, BASE_ID + 10_000, SUPPLIER_ID);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_order_item (
                    id, order_id, line_no, material_id, material_code,
                    brand, category, material, spec, unit,
                    quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
                )
                SELECT ? + value, ? + value, 1, ? + value,
                       'TEST-PENDING-PAGE-' || LPAD(value::text, 4, '0'),
                       '测试品牌', '测试类别', '测试材质', 'TEST-SPEC', '吨',
                       1, 1, 1, 1, 1, 1
                FROM generate_series(1, 1005) AS value
                """, BASE_ID + 20_000, BASE_ID + 10_000, MATERIAL_ID_BASE);

        DataScopeContext.set(77L, "pending-invoice-receipt-report", "self");
        Set<Long> ids = new HashSet<>();
        long total = -1;
        for (int pageNumber = 0; pageNumber < 6; pageNumber++) {
            Page<PendingInvoiceReceiptReportResponse> page = repository.page(
                    PageQuery.of(pageNumber, 200, "orderDate", "asc"),
                    "TEST-PENDING-PAGE-", null, null, null);
            total = page.getTotalElements();
            page.forEach(row -> ids.add(row.id()));
        }

        assertThat(total).isEqualTo(1_005);
        assertThat(ids).hasSize(1_005);
    }

    private void insertPurchaseOrder(long offset, String status, int quantity, String amount,
                                     long createdBy, String materialCode) {
        long orderId = BASE_ID + offset;
        long materialId = MATERIAL_ID_BASE + offset;
        jdbcTemplate.update("""
                INSERT INTO md_material (
                    id, material_code, brand, material, category, spec, length, unit,
                    piece_weight_ton, pieces_per_bundle, unit_price, deleted_flag
                ) VALUES (?, ?, '测试品牌', '测试材质', '测试类别', 'TEST-SPEC', ?, '吨',
                          1, 1, 1, FALSE)
                """, materialId, materialCode, "PENDING-" + offset);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_order (
                    id, order_no, supplier_id, supplier_code, supplier_name,
                    order_date, total_weight, total_amount,
                    status, settlement_company_id, settlement_company_name, deleted_flag, created_by
                ) VALUES (?, ?, ?, ?, '测试供应商', TIMESTAMP '2026-03-01 10:00:00', ?, ?, ?,
                          ?, '测试结算主体', FALSE, ?)
                """, orderId, "TEST-PENDING-ORDER-" + offset, SUPPLIER_ID, SUPPLIER_CODE,
                quantity, new BigDecimal(amount), status, SETTLEMENT_COMPANY_ID, createdBy);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_order_item (
                    id, order_id, line_no, material_id, material_code,
                    brand, category, material, spec, unit,
                    quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
                ) VALUES (?, ?, 1, ?, ?, '测试品牌', '测试类别', '测试材质', 'TEST-SPEC', '吨',
                          ?, 1, 1, ?, 10, ?)
                """, BASE_ID + 100 + offset, orderId, materialId, materialCode,
                quantity, quantity, new BigDecimal(amount));
    }

    private void insertInvoiceReceipt(long offset, String status, boolean deleted,
                                      long sourceOrderOffset, int weight, String amount) {
        long receiptId = BASE_ID + 1_000 + offset;
        jdbcTemplate.update("""
                INSERT INTO fm_invoice_receipt (
                    id, receive_no, invoice_no, supplier_id, supplier_code, supplier_name,
                    invoice_date, invoice_type,
                    amount, tax_amount, status, operator_name, deleted_flag
                ) VALUES (?, ?, ?, ?, ?, '测试供应商', CURRENT_DATE, '增值税专票', ?, 0, ?, '测试员', ?)
                """, receiptId, "TEST-RECEIVE-" + offset, "TEST-INVOICE-" + offset,
                SUPPLIER_ID, SUPPLIER_CODE, new BigDecimal(amount), status, deleted);
        jdbcTemplate.update("""
                INSERT INTO fm_invoice_receipt_item (
                    id, receipt_id, line_no, source_no, source_purchase_order_item_id,
                    material_id, material_code, brand, category, material, spec, unit, quantity, quantity_unit,
                    piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
                ) VALUES (?, ?, 1, ?, ?, ?, ?, '测试品牌', '测试类别', '测试材质', 'TEST-SPEC', '吨',
                          ?, '件', 1, 1, ?, 10, ?)
                """, BASE_ID + 2_000 + offset, receiptId, "TEST-PENDING-ORDER-" + sourceOrderOffset,
                BASE_ID + 100 + sourceOrderOffset, MATERIAL_ID_BASE + sourceOrderOffset,
                MATRIX_CODE, weight, weight, new BigDecimal(amount));
    }

    private void insertPurchaseRefund(long offset, String status, boolean deleted, long sourceOrderOffset,
                                      int weight, String amount) {
        long refundId = BASE_ID + 3_000 + offset;
        jdbcTemplate.update("""
                INSERT INTO po_purchase_refund (
                    id, refund_no, source_purchase_order_id, purchase_order_no,
                    supplier_id, supplier_code, supplier_name, refund_date, total_quantity,
                    total_weight, total_amount, status, operator_name, deleted_flag
                ) VALUES (?, ?, ?, ?, ?, ?, '测试供应商', CURRENT_DATE,
                          ?, ?, ?, ?, '测试员', ?)
                """, refundId, "TEST-REFUND-" + offset, BASE_ID + sourceOrderOffset,
                "TEST-PENDING-ORDER-" + sourceOrderOffset, SUPPLIER_ID, SUPPLIER_CODE,
                weight, weight, new BigDecimal(amount), status, deleted);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_refund_item (
                    id, refund_id, source_purchase_order_item_id, line_no,
                    material_id, material_code, brand, category, material, spec, unit,
                    quantity, quantity_unit, piece_weight_ton, pieces_per_bundle,
                    weight_ton, unit_price, amount
                ) VALUES (?, ?, ?, 1, ?, ?, '测试品牌', '测试类别', '测试材质', 'TEST-SPEC', '吨',
                          ?, '件', 1, 1, ?, 10, ?)
                """, BASE_ID + 4_000 + offset, refundId, BASE_ID + 100 + sourceOrderOffset,
                MATERIAL_ID_BASE + sourceOrderOffset, MATRIX_CODE,
                weight, weight, new BigDecimal(amount));
    }
}
