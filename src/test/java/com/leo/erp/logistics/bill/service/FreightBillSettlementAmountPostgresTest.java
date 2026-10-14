package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.persistence.JpaAuditConfig;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.enums.FreightSettlementMode;
import com.leo.erp.logistics.bill.mapper.FreightBillMapperImpl;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import com.leo.erp.master.api.CarrierQuery;
import com.leo.erp.master.api.VehicleQuery;
import com.leo.erp.sales.api.SalesOrderLogisticsSourceQuery;
import com.leo.erp.sales.api.SalesOrderSourceItemSnapshot;
import com.leo.erp.sales.api.SalesOrderSourceSnapshot;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 真实 PostgreSQL 的钱口径回归: 经 {@link FreightBillService} 落库后
 * {@code lg_freight_bill.total_freight} 必须等于「本次结算金额」——BY_TONNAGE 时 = 单价 × 吨位,
 * FIXED 时 = 固定运费金额, 且 {@code unit_price} / {@code total_weight} 不被改写
 * （默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行）。
 *
 * <p>与 {@code FreightBillSettlementModePostgresTest} 的分工: 那边只验证迁移结构/实体往返,
 * 这里走完整服务写路径(create/update)并核对落库金额, 是本功能唯一的钱口径端到端证据。</p>
 *
 * <p><b>自包含夹具链</b>: 物流单明细行对主数据与来源单据有一整套 NOT NULL 外键
 * (客户/项目/物料/仓库、以及来源销售订单明细 {@code source_sales_order_item_id}), 而
 * {@code so_sales_order_item} 又要求二选一的采购来源外键, 因此本用例自建一条最小主数据链, 跑完按
 * FK RESTRICT 逆序删除, 不依赖开发库既有业务数据(新库/CI 同样能真跑, 不用 assume-skip 掩盖没跑):</p>
 * <pre>
 * md_customer → md_project(id, customer_id) → md_material, md_warehouse, md_supplier
 *   → po_purchase_order → po_purchase_order_item
 *   → so_sales_order(order → 客户/项目) → so_sales_order_item(来源=po_purchase_order_item)
 *   → md_carrier, sys_company_setting(物流商与其默认结算主体)
 * </pre>
 *
 * <p>外部依赖(销售来源/物流商/结算主体/加锁/审计事件/费用明细)按仓储惯例用 mock 注入;
 * 夹具用 {@code REQUIRES_NEW} 显式提交, 保证服务自己的写事务能看到它们。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
@Import({FreightBillService.class, FreightBillApplyService.class, FreightBillMapperImpl.class,
        // 审计字段(created_by/created_at)由 JPA 审计填充, 与生产同一套配置; @DataJpaTest 不会自动带
        JpaAuditConfig.class, FreightBillSettlementAmountPostgresTest.StubConfig.class})
// 关闭测试托管事务: 夹具与清理必须真正提交, 服务写路径各自开事务才能看到并引用它们
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FreightBillSettlementAmountPostgresTest {

    private static final long CARRIER_ID = 930000000000000200L;
    private static final String CARRIER_CODE = "PG-SETTLE-C";
    private static final String CARRIER_NAME = "结算口径回归承运商";
    private static final long SETTLEMENT_COMPANY_ID = 930000000000000201L;
    private static final String SETTLEMENT_COMPANY_NAME = "V184回归结算主体";
    private static final long CUSTOMER_ID = 930000000000000210L;
    private static final String CUSTOMER_CODE = "PG-V184-CUST";
    private static final String CUSTOMER_NAME = "V184回归客户";
    private static final long PROJECT_ID = 930000000000000211L;
    private static final String PROJECT_NAME = "V184回归项目";
    private static final long MATERIAL_ID = 930000000000000212L;
    private static final String MATERIAL_CODE = "PG-V184-MAT";
    private static final String MATERIAL_BRAND = "V184品牌";
    private static final String MATERIAL_CATEGORY = "V184品类";
    private static final String MATERIAL_NAME = "V184材质";
    private static final String MATERIAL_SPEC = "V184规格";
    private static final long WAREHOUSE_ID = 930000000000000213L;
    private static final String WAREHOUSE_NAME = "V184回归库房";
    private static final long SUPPLIER_ID = 930000000000000214L;
    private static final long PURCHASE_ORDER_ID = 930000000000000220L;
    private static final long PURCHASE_ORDER_ITEM_ID = 930000000000000230L;
    private static final long SALES_ORDER_ID = 930000000000000240L;
    private static final long SOURCE_ITEM_ID = 930000000000000250L;
    private static final int SOURCE_QUANTITY = 10;

    /** 单价 100.00 × 吨位 12.500 = 按吨位金额 1250.00。 */
    private static final BigDecimal UNIT_PRICE = new BigDecimal("100.00");
    private static final BigDecimal FIXED_AMOUNT = new BigDecimal("1800.55");
    private static final String TONNAGE_FREIGHT = "1250.00";
    private static final String TOTAL_WEIGHT = "12.50000000";

    @Autowired
    private FreightBillService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private SalesOrderLogisticsSourceQuery salesOrderSourceQuery;

    @Autowired
    private CarrierQuery carrierQuery;

    @Autowired
    private CompanySettingService companySettingService;

    @Autowired
    private DocumentChargeItemService documentChargeItemService;

    @BeforeEach
    void setUpFixturesAndStubs() {
        // 夹具必须"真提交": 用 REQUIRES_NEW 显式开事务并提交, 不依赖测试托管事务的挂起/回滚语义,
        // 否则服务自己的写事务看不到它们, 会撞外键。
        newTransaction().executeWithoutResult(status -> {
            cleanupRows();
            insertMasterData();
            insertSourceDocuments();
        });
        when(carrierQuery.findActiveById(CARRIER_ID)).thenReturn(Optional.of(
                new CarrierQuery.CarrierSnapshot(CARRIER_ID, CARRIER_CODE, CARRIER_NAME,
                        SETTLEMENT_COMPANY_ID)));
        CompanySetting company = new CompanySetting();
        company.setId(SETTLEMENT_COMPANY_ID);
        company.setCompanyName(SETTLEMENT_COMPANY_NAME);
        when(companySettingService.requireActiveSettlementCompany(SETTLEMENT_COMPANY_ID)).thenReturn(company);
        when(documentChargeItemService.list(any(), any())).thenReturn(List.of());
        when(documentChargeItemService.sumAmount(any())).thenReturn(BigDecimal.ZERO);
        when(salesOrderSourceQuery.findOrderIdsBySourceItemIds(any())).thenReturn(List.of(SALES_ORDER_ID));
        when(salesOrderSourceQuery.findBySourceItemIds(any())).thenReturn(List.of(sourceOrder()));
    }

    @AfterEach
    void cleanup() {
        newTransaction().executeWithoutResult(status -> cleanupRows());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void create_byTonnageMode_settlesByUnitPriceTimesWeight() {
        FreightBillResponse created = service.create(request(null, null));

        assertThat(created.settlementMode()).isEqualTo(FreightSettlementMode.BY_TONNAGE);
        assertThat(created.fixedFreightAmount()).isNull();
        assertThat(created.unitPrice()).isEqualByComparingTo(UNIT_PRICE);
        assertThat(created.totalWeight()).isEqualByComparingTo(TOTAL_WEIGHT);
        assertThat(created.totalFreight()).isEqualByComparingTo(TONNAGE_FREIGHT);
        assertThat(created.tonnageFreightAmount()).isEqualByComparingTo(TONNAGE_FREIGHT);

        Map<String, Object> raw = row(created.id());
        assertThat((BigDecimal) raw.get("total_freight")).isEqualByComparingTo(TONNAGE_FREIGHT);
        assertThat((BigDecimal) raw.get("unit_price")).isEqualByComparingTo(UNIT_PRICE);
        assertThat((BigDecimal) raw.get("total_weight")).isEqualByComparingTo(TOTAL_WEIGHT);
        assertThat(raw.get("settlement_mode")).isEqualTo("BY_TONNAGE");
        assertThat(raw.get("fixed_freight_amount")).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void create_fixedMode_settlesByFixedAmountAndKeepsUnitPriceAndWeight() {
        FreightBillResponse created = service.create(
                request(FreightSettlementMode.FIXED, FIXED_AMOUNT));

        assertThat(created.settlementMode()).isEqualTo(FreightSettlementMode.FIXED);
        assertThat(created.fixedFreightAmount()).isEqualByComparingTo(FIXED_AMOUNT);
        // 结算额取固定运费, 按吨位金额只作为只读参考
        assertThat(created.totalFreight()).isEqualByComparingTo(FIXED_AMOUNT);
        assertThat(created.tonnageFreightAmount()).isEqualByComparingTo(TONNAGE_FREIGHT);
        // 吨位与单价照旧落库(其它模块与对账单还在读)
        assertThat(created.unitPrice()).isEqualByComparingTo(UNIT_PRICE);
        assertThat(created.totalWeight()).isEqualByComparingTo(TOTAL_WEIGHT);

        Map<String, Object> raw = row(created.id());
        assertThat((BigDecimal) raw.get("total_freight")).isEqualByComparingTo(FIXED_AMOUNT);
        assertThat((BigDecimal) raw.get("fixed_freight_amount")).isEqualByComparingTo(FIXED_AMOUNT);
        assertThat((BigDecimal) raw.get("unit_price")).isEqualByComparingTo(UNIT_PRICE);
        assertThat((BigDecimal) raw.get("total_weight")).isEqualByComparingTo(TOTAL_WEIGHT);
        assertThat(raw.get("settlement_mode")).isEqualTo("FIXED");
    }

    /** 编辑既有单据切换结算方式: 结算额随之重算, 切回按吨位时遗留的固定金额被清空。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void update_switchingSettlementMode_recomputesSettlementAmount() {
        FreightBillResponse created = service.create(request(null, null));
        assertThat(created.totalFreight()).isEqualByComparingTo(TONNAGE_FREIGHT);

        FreightBillResponse switchedToFixed = service.update(created.id(),
                request(FreightSettlementMode.FIXED, FIXED_AMOUNT));

        assertThat(switchedToFixed.settlementMode()).isEqualTo(FreightSettlementMode.FIXED);
        assertThat(switchedToFixed.totalFreight()).isEqualByComparingTo(FIXED_AMOUNT);
        assertThat((BigDecimal) row(created.id()).get("total_freight")).isEqualByComparingTo(FIXED_AMOUNT);

        FreightBillResponse switchedBack = service.update(created.id(),
                request(FreightSettlementMode.BY_TONNAGE, null));

        assertThat(switchedBack.settlementMode()).isEqualTo(FreightSettlementMode.BY_TONNAGE);
        assertThat(switchedBack.fixedFreightAmount()).isNull();
        assertThat(switchedBack.totalFreight()).isEqualByComparingTo(TONNAGE_FREIGHT);
        Map<String, Object> raw = row(created.id());
        assertThat((BigDecimal) raw.get("total_freight")).isEqualByComparingTo(TONNAGE_FREIGHT);
        assertThat(raw.get("fixed_freight_amount")).isNull();
        // 结算方式来回切换不改写单价与吨位
        assertThat((BigDecimal) raw.get("unit_price")).isEqualByComparingTo(UNIT_PRICE);
        assertThat((BigDecimal) raw.get("total_weight")).isEqualByComparingTo(TOTAL_WEIGHT);
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private Map<String, Object> row(Long billId) {
        return jdbc.queryForMap("""
                select settlement_mode, fixed_freight_amount, total_freight, unit_price, total_weight
                  from lg_freight_bill where id = ?
                """, billId);
    }

    /** 主数据夹具: 物流商/结算主体/客户/项目/物料/仓库/供应商(字段取值对齐开发库 NOT NULL 与 CHECK)。 */
    private void insertMasterData() {
        jdbc.update("""
                insert into sys_company_setting (id, company_name, tax_no, bank_name, bank_account,
                                                 status, settlement_accounts_json, created_by, created_name,
                                                 created_at, deleted_flag, version)
                values (?, ?, 'PG-V184-TAX', 'V184银行', 'PG-V184-ACC', '正常', '[]'::jsonb,
                        0, 'system', now(), false, 0)
                """, SETTLEMENT_COMPANY_ID, SETTLEMENT_COMPANY_NAME);
        jdbc.update("""
                insert into md_carrier (id, carrier_code, carrier_name, status, deleted_flag,
                                        created_by, created_name, created_at, version)
                values (?, ?, ?, '正常', false, 0, 'system', now(), 0)
                """, CARRIER_ID, CARRIER_CODE, CARRIER_NAME);
        jdbc.update("""
                insert into md_customer (id, customer_code, customer_name, project_name, status,
                                         deleted_flag, created_by, created_name, created_at, version)
                values (?, ?, ?, ?, '正常', false, 0, 'system', now(), 0)
                """, CUSTOMER_ID, CUSTOMER_CODE, CUSTOMER_NAME, PROJECT_NAME);
        jdbc.update("""
                insert into md_project (id, project_code, project_name, customer_code, customer_id, status,
                                        deleted_flag, created_by, created_name, created_at, version)
                values (?, 'PG-V184-PRJ', ?, ?, ?, '正常', false, 0, 'system', now(), 0)
                """, PROJECT_ID, PROJECT_NAME, CUSTOMER_CODE, CUSTOMER_ID);
        jdbc.update("""
                insert into md_material (id, material_code, brand, material, category, spec, unit,
                                         piece_weight_ton, pieces_per_bundle, unit_price, deleted_flag,
                                         created_by, created_name, created_at, version)
                values (?, ?, ?, ?, ?, ?, '吨', 1.250, 100, 4000, false, 0, 'system', now(), 0)
                """, MATERIAL_ID, MATERIAL_CODE, MATERIAL_BRAND, MATERIAL_NAME, MATERIAL_CATEGORY,
                MATERIAL_SPEC);
        jdbc.update("""
                insert into md_warehouse (id, warehouse_code, warehouse_name, warehouse_type, status,
                                          deleted_flag, created_by, created_name, created_at, version)
                values (?, 'PG-V184-WH', ?, '自营仓', '正常', false, 0, 'system', now(), 0)
                """, WAREHOUSE_ID, WAREHOUSE_NAME);
        jdbc.update("""
                insert into md_supplier (id, supplier_code, supplier_name, status, deleted_flag,
                                         created_by, created_name, created_at, version)
                values (?, 'PG-V184-SUP', 'V184回归供应商', '正常', false, 0, 'system', now(), 0)
                """, SUPPLIER_ID);
    }

    /**
     * 来源单据夹具: 采购订单 → 采购订单明细 → 销售订单 → 销售订单明细。
     * <p>{@code so_sales_order_item} 有 CHECK「必须且只能有一个采购来源外键」, 因此销售明细指向刚建的
     * 采购订单明细; 数量/件重与快照一致(10 × 1.250 = 12.500 吨), 保证金额口径可精确断言。</p>
     */
    private void insertSourceDocuments() {
        jdbc.update("""
                insert into po_purchase_order (id, order_no, supplier_id, supplier_code, supplier_name,
                                               order_date, total_weight, total_amount, status,
                                               deleted_flag, created_by, created_name, created_at, version)
                values (?, 'PG-V184-PO', ?, 'PG-V184-SUP', 'V184回归供应商', current_date,
                        12.500, 50000, '已审核', false, 0, 'system', now(), 0)
                """, PURCHASE_ORDER_ID, SUPPLIER_ID);
        jdbc.update("""
                insert into po_purchase_order_item (id, order_id, line_no, material_code, brand, category,
                                                    material, spec, unit, quantity, piece_weight_ton,
                                                    pieces_per_bundle, weight_ton, unit_price, amount,
                                                    material_id)
                values (?, ?, 1, ?, ?, ?, ?, ?, '吨', 100, 1.250, 100, 125.000, 4000, 500000, ?)
                """, PURCHASE_ORDER_ITEM_ID, PURCHASE_ORDER_ID, MATERIAL_CODE, MATERIAL_BRAND,
                MATERIAL_CATEGORY, MATERIAL_NAME, MATERIAL_SPEC, MATERIAL_ID);
        jdbc.update("""
                insert into so_sales_order (id, order_no, customer_id, customer_name, project_id, project_name,
                                            delivery_date, sales_name, total_weight, total_amount, status,
                                            deleted_flag, created_by, created_name, created_at, version)
                values (?, 'PG-V184-SO', ?, ?, ?, ?, current_date, '销售员A', 12.500, 50000, '已审核',
                        false, 0, 'system', now(), 0)
                """, SALES_ORDER_ID, CUSTOMER_ID, CUSTOMER_NAME, PROJECT_ID, PROJECT_NAME);
        jdbc.update("""
                insert into so_sales_order_item (id, order_id, line_no, material_id, material_code, brand,
                                                 category, material, spec, unit, quantity, piece_weight_ton,
                                                 pieces_per_bundle, weight_ton, unit_price, amount,
                                                 source_purchase_order_item_id, warehouse_id,
                                                 settlement_company_id)
                values (?, ?, 1, ?, ?, ?, ?, ?, ?, '吨', 10, 1.250, 100, 12.500, 4000, 50000,
                        ?, ?, ?)
                """, SOURCE_ITEM_ID, SALES_ORDER_ID, MATERIAL_ID, MATERIAL_CODE, MATERIAL_BRAND,
                MATERIAL_CATEGORY, MATERIAL_NAME, MATERIAL_SPEC, PURCHASE_ORDER_ITEM_ID, WAREHOUSE_ID,
                SETTLEMENT_COMPANY_ID);
    }

    /** 按 FK RESTRICT 逆序清理夹具, 并顺手清掉上次异常退出可能残留的物流单及其子行。 */
    private void cleanupRows() {
        // lg_freight_bill 有两个 RESTRICT 子表(明细行 + 来源订单关系), 必须先删子行再删单据
        jdbc.update("""
                delete from lg_freight_bill_item
                 where bill_id in (select id from lg_freight_bill where carrier_id = ?)
                """, CARRIER_ID);
        jdbc.update("""
                delete from lg_freight_bill_source_order
                 where freight_bill_id in (select id from lg_freight_bill where carrier_id = ?)
                """, CARRIER_ID);
        jdbc.update("delete from lg_freight_bill where carrier_id = ?", CARRIER_ID);
        jdbc.update("delete from so_sales_order_item where id = ?", SOURCE_ITEM_ID);
        jdbc.update("delete from so_sales_order where id = ?", SALES_ORDER_ID);
        jdbc.update("delete from po_purchase_order_item where id = ?", PURCHASE_ORDER_ITEM_ID);
        jdbc.update("delete from po_purchase_order where id = ?", PURCHASE_ORDER_ID);
        jdbc.update("delete from md_supplier where id = ?", SUPPLIER_ID);
        jdbc.update("delete from md_warehouse where id = ?", WAREHOUSE_ID);
        jdbc.update("delete from md_material where id = ?", MATERIAL_ID);
        jdbc.update("delete from md_project where id = ?", PROJECT_ID);
        jdbc.update("delete from md_customer where id = ?", CUSTOMER_ID);
        jdbc.update("delete from md_carrier where id = ?", CARRIER_ID);
        jdbc.update("delete from sys_company_setting where id = ?", SETTLEMENT_COMPANY_ID);
    }

    private FreightBillRequest request(FreightSettlementMode settlementMode, BigDecimal fixedFreightAmount) {
        return new FreightBillRequest(
                null, CARRIER_ID, CARRIER_CODE, CARRIER_NAME, null, null, null, null,
                LocalDate.now(), UNIT_PRICE, settlementMode, fixedFreightAmount,
                null, null, List.of(itemRequest()), List.of(), false);
    }

    private static FreightBillItemRequest itemRequest() {
        // 只给来源明细 id: 数量缺省取来源数量(10), 重量按来源件重换算(10 × 1.250 = 12.500)
        return new FreightBillItemRequest(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                SOURCE_ITEM_ID);
    }

    private static SalesOrderSourceSnapshot sourceOrder() {
        return new SalesOrderSourceSnapshot(
                SALES_ORDER_ID, "PG-V184-SO", "PG-V184-IB", "PG-V184-PO", CUSTOMER_CODE,
                CUSTOMER_ID, CUSTOMER_NAME, PROJECT_ID, PROJECT_NAME, SETTLEMENT_COMPANY_ID,
                SETTLEMENT_COMPANY_NAME, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 30), "销售员A",
                new BigDecimal("12.500"), new BigDecimal("50000"),
                StatusConstants.AUDITED, false, null, List.of(sourceItem()));
    }

    private static SalesOrderSourceItemSnapshot sourceItem() {
        return new SalesOrderSourceItemSnapshot(
                SOURCE_ITEM_ID, 1, MATERIAL_ID, MATERIAL_CODE, MATERIAL_BRAND, MATERIAL_CATEGORY,
                MATERIAL_NAME, MATERIAL_SPEC, "12m", "吨",
                null, PURCHASE_ORDER_ITEM_ID, SETTLEMENT_COMPANY_ID, SETTLEMENT_COMPANY_NAME,
                WAREHOUSE_ID, WAREHOUSE_NAME, "B001", "b001", SOURCE_QUANTITY, "件",
                new BigDecimal("1.250"), 100,
                new BigDecimal("12.500"), new BigDecimal("4000"), new BigDecimal("50000"),
                new BigDecimal("12.500"), null);
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        SalesOrderLogisticsSourceQuery salesOrderLogisticsSourceQuery() {
            return Mockito.mock(SalesOrderLogisticsSourceQuery.class);
        }

        @Bean
        CarrierQuery carrierQuery() {
            return Mockito.mock(CarrierQuery.class);
        }

        @Bean
        VehicleQuery vehicleQuery() {
            return Mockito.mock(VehicleQuery.class);
        }

        @Bean
        CompanySettingService companySettingService() {
            return Mockito.mock(CompanySettingService.class);
        }

        @Bean
        SourceAllocationLockService sourceAllocationLockService() {
            return Mockito.mock(SourceAllocationLockService.class);
        }

        @Bean
        FreightBillDownstreamMutationGuard freightBillDownstreamMutationGuard() {
            return Mockito.mock(FreightBillDownstreamMutationGuard.class);
        }

        @Bean
        BusinessOperationEventPublisher businessOperationEventPublisher() {
            return Mockito.mock(BusinessOperationEventPublisher.class);
        }

        @Bean
        DocumentChargeItemService documentChargeItemService() {
            return Mockito.mock(DocumentChargeItemService.class);
        }

        @Bean
        SnowflakeIdGenerator snowflakeIdGenerator() {
            return new SnowflakeIdGenerator();
        }

        /**
         * 被 mock 的 {@code CompanySettingService} 继承了 {@code @Autowired setCacheManager},
         * Spring 装配该 mock 时会调用它, 因此上下文里必须存在一个 CacheManager。
         */
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }
}
