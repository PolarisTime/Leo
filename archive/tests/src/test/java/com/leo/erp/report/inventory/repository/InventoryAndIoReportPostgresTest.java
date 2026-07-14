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
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class InventoryAndIoReportPostgresTest {

    private static final long BASE_ID = 8_700_000_000_000_000_000L;
    private static final long MATERIAL_ID = BASE_ID + 10_000;
    private static final long SUPPLIER_ID = BASE_ID + 10_001;
    private static final long CUSTOMER_ID = BASE_ID + 10_002;
    private static final long PROJECT_ID = BASE_ID + 10_003;
    private static final long SOURCE_PURCHASE_ORDER_ID = BASE_ID + 10_004;
    private static final long SOURCE_PURCHASE_ORDER_ITEM_ID = BASE_ID + 10_005;
    private static final String MATERIAL_CODE = "TEST-INV-STATUS-MATRIX";
    private static final String SUPPLIER_CODE = "TEST-INV-SUPPLIER";
    private static final String CUSTOMER_CODE = "TEST-INV-CUSTOMER";

    private final Map<String, Long> warehouseIds = new HashMap<>();
    private long nextWarehouseId = BASE_ID + 11_000;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private InventoryReportQueryRepository inventoryRepository;
    private IoReportQueryRepository ioRepository;

    @BeforeEach
    void setUp() {
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        inventoryRepository = new InventoryReportQueryRepository(namedJdbc);
        ioRepository = new IoReportQueryRepository(namedJdbc);
        insertMasterData();
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

    @Test
    void shouldUseWeighWeightForInboundInventoryAndMovement() {
        insertInboundWithWeigh(
                21,
                "已审核",
                10,
                new BigDecimal("20.000"),
                new BigDecimal("19.238"),
                "过磅仓"
        );

        Page<InventoryReportResponse> inventory = inventoryRepository.page(
                new PageQuery(0, 20, null, null), MATERIAL_CODE, null, null, false);
        Page<IoReportResponse> movements = ioRepository.page(
                new PageQuery(0, 20, null, null), MATERIAL_CODE, "采购入库", null, null);

        assertThat(inventory).singleElement().satisfies(row ->
                assertThat(row.onHandWeightTon()).isEqualByComparingTo("19.238")
        );
        assertThat(movements).singleElement().satisfies(row ->
                assertThat(row.inWeightTon()).isEqualByComparingTo("19.238")
        );
    }

    private void insertInbound(long offset, String status, boolean deleted, int quantity,
                               BigDecimal weight, String warehouseName) {
        long inboundId = BASE_ID + offset;
        long warehouseId = warehouseId(warehouseName);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_inbound (
                    id, inbound_no, supplier_id, supplier_code, supplier_name, warehouse_id, warehouse_name,
                    inbound_date, settlement_mode, total_weight, total_amount, status, deleted_flag
                ) VALUES (?, ?, ?, ?, '测试供应商', ?, ?, CURRENT_DATE, '按重量', ?, 0, ?, ?)
                """, inboundId, "TEST-IN-" + offset, SUPPLIER_ID, SUPPLIER_CODE,
                warehouseId, warehouseName, weight, status, deleted);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_inbound_item (
                    id, inbound_id, line_no, material_id, material_code, brand, category, material, spec, unit,
                    quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount,
                    warehouse_id, warehouse_name, source_purchase_order_item_id, settlement_mode
                ) VALUES (?, ?, 1, ?, ?, '测试品牌', '测试类别', '测试材质', 'TEST-SPEC', '吨',
                          ?, 1, 1, ?, 0, 0, ?, ?, ?, '按重量')
                """, BASE_ID + 100 + offset, inboundId, MATERIAL_ID, MATERIAL_CODE,
                quantity, weight, warehouseId, warehouseName, SOURCE_PURCHASE_ORDER_ITEM_ID);
    }

    private void insertOutbound(long offset, String status, boolean deleted, int quantity,
                                BigDecimal weight, String headerWarehouse, String itemWarehouse) {
        long outboundId = BASE_ID + offset;
        long headerWarehouseId = warehouseId(headerWarehouse);
        long itemWarehouseId = warehouseId(itemWarehouse);
        long sourceOrderId = BASE_ID + 1_000 + offset;
        long sourceOrderItemId = BASE_ID + 2_000 + offset;
        insertSourceSalesOrder(sourceOrderId, sourceOrderItemId, itemWarehouseId, weight, quantity);
        jdbcTemplate.update("""
                INSERT INTO so_sales_outbound (
                    id, outbound_no, customer_id, customer_name, project_id, project_name,
                    warehouse_id, warehouse_name, outbound_date, total_weight, total_amount, status, deleted_flag
                ) VALUES (?, ?, ?, '测试客户', ?, '测试项目', ?, ?, CURRENT_DATE, ?, 0, ?, ?)
                """, outboundId, "TEST-OUT-" + offset, CUSTOMER_ID, PROJECT_ID,
                headerWarehouseId, headerWarehouse, weight, status, deleted);
        jdbcTemplate.update("""
                INSERT INTO so_sales_outbound_item (
                    id, outbound_id, line_no, material_id, material_code, brand, category, material, spec, unit,
                    quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount,
                    warehouse_id, warehouse_name, source_sales_order_item_id
                ) VALUES (?, ?, 1, ?, ?, '测试品牌', '测试类别', '测试材质', 'TEST-SPEC', '吨',
                          ?, 1, 1, ?, 0, 0, ?, ?, ?)
                """, BASE_ID + 100 + offset, outboundId, MATERIAL_ID, MATERIAL_CODE,
                quantity, weight, itemWarehouseId, itemWarehouse, sourceOrderItemId);
    }

    private void insertInboundWithWeigh(long offset, String status, int quantity,
                                        BigDecimal theoreticalWeight, BigDecimal weighWeight,
                                        String warehouseName) {
        long inboundId = BASE_ID + offset;
        long warehouseId = warehouseId(warehouseName);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_inbound (
                    id, inbound_no, supplier_id, supplier_code, supplier_name, warehouse_id, warehouse_name,
                    inbound_date, settlement_mode, total_weight, total_amount, status, deleted_flag
                ) VALUES (?, ?, ?, ?, '测试供应商', ?, ?, CURRENT_DATE, '过磅', ?, 0, ?, FALSE)
                """, inboundId, "TEST-IN-" + offset, SUPPLIER_ID, SUPPLIER_CODE,
                warehouseId, warehouseName, theoreticalWeight, status);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_inbound_item (
                    id, inbound_id, line_no, material_id, material_code, brand, category, material, spec, unit,
                    quantity, piece_weight_ton, pieces_per_bundle, weight_ton, weigh_weight_ton,
                    weight_adjustment_ton, weight_adjustment_amount, unit_price, amount,
                    warehouse_id, warehouse_name, source_purchase_order_item_id, settlement_mode
                ) VALUES (?, ?, 1, ?, ?, '测试品牌', '测试类别', '测试材质', 'TEST-SPEC', '吨',
                          ?, 2, 1, ?, ?, ?, 0, 0, 0, ?, ?, ?, '过磅')
                """,
                BASE_ID + 100 + offset,
                inboundId,
                MATERIAL_ID,
                MATERIAL_CODE,
                quantity,
                theoreticalWeight,
                weighWeight,
                weighWeight.subtract(theoreticalWeight),
                warehouseId,
                warehouseName,
                SOURCE_PURCHASE_ORDER_ITEM_ID
        );
    }

    private void insertMasterData() {
        jdbcTemplate.update("""
                INSERT INTO md_supplier (id, supplier_code, supplier_name, status, deleted_flag)
                VALUES (?, ?, '测试供应商', '正常', FALSE)
                """, SUPPLIER_ID, SUPPLIER_CODE);
        jdbcTemplate.update("""
                INSERT INTO md_material (
                    id, material_code, brand, material, category, spec, unit,
                    piece_weight_ton, pieces_per_bundle, unit_price, deleted_flag
                ) VALUES (?, ?, '测试品牌', '测试材质', '测试类别', 'TEST-SPEC', '吨', 1, 1, 0, FALSE)
                """, MATERIAL_ID, MATERIAL_CODE);
        jdbcTemplate.update("""
                INSERT INTO md_customer (
                    id, customer_code, customer_name, project_name, status, deleted_flag
                ) VALUES (?, ?, '测试客户', '测试项目', '正常', FALSE)
                """, CUSTOMER_ID, CUSTOMER_CODE);
        jdbcTemplate.update("""
                INSERT INTO md_project (
                    id, project_code, project_name, customer_id, customer_code, status, deleted_flag
                ) VALUES (?, 'TEST-INV-PROJECT', '测试项目', ?, ?, '正常', FALSE)
                """, PROJECT_ID, CUSTOMER_ID, CUSTOMER_CODE);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_order (
                    id, order_no, supplier_id, supplier_code, supplier_name, order_date,
                    total_weight, total_amount, status, deleted_flag
                ) VALUES (?, 'TEST-INV-SOURCE-PO', ?, ?, '测试供应商', CURRENT_TIMESTAMP,
                          1, 1, '已审核', FALSE)
                """, SOURCE_PURCHASE_ORDER_ID, SUPPLIER_ID, SUPPLIER_CODE);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_order_item (
                    id, order_id, line_no, material_id, material_code, brand, category,
                    material, spec, unit, quantity, piece_weight_ton, pieces_per_bundle,
                    weight_ton, unit_price, amount
                ) VALUES (?, ?, 1, ?, ?, '测试品牌', '测试类别', '测试材质',
                          'TEST-SPEC', '吨', 1, 1, 1, 1, 1, 1)
                """, SOURCE_PURCHASE_ORDER_ITEM_ID, SOURCE_PURCHASE_ORDER_ID, MATERIAL_ID, MATERIAL_CODE);
    }

    private long warehouseId(String warehouseName) {
        return warehouseIds.computeIfAbsent(warehouseName, name -> {
            long id = nextWarehouseId++;
            jdbcTemplate.update("""
                    INSERT INTO md_warehouse (
                        id, warehouse_code, warehouse_name, warehouse_type, status, deleted_flag
                    ) VALUES (?, ?, ?, '常规仓', '正常', FALSE)
                    """, id, "TEST-INV-WH-" + (id - BASE_ID), name);
            return id;
        });
    }

    private void insertSourceSalesOrder(long orderId, long itemId, long warehouseId,
                                        BigDecimal weight, int quantity) {
        jdbcTemplate.update("""
                INSERT INTO so_sales_order (
                    id, order_no, customer_id, customer_code, customer_name, project_id, project_name,
                    delivery_date, sales_name, total_weight, total_amount, status, deleted_flag
                ) VALUES (?, ?, ?, ?, '测试客户', ?, '测试项目', CURRENT_DATE,
                          '测试销售', ?, 0, '已审核', FALSE)
                """, orderId, "TEST-SOURCE-SO-" + orderId, CUSTOMER_ID, CUSTOMER_CODE,
                PROJECT_ID, weight);
        jdbcTemplate.update("""
                INSERT INTO so_sales_order_item (
                    id, order_id, line_no, material_id, material_code, brand, category, material,
                    spec, unit, quantity, piece_weight_ton, pieces_per_bundle, weight_ton,
                    unit_price, amount, warehouse_id, warehouse_name
                ) VALUES (?, ?, 1, ?, ?, '测试品牌', '测试类别', '测试材质', 'TEST-SPEC', '吨',
                          ?, 1, 1, ?, 0, 0, ?,
                          (SELECT warehouse_name FROM md_warehouse WHERE id = ?))
                """, itemId, orderId, MATERIAL_ID, MATERIAL_CODE, quantity, weight,
                warehouseId, warehouseId);
    }
}
