package com.leo.erp.system.printtemplate.service;

import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 打印模板唯一约束与乐观锁契约测试（V121 迁移配套占位）。
 * <p>
 * 预期行为说明（集成环境验证项，当前以轻量契约断言锁定约定）：
 * <ul>
 *   <li>乐观锁：{@link PrintTemplate#version} 标注 {@code @Version}，由 JPA 自动管理；
 *       两个事务并发更新同一模板时，后提交者抛 {@code ObjectOptimisticLockingFailureException}，
 *       经 GlobalExceptionHandler 映射为 409 CONCURRENT_MODIFICATION。</li>
 *   <li>唯一约束：未删除模板中 (bill_type, template_code) 全局唯一（跨结算主体也唯一），
 *       由 V121 部分唯一索引 uk_print_template_bill_type_code_active 保证；重复插入抛
 *       DataIntegrityViolationException；软删除行不占用编码，可复用。</li>
 * </ul>
 */
class PrintTemplatePersistenceContractTest {

    private static final String MIGRATION_PATH = "src/main/resources/db/migration/V121__print_template_unique_and_version.sql";

    @Test
    void versionField_shouldBeJpaOptimisticLock() throws NoSuchFieldException {
        var field = PrintTemplate.class.getDeclaredField("version");

        assertThat(field.getType()).isEqualTo(Long.class);
        assertThat(field.getAnnotation(Version.class)).as("version 字段必须标注 @Version 以启用乐观锁").isNotNull();
    }

    @Test
    void v121Migration_shouldDeclareActiveUniqueIndexAndVersionColumn() throws IOException {
        String sql = Files.readString(Path.of(MIGRATION_PATH));

        assertThat(sql).contains("uk_print_template_bill_type_code_active");
        assertThat(sql).contains("ON sys_print_template (bill_type, template_code)");
        assertThat(sql).contains("WHERE deleted_flag = FALSE");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0");
    }

    @Test
    void v121Migration_shouldNotTouchExistingCompanyScopedIndex() throws IOException {
        List<String> sqlLines = Files.readAllLines(Path.of(MIGRATION_PATH));
        String sql = String.join("\n", sqlLines);

        // 既有含结算主体的唯一索引属于基线 schema，本迁移不得改写或删除（非破坏性变更约束）。
        assertThat(sql).doesNotContain("DROP INDEX");
        assertThat(sql).doesNotContain("uk_sys_print_template_bill_type_code");
    }
}
