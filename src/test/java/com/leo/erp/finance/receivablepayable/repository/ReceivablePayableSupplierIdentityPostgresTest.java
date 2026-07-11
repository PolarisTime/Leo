package com.leo.erp.finance.receivablepayable.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=false")
class ReceivablePayableSupplierIdentityPostgresTest {

    private static final long BASE_ID = 8_760_000_000_000_000_000L;
    private static final String SUPPLIER_CODE = "TEST-PAYABLE-SUPPLIER";
    private static final long COMPANY_A_ID = BASE_ID + 100;
    private static final long COMPANY_B_ID = BASE_ID + 101;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ReceivablePayableQueryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ReceivablePayableQueryRepository(new NamedParameterJdbcTemplate(jdbcTemplate));
        ReceivablePayablePostgresTestSchemaSupport.preparePurchaseLedgerSchema(jdbcTemplate);
    }

    @Test
    void shouldMergeHistoricalSupplierNamesByCodeAndDisplayLatestBusinessSnapshot() {
        insertInbound(BASE_ID, "TEST-PAYABLE-OLD", SUPPLIER_CODE, "供应商旧名称",
                COMPANY_A_ID, "结算主体甲", LocalDateTime.of(2026, 7, 9, 10, 0), new BigDecimal("100.00"));
        insertInbound(BASE_ID + 2, "TEST-PAYABLE-NEW", SUPPLIER_CODE, "供应商新名称",
                COMPANY_A_ID, "结算主体甲", LocalDateTime.of(2026, 7, 10, 10, 0), new BigDecimal("200.00"));

        var result = repository.page(
                PageQuery.of(0, 20, "counterpartyName", "asc"),
                "应付",
                "供应商",
                COMPANY_A_ID,
                "未对账",
                null,
                SUPPLIER_CODE
        );

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo("应付:供应商:未对账:" + COMPANY_A_ID + ":" + SUPPLIER_CODE);
            assertThat(row.counterpartyCode()).isEqualTo(SUPPLIER_CODE);
            assertThat(row.counterpartyName()).isEqualTo("供应商新名称");
            assertThat(row.settlementCompanyId()).isEqualTo(COMPANY_A_ID);
            assertThat(row.settlementCompanyName()).isEqualTo("结算主体甲");
            assertThat(row.recognizedAmount()).isEqualByComparingTo("300.00");
            assertThat(row.balanceAmount()).isEqualByComparingTo("300.00");
            assertThat(row.entryCount()).isEqualTo(2L);
        });
    }

    @Test
    void shouldKeepSameNameSuppliersSeparateWhenCodesDiffer() {
        insertInbound(BASE_ID + 4, "TEST-PAYABLE-A", SUPPLIER_CODE + "-A", "同名供应商",
                COMPANY_A_ID, "结算主体甲", LocalDateTime.of(2026, 7, 9, 10, 0), new BigDecimal("100.00"));
        insertInbound(BASE_ID + 6, "TEST-PAYABLE-B", SUPPLIER_CODE + "-B", "同名供应商",
                COMPANY_A_ID, "结算主体甲", LocalDateTime.of(2026, 7, 10, 10, 0), new BigDecimal("200.00"));

        var result = repository.page(
                PageQuery.of(0, 20, "counterpartyCode", "asc"),
                "应付",
                "供应商",
                COMPANY_A_ID,
                "未对账",
                null,
                SUPPLIER_CODE + "-"
        );

        assertThat(result.getContent())
                .extracting(ReceivablePayableResponse::counterpartyCode)
                .containsExactly(SUPPLIER_CODE + "-A", SUPPLIER_CODE + "-B");
        assertThat(result.getContent())
                .extracting(ReceivablePayableResponse::id)
                .doesNotHaveDuplicates();
    }

    @Test
    void shouldKeepSameSupplierSeparateAcrossSettlementCompanies() {
        insertInbound(BASE_ID + 8, "TEST-PAYABLE-COMPANY-A", SUPPLIER_CODE, "同一供应商",
                COMPANY_A_ID, "结算主体甲", LocalDateTime.of(2026, 7, 9, 10, 0), new BigDecimal("100.00"));
        insertInbound(BASE_ID + 10, "TEST-PAYABLE-COMPANY-B", SUPPLIER_CODE, "同一供应商",
                COMPANY_B_ID, "结算主体乙", LocalDateTime.of(2026, 7, 10, 10, 0), new BigDecimal("200.00"));

        var result = repository.page(
                PageQuery.of(0, 20, "counterpartyName", "asc"),
                "应付", "供应商", null, "未对账", null, SUPPLIER_CODE
        );

        assertThat(result.getContent())
                .extracting(ReceivablePayableResponse::settlementCompanyId)
                .containsExactlyInAnyOrder(COMPANY_A_ID, COMPANY_B_ID);
        assertThat(result.getContent())
                .extracting(ReceivablePayableResponse::balanceAmount)
                .containsExactlyInAnyOrder(new BigDecimal("100.00"), new BigDecimal("200.00"));
        assertThat(result.getContent())
                .extracting(ReceivablePayableResponse::id)
                .doesNotHaveDuplicates();
    }

    @Test
    void shouldKeepHistoricalNullSettlementCompanySeparateFromConcreteCompany() {
        insertInbound(BASE_ID + 12, "TEST-PAYABLE-COMPANY-NONE", SUPPLIER_CODE, "同一供应商",
                null, null, LocalDateTime.of(2026, 7, 9, 10, 0), new BigDecimal("100.00"));
        insertInbound(BASE_ID + 14, "TEST-PAYABLE-COMPANY-SET", SUPPLIER_CODE, "同一供应商",
                COMPANY_A_ID, "结算主体甲", LocalDateTime.of(2026, 7, 10, 10, 0), new BigDecimal("200.00"));

        var result = repository.page(
                PageQuery.of(0, 20, "counterpartyName", "asc"),
                "应付", "供应商", null, "未对账", null, SUPPLIER_CODE
        );

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(ReceivablePayableResponse::id)
                .containsExactlyInAnyOrder(
                        "应付:供应商:未对账:none:" + SUPPLIER_CODE,
                        "应付:供应商:未对账:" + COMPANY_A_ID + ":" + SUPPLIER_CODE
                );
    }

    private void insertInbound(long inboundId,
                               String inboundNo,
                               String supplierCode,
                               String supplierName,
                               Long settlementCompanyId,
                               String settlementCompanyName,
                               LocalDateTime inboundDate,
                               BigDecimal amount) {
        jdbcTemplate.update("""
                INSERT INTO po_purchase_inbound (
                    id, inbound_no, supplier_code, supplier_name, warehouse_name, inbound_date,
                    settlement_company_id, settlement_company_name,
                    settlement_mode, total_weight, total_amount, status, deleted_flag, created_by
                ) VALUES (?, ?, ?, ?, '稳定身份测试仓', ?, ?, ?, '理计', 1, ?, '已审核', FALSE, 0)
                """, inboundId, inboundNo, supplierCode, supplierName, inboundDate,
                settlementCompanyId, settlementCompanyName, amount);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_inbound_item (
                    id, inbound_id, line_no, material_code, brand, category, material, spec, unit,
                    quantity, quantity_unit, piece_weight_ton, pieces_per_bundle, weight_ton,
                    weigh_weight_ton, weight_adjustment_ton, weight_adjustment_amount,
                    unit_price, amount, warehouse_name, settlement_mode
                ) VALUES (?, ?, 1, 'TEST-PAYABLE-MATERIAL', '测试品牌', '测试品类', '测试材质', '1', '吨',
                          1, '件', 1, 1, 1, 1, 0, 0, ?, ?, '稳定身份测试仓', '理计')
                """, inboundId + 1, inboundId, amount, amount);
    }
}
