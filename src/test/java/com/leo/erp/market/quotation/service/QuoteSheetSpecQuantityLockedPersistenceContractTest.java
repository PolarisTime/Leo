package com.leo.erp.market.quotation.service;

import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 报价单"锁定报单规格和数量"的迁移/实体映射契约测试(V145 配套)。
 * <p>
 * 轻量契约断言锁定列名、类型、默认值与 JPA 映射一致, 真实 PostgreSQL 行为由集成环境验证。
 */
class QuoteSheetSpecQuantityLockedPersistenceContractTest {

    private static final String MIGRATION_PATH =
            "src/main/resources/db/migration/V145__quote_sheet_spec_quantity_lock.sql";

    @Test
    void v145Migration_declaresSpecQuantityLockedWithReadableDefault() throws IOException {
        String sql = Files.readString(Path.of(MIGRATION_PATH));

        assertThat(sql).contains("ALTER TABLE public.mk_quote_sheet");
        assertThat(sql).contains("ADD COLUMN spec_quantity_locked boolean DEFAULT false NOT NULL");
        assertThat(sql).doesNotContain("DROP COLUMN");
    }

    @Test
    void quoteSheet_specQuantityLockedMappedToNotBlankDefaultFalseColumn() throws NoSuchFieldException {
        Field field = QuoteSheet.class.getDeclaredField("specQuantityLocked");

        assertThat(field.getType()).isEqualTo(boolean.class);
        Column column = field.getAnnotation(Column.class);
        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo("spec_quantity_locked");
        assertThat(column.nullable()).isFalse();
        assertThat(new QuoteSheet().isSpecQuantityLocked()).isFalse();
    }
}
