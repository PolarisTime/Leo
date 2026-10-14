package com.leo.erp.logistics.bill.repository;

import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.enums.FreightSettlementMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PostgreSQL 的 V184「物流单结算方式」迁移与实体映射回归（默认跳过，设置
 * {@code LEO_TEST_POSTGRES=true} 才执行）。
 *
 * <p>覆盖三件事: 迁移把两列按预期加出来(类型/可空/默认值/列注释)、老行(不带该列的插入)
 * 默认按吨位结算不破坏历史数据、FIXED 单据能经 JPA 往返落库并读回。</p>
 *
 * <p>物流单对 {@code md_carrier} 有外键, 因此本用例自建(并在结束时删除)一个专用物流商夹具,
 * 不依赖开发库里的既有主数据。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class FreightBillSettlementModePostgresTest {

    private static final long LEGACY_BILL_ID = 930000000000000001L;
    private static final long FIXED_BILL_ID = 930000000000000002L;
    private static final long CARRIER_ID = 930000000000000100L;
    private static final String CARRIER_CODE = "PG-V184-C";
    private static final String CARRIER_NAME = "V184回归承运商";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FreightBillRepository repository;

    @BeforeEach
    void createCarrierFixture() {
        cleanupRows();
        jdbc.update("""
                insert into md_carrier (id, carrier_code, carrier_name, status, deleted_flag,
                                        created_by, created_name, created_at, version)
                values (?, ?, ?, '正常', false, 0, 'system', now(), 0)
                """, CARRIER_ID, CARRIER_CODE, CARRIER_NAME);
    }

    @AfterEach
    void cleanup() {
        cleanupRows();
    }

    /** 先删单据再删物流商(外键 RESTRICT), 并顺手清理上次异常退出可能残留的行。 */
    private void cleanupRows() {
        jdbc.update("delete from lg_freight_bill where id in (?, ?)", LEGACY_BILL_ID, FIXED_BILL_ID);
        jdbc.update("delete from md_carrier where id = ?", CARRIER_ID);
    }

    @Test
    void v184_addsSettlementColumnsWithBackwardCompatibleDefaults() {
        ColumnMeta mode = column("settlement_mode");
        assertThat(mode.type()).isEqualTo("character varying");
        assertThat(mode.nullable()).isFalse();
        assertThat(mode.maxLength()).isEqualTo(16);
        assertThat(mode.defaultValue()).contains("BY_TONNAGE");

        ColumnMeta fixed = column("fixed_freight_amount");
        assertThat(fixed.type()).isEqualTo("numeric");
        assertThat(fixed.nullable()).isTrue();
        assertThat(fixed.numericPrecision()).isEqualTo(14);
        assertThat(fixed.numericScale()).isEqualTo(2);

        // 老数据全部按吨位结算, 不允许出现 NULL(否则应用枚举映射会炸)。
        assertThat(jdbc.queryForObject(
                "select count(*) from lg_freight_bill where settlement_mode is null", Integer.class)).isZero();
    }

    @Test
    void v184_columnComments_documentSettlementSemantics() {
        assertThat(columnComment("settlement_mode")).contains("BY_TONNAGE").contains("FIXED");
        assertThat(columnComment("fixed_freight_amount")).contains("FIXED").contains("total_freight");
        assertThat(columnComment("total_freight")).contains("结算金额");
        assertThat(columnComment("unit_price")).contains("照旧");
    }

    /** 老行(插入时完全不带 settlement_mode)必须读到按吨位结算, 固定运费金额为空。 */
    @Test
    void legacyRowInsertedWithoutSettlementMode_readsBackAsByTonnage() {
        jdbc.update("""
                insert into lg_freight_bill (id, bill_no, carrier_name, bill_time, unit_price, total_weight,
                                             total_freight, status, carrier_code, carrier_id, version, created_by,
                                             created_name, created_at, deleted_flag)
                values (?, ?, ?, ?, 100.00, 12.50000000, 1250.00, '草稿', ?, ?, 0, 0,
                        'system', now(), false)
                """, LEGACY_BILL_ID, "FB-PG-V184-LEGACY", CARRIER_NAME, LocalDateTime.now(),
                CARRIER_CODE, CARRIER_ID);

        FreightBill bill = repository.findById(LEGACY_BILL_ID).orElseThrow();

        assertThat(bill.getSettlementMode()).isEqualTo(FreightSettlementMode.BY_TONNAGE);
        assertThat(bill.getFixedFreightAmount()).isNull();
        assertThat(bill.getTotalFreight()).isEqualByComparingTo("1250.00");
    }

    /** FIXED 单据经 JPA 往返: 枚举按名字落库, 固定金额按 numeric(14,2) 读回。 */
    @Test
    void fixedModeBill_roundTripsThroughJpaMapping() {
        FreightBill bill = new FreightBill();
        bill.setId(FIXED_BILL_ID);
        bill.setBillNo("FB-PG-V184-FIXED");
        bill.setCarrierId(CARRIER_ID);
        bill.setCarrierCode(CARRIER_CODE);
        bill.setCarrierName(CARRIER_NAME);
        bill.setBillTime(LocalDate.now());
        bill.setUnitPrice(new BigDecimal("100.00"));
        bill.setTotalWeight(new BigDecimal("12.50000000"));
        bill.setTotalFreight(new BigDecimal("1800.55"));
        bill.setSettlementMode(FreightSettlementMode.FIXED);
        bill.setFixedFreightAmount(new BigDecimal("1800.55"));
        bill.setStatus("草稿");
        bill.setCreatedAt(LocalDateTime.now());
        bill.setCreatedBy(0L);
        bill.setCreatedName("system");

        repository.saveAndFlush(bill);

        FreightBill reloaded = repository.findById(FIXED_BILL_ID).orElseThrow();
        assertThat(reloaded.getSettlementMode()).isEqualTo(FreightSettlementMode.FIXED);
        assertThat(reloaded.getFixedFreightAmount()).isEqualByComparingTo("1800.55");
        assertThat(reloaded.getTotalFreight()).isEqualByComparingTo("1800.55");
        // 吨位与单价照旧落库(其它模块还在读)
        assertThat(reloaded.getUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(reloaded.getTotalWeight()).isEqualByComparingTo("12.50000000");

        // 枚举以名字(而不是序号)落库: 直接读裸列核对
        List<String> raw = jdbc.queryForList(
                "select settlement_mode from lg_freight_bill where id = ?", String.class, FIXED_BILL_ID);
        assertThat(raw).containsExactly("FIXED");
        assertThat(jdbc.queryForObject(
                "select fixed_freight_amount from lg_freight_bill where id = ?",
                BigDecimal.class, FIXED_BILL_ID)).isEqualByComparingTo("1800.55");
    }

    private String columnComment(String column) {
        return jdbc.queryForObject("""
                select col_description('public.lg_freight_bill'::regclass, ordinal_position)
                  from information_schema.columns
                 where table_schema = 'public' and table_name = 'lg_freight_bill' and column_name = ?
                """, String.class, column);
    }

    private ColumnMeta column(String column) {
        return jdbc.queryForObject("""
                select data_type, is_nullable, column_default, character_maximum_length,
                       numeric_precision, numeric_scale
                from information_schema.columns
                where table_schema = 'public' and table_name = 'lg_freight_bill' and column_name = ?
                """,
                (rs, rowNum) -> new ColumnMeta(
                        rs.getString("data_type"),
                        "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                        rs.getString("column_default"),
                        rs.getObject("character_maximum_length", Integer.class),
                        rs.getObject("numeric_precision", Integer.class),
                        rs.getObject("numeric_scale", Integer.class)),
                column);
    }

    private record ColumnMeta(String type, boolean nullable, String defaultValue,
                              Integer maxLength, Integer numericPrecision, Integer numericScale) {
        ColumnMeta {
            if (type != null) {
                type = type.toLowerCase(Locale.ROOT);
            }
        }
    }
}
