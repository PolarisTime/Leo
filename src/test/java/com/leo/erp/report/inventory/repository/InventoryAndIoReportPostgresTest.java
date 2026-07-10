package com.leo.erp.report.inventory.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.report.inventory.web.dto.InventoryReportResponse;
import com.leo.erp.report.io.repository.IoReportQueryRepository;
import com.leo.erp.report.io.web.dto.IoReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class InventoryAndIoReportPostgresTest {

    private static final long BASE_ID = 8_700_000_000_000_000_000L;
    private static final String MATERIAL_CODE = "TEST-INV-STATUS-MATRIX";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private InventoryReportQueryRepository inventoryRepository;
    private IoReportQueryRepository ioRepository;

    @BeforeEach
    void setUp() {
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        inventoryRepository = new InventoryReportQueryRepository(namedJdbc);
        ioRepository = new IoReportQueryRepository(namedJdbc);
    }

    @Test
    void shouldApplyEffectiveAndReservedStatusMatrixInPostgres() {
        insertInbound(1, "草稿", false, 100, new BigDecimal("100.000"), "草稿仓");
        insertInbound(2, "已审核", false, 10, new BigDecimal("10.000"), "一号仓");
        insertInbound(3, "完成入库", false, 5, new BigDecimal("5.000"), "二号仓");
        insertInbound(4, "已审核", true, 99, new BigDecimal("99.000"), "删除仓");

        insertOutbound(11, "草稿", false, 50, new BigDecimal("50.000"), "草稿头仓", "草稿明细仓");
        insertOutbound(12, "预出库", false, 4, new BigDecimal("4.000"), "预留头仓", "一号仓");
        insertOutbound(13, "已审核", false, 3, new BigDecimal("3.000"), "错误头仓", "二号仓");
        insertOutbound(14, "已审核", true, 99, new BigDecimal("99.000"), "删除头仓", "删除明细仓");

        Page<InventoryReportResponse> inventory = inventoryRepository.page(
                new PageQuery(0, 20, null, null), MATERIAL_CODE, null, null, false);

        assertThat(inventory).singleElement().satisfies(row -> {
            assertThat(row.onHandQuantity()).isEqualTo(12);
            assertThat(row.reservedQuantity()).isEqualTo(4);
            assertThat(row.availableQuantity()).isEqualTo(8);
            assertThat(row.quantity()).isEqualTo(row.onHandQuantity());
            assertThat(row.onHandWeightTon()).isEqualByComparingTo("12.000");
            assertThat(row.reservedWeightTon()).isEqualByComparingTo("4.000");
            assertThat(row.availableWeightTon()).isEqualByComparingTo("8.000");
            assertThat(row.weightTon()).isEqualByComparingTo(row.onHandWeightTon());
        });

        Page<IoReportResponse> movements = ioRepository.page(
                new PageQuery(0, 20, null, null), MATERIAL_CODE, null, null, null);

        assertThat(movements.getTotalElements()).isEqualTo(3);
        assertThat(movements).filteredOn(row -> "采购入库".equals(row.businessType())).hasSize(2);
        assertThat(movements).filteredOn(row -> "销售出库".equals(row.businessType()))
                .singleElement()
                .satisfies(row -> assertThat(row.warehouseName()).isEqualTo("二号仓"));
    }

    private void insertInbound(long offset, String status, boolean deleted, int quantity,
                               BigDecimal weight, String warehouseName) {
        long inboundId = BASE_ID + offset;
        jdbcTemplate.update("""
                INSERT INTO po_purchase_inbound (
                    id, inbound_no, supplier_name, warehouse_name, inbound_date, settlement_mode,
                    total_weight, total_amount, status, deleted_flag
                ) VALUES (?, ?, '测试供应商', ?, CURRENT_DATE, '按重量', ?, 0, ?, ?)
                """, inboundId, "TEST-IN-" + offset, warehouseName, weight, status, deleted);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_inbound_item (
                    id, inbound_id, line_no, material_code, brand, category, material, spec, unit,
                    quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount,
                    warehouse_name, settlement_mode
                ) VALUES (?, ?, 1, ?, '测试品牌', '测试类别', '测试材质', 'TEST-SPEC', '吨',
                          ?, 1, 1, ?, 0, 0, ?, '按重量')
                """, BASE_ID + 100 + offset, inboundId, MATERIAL_CODE, quantity, weight, warehouseName);
    }

    private void insertOutbound(long offset, String status, boolean deleted, int quantity,
                                BigDecimal weight, String headerWarehouse, String itemWarehouse) {
        long outboundId = BASE_ID + offset;
        jdbcTemplate.update("""
                INSERT INTO so_sales_outbound (
                    id, outbound_no, customer_name, project_name, warehouse_name, outbound_date,
                    total_weight, total_amount, status, deleted_flag
                ) VALUES (?, ?, '测试客户', '测试项目', ?, CURRENT_DATE, ?, 0, ?, ?)
                """, outboundId, "TEST-OUT-" + offset, headerWarehouse, weight, status, deleted);
        jdbcTemplate.update("""
                INSERT INTO so_sales_outbound_item (
                    id, outbound_id, line_no, material_code, brand, category, material, spec, unit,
                    quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount,
                    warehouse_name
                ) VALUES (?, ?, 1, ?, '测试品牌', '测试类别', '测试材质', 'TEST-SPEC', '吨',
                          ?, 1, 1, ?, 0, 0, ?)
                """, BASE_ID + 100 + offset, outboundId, MATERIAL_CODE, quantity, weight, itemWarehouse);
    }
}
