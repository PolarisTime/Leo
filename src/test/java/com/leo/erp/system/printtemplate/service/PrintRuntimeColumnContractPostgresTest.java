package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 打印配置（print-runtime.json）与真实库表结构的列名契约。
 *
 * <p>背景：{@code printColumns} / {@code itemPrintColumns} 会被直接拼进 SQL，配置引用
 * 已删除或改名的列时，编译期与启动期都不会报错，只在用户点「打印」时 500。
 * 2026-11 已实际发生两轮：{@code so_sales_order_item.deleted_flag}（客户对账单明细）、
 * 采购订单 / 收款 / 付款三处改名列。本用例在真实库门禁
 * （{@code LEO_TEST_POSTGRES=true}）里逐列核对，配置与 schema 一旦漂移 CI 直接失败。</p>
 *
 * <p>不覆盖 {@code PrintRecordDataProvider.statementGroupingColumns} 里按模块硬编码的
 * 分组 SQL：那段列名由 {@code PrintRecordDataProviderTest} 的 SQL 断言与真实库回归覆盖；
 * 这里守住配置驱动的数据面（主表 + 明细表两份列清单）。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class PrintRuntimeColumnContractPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    private final PrintRuntimeProperties properties = new PrintRuntimeProperties(new ObjectMapper());

    @Test
    void printColumnsAndItemPrintColumnsShouldExistInDatabase() {
        List<String> problems = new ArrayList<>();
        for (String moduleKey : properties.printableModules()) {
            PrintRecordSource source = properties.source(moduleKey);
            checkTable(moduleKey, "printColumns", source.tableName(), source.printColumns(), problems);
            checkTable(moduleKey, "itemPrintColumns", source.itemTableName(), source.itemPrintColumns(), problems);
        }
        assertThat(problems)
                .as("print-runtime.json 引用了数据库中不存在的列（点打印会 500）")
                .isEmpty();
    }

    private void checkTable(
            String moduleKey,
            String field,
            String table,
            List<String> columns,
            List<String> problems
    ) {
        Set<String> actual = tableColumns(table);
        if (actual.isEmpty()) {
            problems.add(moduleKey + "." + field + ": 表 " + table + " 不存在");
            return;
        }
        for (String column : columns) {
            if (!actual.contains(column)) {
                problems.add(moduleKey + "." + field + ": " + table + " 缺列 " + column);
            }
        }
    }

    private Set<String> tableColumns(String table) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT a.attname FROM pg_class c "
                        + "JOIN pg_attribute a ON a.attrelid = c.oid "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'public' AND c.relname = ? AND c.relkind IN ('r','p') "
                        + "AND a.attnum > 0 AND a.attname NOT LIKE '.pg.dropped%'",
                String.class,
                table
        ));
    }
}
