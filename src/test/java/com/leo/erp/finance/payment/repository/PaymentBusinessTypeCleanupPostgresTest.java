package com.leo.erp.finance.payment.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V210 真库回归：fm_payment.business_type 孤立 NOT NULL 列已删除。
 *
 * <p>背景：eb9c985b 删除了 Payment 实体对 business_type 的映射但未删列，该列 NOT NULL 且无默认值，
 * 导致 POST /payments 100% 422（{@code null value in column "business_type"}）。
 * 本测试锁三件事：列不存在、迁移已登记成功、global_search_document 仍可查询（视图曾引用该列）。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class PaymentBusinessTypeCleanupPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v210_fmPayment_businessType_columnShouldBeDropped() {
        List<String> columns = jdbc.queryForList("""
                select column_name
                  from information_schema.columns
                 where table_schema = 'public'
                   and table_name = 'fm_payment'
                   and column_name = 'business_type'
                """, String.class);
        assertThat(columns).isEmpty();

        // 防「表没了所以列不存在」的假阳性：payment 表与其关键列仍存在
        Integer coreColumns = jdbc.queryForObject("""
                select count(*)
                  from information_schema.columns
                 where table_schema = 'public'
                   and table_name = 'fm_payment'
                   and column_name in ('id', 'payment_no', 'counterparty_type', 'amount', 'status')
                """, Integer.class);
        assertThat(coreColumns).isEqualTo(5);
    }

    @Test
    void v210_migrationShouldBeRegisteredAsSuccess() {
        Integer succeeded = jdbc.queryForObject("""
                select count(*)
                  from flyway_schema_history
                 where version = '210'
                   and success = true
                """, Integer.class);
        assertThat(succeeded).isEqualTo(1);
    }

    @Test
    void globalSearchDocumentView_shouldStillBeQueryableAfterRebuild() {
        Integer columnCount = jdbc.queryForObject("""
                select count(*)
                  from information_schema.columns
                 where table_schema = 'public'
                   and table_name = 'global_search_document'
                """, Integer.class);
        assertThat(columnCount).isEqualTo(8);

        Long rows = jdbc.queryForObject("select count(*) from global_search_document", Long.class);
        assertThat(rows).isNotNull().isGreaterThanOrEqualTo(0L);
    }
}
