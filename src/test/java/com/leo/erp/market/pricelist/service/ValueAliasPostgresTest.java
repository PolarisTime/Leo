package com.leo.erp.market.pricelist.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.persistence.JpaAuditConfig;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.pricelist.domain.entity.ValueAlias;
import com.leo.erp.market.pricelist.domain.enums.ValueAliasDimension;
import com.leo.erp.market.pricelist.repository.ValueAliasRepository;
import com.leo.erp.market.pricelist.web.dto.ValueAliasRequest;
import com.leo.erp.market.pricelist.web.dto.ValueAliasResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 值映射真实 PostgreSQL 回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 *
 * <p>覆盖单元测试抓不到的部分:</p>
 * <ol>
 *   <li>迁移结构: CHECK(维度枚举/自映射)、部分唯一索引(仅未删除行)与 V171 的 CATEGORY 种子行存在;</li>
 *   <li>真库约束兜底: 非法维度/自映射即使绕过应用层也会被数据库拒绝;</li>
 *   <li>写入口径: 重复 (维度, 源值) 409、软删后可重建同源值、定尺写入归一后落库;</li>
 *   <li><b>行为守卫</b>: 改映射后规格键字典输出跟着变(证明字典投影真的走映射表, 而不是硬编码)。</li>
 * </ol>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditConfig.class, MaterialSpecCatalogQuery.class, ValueAliasQuery.class, ValueAliasMappings.class})
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class ValueAliasPostgresTest {

    private static final String TEST_MATERIAL_CODE = "PG-VALUE-ALIAS-DICT-901";
    /** 测试专用别名写法(不与真实数据冲突, 便于精确断言与清理)。 */
    private static final String TEST_CATEGORY_ALIAS = "PG别名螺纹";
    private static final String TEST_TARGET_CATEGORY = "螺纹钢";

    @Autowired
    private ValueAliasRepository repository;

    @Autowired
    private ValueAliasQuery valueAliasQuery;

    @Autowired
    private MaterialSpecCatalogQuery specCatalogQuery;

    @Autowired
    private JdbcTemplate jdbc;

    private final AtomicLong idSequence = new AtomicLong(920000000000000000L);
    private final SnowflakeIdGenerator idGenerator = Mockito.mock(SnowflakeIdGenerator.class);

    private ValueAliasStore store() {
        when(idGenerator.nextId()).thenAnswer(invocation -> idSequence.incrementAndGet());
        return new ValueAliasStore(repository, idGenerator);
    }

    private static ValueAliasRequest request(String dimension, String source, String target) {
        return new ValueAliasRequest(dimension, source, target, "PG真库用例");
    }

    /** 迁移结构: 维度枚举 CHECK、自映射 CHECK、部分唯一索引都在。 */
    @Test
    void migration_definesConstraintsAndPartialUniqueIndex() {
        List<String> checks = jdbc.queryForList("""
                select con.conname
                from pg_constraint con
                join pg_class rel on rel.oid = con.conrelid
                where rel.relname = 'md_value_alias' and con.contype = 'c'
                """, String.class);
        assertThat(checks).contains("ck_value_alias_dimension", "ck_value_alias_self_mapping");

        Boolean partialUnique = jdbc.queryForObject("""
                select bool_and(indexdef like '%UNIQUE%' and indexdef like '%WHERE (deleted_flag = false)%')
                from pg_indexes
                where tablename = 'md_value_alias' and indexname = 'uk_value_alias_dimension_source'
                """, Boolean.class);
        assertThat(partialUnique).isTrue();

        Boolean dimensionIndex = jdbc.queryForObject("""
                select exists (select 1 from pg_indexes
                               where tablename = 'md_value_alias' and indexname = 'idx_value_alias_dimension')
                """, Boolean.class);
        assertThat(dimensionIndex).isTrue();
    }

    /** 种子行: 原硬编码规则 直条 → 螺纹钢 已固化为 CATEGORY 行, 归一化仍返回 螺纹钢。 */
    @Test
    void seedRow_mapsLegacyRebarAlias() {
        Optional<ValueAlias> seed = repository
                .findByDimensionAndSourceValueAndDeletedFlagFalse(ValueAliasDimension.CATEGORY, "直条");
        assertThat(seed).isPresent();
        assertThat(seed.orElseThrow().getTargetValue()).isEqualTo("螺纹钢");

        assertThat(valueAliasQuery.normalize(ValueAliasDimension.CATEGORY, "直条")).isEqualTo("螺纹钢");
        ValueAliasQuery.Resolution resolution = valueAliasQuery.resolve(ValueAliasDimension.CATEGORY, "直条");
        assertThat(resolution.matched()).isTrue();
        assertThat(resolution.targetValue()).isEqualTo("螺纹钢");
    }

    /**
     * 数据库兜底一: 非法维度即使绕过应用层也会被拒(PostgreSQL 事务失败后即中止,
     * 因此每条负向断言单独一个用例)。
     */
    @Test
    void database_rejectsInvalidDimension() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into public.md_value_alias (id, dimension, source_value, target_value)
                values (?, 'COLOR', '红', '红色')
                """, idSequence.incrementAndGet()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 数据库兜底二: 自映射(source = target)被 CHECK 拒绝。 */
    @Test
    void database_rejectsSelfMapping() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into public.md_value_alias (id, dimension, source_value, target_value)
                values (?, 'CATEGORY', 'PG自映射', 'PG自映射')
                """, idSequence.incrementAndGet()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 生命周期: 创建 → 重复 409 → 软删 → 同 (维度, 源值) 可重建。 */
    @Test
    void lifecycle_duplicateThenSoftDeleteThenRecreate() {
        ValueAliasStore store = store();
        ValueAliasResponse created = store.create(request("BRAND", "PG富鑫", "PG安徽富鑫"));
        assertThat(created.id()).isNotNull();
        assertThat(created.dimension()).isEqualTo("BRAND");

        assertThatThrownBy(() -> store.create(request("BRAND", "PG富鑫", "PG安徽富鑫")))
                .hasMessageContaining("已存在映射");

        store.delete(created.id());
        assertThat(repository.findByIdAndDeletedFlagFalse(created.id())).isEmpty();

        ValueAliasResponse recreated = store.create(request("BRAND", "PG富鑫", "PG安徽富鑫"));
        assertThat(recreated.id()).isNotEqualTo(created.id());
        assertThat(recreated.sourceValue()).isEqualTo("PG富鑫");
        // 同一维度不同源值互不影响
        assertThat(store.create(request("MATERIAL", "PG富鑫", "PG材质")).sourceValue()).isEqualTo("PG富鑫");
    }

    /** 定尺维度写入按定尺口径归一后落库(9M → 9米), 且映射命中生效。 */
    @Test
    void lengthAlias_isCanonicalizedOnWriteAndAppliedOnRead() {
        ValueAliasStore store = store();
        ValueAliasResponse created = store.create(request("LENGTH", "PG12M", "PG12"));
        assertThat(created.sourceValue()).isEqualTo("PG12M");
        assertThat(created.targetValue()).isEqualTo("PG12");

        // 结构归一 + 映射: 9m 命中已存在的 "9米" 键才算映射命中; 这里验证自定义键的精确命中
        assertThat(valueAliasQuery.normalize(ValueAliasDimension.LENGTH, "PG12M")).isEqualTo("PG12");
        assertThat(valueAliasQuery.normalize(ValueAliasDimension.LENGTH, "9m")).isEqualTo("9米");

        // 9m 与 9 米 归一后同键, 因此写入时不会各存一行(结构口径统一)
        ValueAliasResponse nine = store.create(request("LENGTH", "9M", "高定尺"));
        assertThat(nine.sourceValue()).isEqualTo("9米");
    }

    /**
     * <b>行为守卫</b>: 字典投影确实走映射表 —— 新增 CATEGORY 映射后字典输出变成目标写法,
     * 软删映射后字典输出回到原写法(证明不是硬编码, 也不是只读时才拼字段)。
     */
    @Test
    void dictionaryOutput_followsMappingChanges() {
        insertMaterial(TEST_CATEGORY_ALIAS);
        try {
            // 无映射: 字典保留商品信息的原写法
            assertThat(specCatalogQuery.find(null, "PG值映射材质"))
                    .extracting(MaterialSpecCatalogQuery.MaterialSpecSnapshot::category)
                    .containsExactly(TEST_CATEGORY_ALIAS);

            // 新增映射: 字典输出跟着变成目标写法
            ValueAlias alias = new ValueAlias();
            alias.setId(idSequence.incrementAndGet());
            alias.setDimension(ValueAliasDimension.CATEGORY);
            alias.setSourceValue(TEST_CATEGORY_ALIAS);
            alias.setTargetValue(TEST_TARGET_CATEGORY);
            repository.saveAndFlush(alias);

            assertThat(specCatalogQuery.find(null, "PG值映射材质"))
                    .extracting(MaterialSpecCatalogQuery.MaterialSpecSnapshot::category)
                    .containsExactly(TEST_TARGET_CATEGORY);

            // 软删映射(改回来): 字典输出回到原写法
            alias.setDeletedFlag(true);
            repository.saveAndFlush(alias);

            assertThat(specCatalogQuery.find(null, "PG值映射材质"))
                    .extracting(MaterialSpecCatalogQuery.MaterialSpecSnapshot::category)
                    .containsExactly(TEST_CATEGORY_ALIAS);
        } finally {
            jdbc.update("delete from public.md_material where material_code like 'PG-VALUE-ALIAS-DICT-%'");
        }
    }

    /** 材质维度映射也进入字典: 商品信息写别名, 字典输出规范写法。 */
    @Test
    void dictionaryOutput_appliesMaterialMapping() {
        insertMaterial("螺纹钢");
        try {
            ValueAlias alias = new ValueAlias();
            alias.setId(idSequence.incrementAndGet());
            alias.setDimension(ValueAliasDimension.MATERIAL);
            alias.setSourceValue("PG值映射材质");
            alias.setTargetValue("PG规范材质");
            repository.saveAndFlush(alias);

            assertThat(specCatalogQuery.find(null, "PG规范材质"))
                    .extracting(MaterialSpecCatalogQuery.MaterialSpecSnapshot::material)
                    .containsExactly("PG规范材质");
        } finally {
            jdbc.update("delete from public.md_material where material_code like 'PG-VALUE-ALIAS-DICT-%'");
        }
    }

    private void insertMaterial(String category) {
        jdbc.update("""
                insert into public.md_material (id, material_code, brand, material, category, spec, length,
                                                unit, piece_weight_ton, pieces_per_bundle, unit_price,
                                                created_by, created_name, deleted_flag)
                values (?, ?, 'PG品牌', 'PG值映射材质', ?, '12', '9m', '吨', 1.5, 100, 3000, 0, 'flyway-test', false)
                """, idSequence.incrementAndGet(), TEST_MATERIAL_CODE, category);
    }

    /** 迁移幂等性: 种子行按 (维度, 源值) 唯一, 不存在重复行。 */
    @Test
    void seedRow_isUnique() {
        Integer duplicates = jdbc.queryForObject("""
                select count(*) from (
                    select dimension, source_value
                    from public.md_value_alias
                    where deleted_flag = false
                    group by dimension, source_value
                    having count(*) > 1
                ) dup
                """, Integer.class);
        assertThat(duplicates).isZero();
    }

    /** 分页排序在真库可用: 白名单内的 sortBy 都能落成 SQL, 关键字命中源值/目标值。 */
    @Test
    void page_supportsWhitelistedSortsAndKeyword() {
        ValueAliasStore store = store();
        store.create(request("BRAND", "PG排序品牌B", "PG排序目标B"));
        store.create(request("BRAND", "PG排序品牌A", "PG排序目标A"));

        for (String sortBy : List.of("id", "dimension", "sourceValue", "targetValue", "createdAt", "updatedAt")) {
            for (String direction : List.of("asc", "desc")) {
                var page = store.page(new PageQuery(0, 20, sortBy, direction), "BRAND", null);
                assertThat(page.getContent()).as("sortBy=%s %s", sortBy, direction).isNotEmpty();
            }
        }

        var filtered = store.page(new PageQuery(0, 20, "sourceValue", "asc"), "BRAND", "PG排序品牌");
        assertThat(filtered.getContent()).extracting(ValueAliasResponse::sourceValue)
                .containsExactly("PG排序品牌A", "PG排序品牌B");

        var byTarget = store.page(new PageQuery(0, 20, "id", "desc"), "BRAND", "PG排序目标A");
        assertThat(byTarget.getContent()).extracting(ValueAliasResponse::targetValue)
                .containsExactly("PG排序目标A");

        // 非法维度筛选 → 422
        assertThatThrownBy(() -> store.page(new PageQuery(0, 20, null, null), "COLOR", null))
                .hasMessageContaining("维度只能是");
    }

    /** 真库归一化: 未配置映射的写法原样返回, 已配置的种子映射生效。 */
    @Test
    void normalize_unmappedValueStaysAsIs() {
        assertThat(valueAliasQuery.normalize(ValueAliasDimension.BRAND, " PG未配置品牌 "))
                .isEqualTo("PG未配置品牌");
        assertThat(valueAliasQuery.rules().normalizeCategory("直条")).isEqualTo("螺纹钢");
    }
}
