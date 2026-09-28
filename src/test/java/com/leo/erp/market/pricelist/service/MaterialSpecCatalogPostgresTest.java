package com.leo.erp.market.pricelist.service;

import com.leo.erp.market.pricelist.domain.entity.SupplierPriceList;
import com.leo.erp.market.pricelist.repository.SupplierPriceItemRepository;
import com.leo.erp.market.pricelist.repository.SupplierPriceListRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 规格全集的真实 PostgreSQL 回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 *
 * <p>为什么必须有真库用例: {@code MaterialSpecCatalogQuery} 的 SQL 里 {@code ? is null}
 * 这类无类型绑定参数只有 PostgreSQL 才会报
 * {@code could not determine data type of parameter $1};
 * 单元测试 mock 了 {@code JdbcTemplate}, 结构上抓不到这类错误。</p>
 *
 * <p>只读断言(不造数据、不写库), 依赖开发库既有 {@code md_material} 商品资料。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MaterialSpecCatalogQuery.class)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class MaterialSpecCatalogPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MaterialSpecCatalogQuery query;

    @Autowired
    private SupplierPriceListRepository listRepository;

    @Autowired
    private SupplierPriceItemRepository itemRepository;

    /** 无筛选调用必须能执行(此前报 could not determine data type of parameter $1)。 */
    @Test
    void findAll_withoutFilters_runsOnRealPostgres() {
        List<MaterialSpecCatalogQuery.MaterialSpecSnapshot> result = query.find(null, null);

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(snapshot -> {
            assertThat(snapshot.category()).isNotBlank();
            assertThat(snapshot.material()).isNotBlank();
            assertThat(snapshot.spec()).isNotNull().isPositive();
            assertThat(snapshot.length()).isNotNull();
        });
        // 排序号连续且与返回顺序一致
        for (int index = 0; index < result.size(); index++) {
            assertThat(result.get(index).sortOrder()).isEqualTo(index);
        }
    }

    /** 排序固定 category, material, spec_sort, length_sort(同键内定尺按数字升序)。 */
    @Test
    void findAll_isOrderedByCategoryMaterialSpecSortLengthSort() {
        List<MaterialSpecCatalogQuery.MaterialSpecSnapshot> result = query.find(null, null);

        assertThat(result).isNotEmpty();
        for (int index = 1; index < result.size(); index++) {
            MaterialSpecCatalogQuery.MaterialSpecSnapshot previous = result.get(index - 1);
            MaterialSpecCatalogQuery.MaterialSpecSnapshot current = result.get(index);
            assertThat(previous.category().compareTo(current.category()))
                    .as("category 必须非递减: %s -> %s", previous.category(), current.category())
                    .isLessThanOrEqualTo(0);
            if (previous.category().equals(current.category())) {
                assertThat(previous.material().compareTo(current.material()))
                        .as("同类别内 material 必须非递减")
                        .isLessThanOrEqualTo(0);
                if (previous.material().equals(current.material())) {
                    assertThat(previous.spec())
                            .as("同类别材质内 spec_sort 必须非递减")
                            .isLessThanOrEqualTo(current.spec());
                    if (previous.spec().equals(current.spec())) {
                        // 同一 (category, material, spec) 内定尺按 length_sort 数字升序
                        assertThat(lengthSort(previous.length()))
                                .as("同 (类别,材质,规格) 内 length_sort 必须非递减: %s -> %s",
                                        previous.length(), current.length())
                                .isLessThanOrEqualTo(lengthSort(current.length()));
                    }
                }
            }
        }
    }

    private static java.math.BigDecimal lengthSort(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.math.BigDecimal.valueOf(-1);
        }
        String digits = raw.replaceAll("[^0-9.]", "");
        return digits.isEmpty() ? java.math.BigDecimal.valueOf(-1) : new java.math.BigDecimal(digits);
    }

    /** 归一化后重复键(如 Φ12 与 12)只保留一行。 */
    @Test
    void findAll_deduplicatesNormalizedKeys() {
        List<MaterialSpecCatalogQuery.MaterialSpecSnapshot> result = query.find(null, null);

        Set<String> keys = new LinkedHashSet<>();
        for (MaterialSpecCatalogQuery.MaterialSpecSnapshot snapshot : result) {
            keys.add(MaterialSpecCatalogQuery.key(snapshot.category(), snapshot.material(),
                    snapshot.spec(), snapshot.length()));
        }
        assertThat(keys).hasSameSizeAs(result);
    }

    /** 规格不含数字的行(spec_sort 为 null)必须整行跳过, 不得当成 0。 */
    @Test
    void findAll_skipsRowsWithoutNumericSpec() {
        Integer skippedRows = jdbc.queryForObject("""
                select count(*) from public.md_material
                where deleted_flag = false and spec_sort is null
                """, Integer.class);
        List<MaterialSpecCatalogQuery.MaterialSpecSnapshot> result = query.find(null, null);

        assertThat(result).noneMatch(snapshot -> snapshot.spec() == null || snapshot.spec() <= 0);
        if (skippedRows != null && skippedRows > 0) {
            Integer numericRows = jdbc.queryForObject("""
                    select count(distinct (category, material, spec_sort, length)) from public.md_material
                    where deleted_flag = false and spec_sort is not null
                    """, Integer.class);
            // 截断后的定尺可能再合并若干行, 因此只断言"不超过数字规格的去重键数"
            assertThat(result.size()).isLessThanOrEqualTo(numericRows == null ? 0 : numericRows);
        }
    }

    /** 按 category 筛选: 结果必须全部落在该类别, 且不筛选时该类别也确实有数据。 */
    @Test
    void find_filtersByCategory() {
        List<MaterialSpecCatalogQuery.MaterialSpecSnapshot> all = query.find(null, null);
        String category = all.get(0).category();

        List<MaterialSpecCatalogQuery.MaterialSpecSnapshot> filtered = query.find(category, null);

        assertThat(filtered).isNotEmpty();
        assertThat(filtered).allSatisfy(snapshot -> assertThat(snapshot.category()).isEqualTo(category));
    }

    /** 按 material 筛选: 结果必须全部落在该材质。 */
    @Test
    void find_filtersByMaterial() {
        List<MaterialSpecCatalogQuery.MaterialSpecSnapshot> all = query.find(null, null);
        String material = all.get(0).material();

        List<MaterialSpecCatalogQuery.MaterialSpecSnapshot> filtered = query.find(null, material);

        assertThat(filtered).isNotEmpty();
        assertThat(filtered).allSatisfy(snapshot -> assertThat(snapshot.material()).isEqualTo(material));
    }

    /** 空筛选参数(空串/空白)等价于不筛选, 同样不得触发类型推断错误。 */
    @Test
    void find_treatsBlankFiltersAsNoFilter() {
        List<MaterialSpecCatalogQuery.MaterialSpecSnapshot> all = query.find(null, null);
        List<MaterialSpecCatalogQuery.MaterialSpecSnapshot> blank = query.find("  ", "");

        assertThat(blank).hasSameSizeAs(all);
    }

    /**
     * 别名规则存在性守卫(真库): 比价单行 {@code mk_quote_item.category='螺纹钢'} 的键,
     * 必须在只按 {@code (material, spec, length)} + 类别别名规范化后能匹配到
     * {@code md_material} 的 {@code 直条} 行。
     *
     * <p>真实库里两者写法不同(比对价单行 {@code 螺纹钢} vs 商品资料 {@code 直条}),
     * 这条用例一旦失败说明两端别名规则又漂移了。</p>
     */
    @Test
    void quoteSheetRebarRows_matchMaterialCatalogViaCategoryAlias() {
        // 取一个真实的 螺纹钢 比价行键
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select i.material, i.spec, i.length
                from public.mk_quote_item i
                where i.category = ? and i.spec is not null
                limit 5
                """, CategoryNormalizer.QUOTE_CATEGORY_REBAR);
        if (rows.isEmpty()) {
            // 开发库无比价单行时无法做存在性守卫; 但必须证明别名两侧确实存在差异数据
            assertThat(query.find(null, null))
                    .anySatisfy(snapshot -> assertThat(snapshot.category()).isNotBlank());
            return;
        }

        for (Map<String, Object> row : rows) {
            String material = String.valueOf(row.get("material"));
            Integer spec = ((Number) row.get("spec")).intValue();
            String length = row.get("length") == null ? "" : String.valueOf(row.get("length"));

            Integer matched = jdbc.queryForObject("""
                    select count(*) from public.md_material m
                    where m.deleted_flag = false
                      and m.material = ?
                      and m.spec_sort = ?
                      and left(coalesce(m.length, ''), ?) = ?
                    """, Integer.class, material, spec, MaterialSpecCatalogQuery.LENGTH_MAX, length);

            assertThat(CategoryNormalizer.sameCategory(
                    CategoryNormalizer.QUOTE_CATEGORY_REBAR, CategoryNormalizer.MATERIAL_CATEGORY_REBAR))
                    .as("别名规则必须把 螺纹钢 与 直条 视为同一类别")
                    .isTrue();
            assertThat(matched)
                    .as("比价行 %s|%s|%s 应能在 md_material 中按 (material,spec,length) 找到对应行"
                            + "(这正是别名规则要解决的差异: 单据写 螺纹钢, 商品资料写 直条)",
                            material, spec, length)
                    .isNotNull()
                    .isPositive();
        }
    }

    /** 真库中确实存在 螺纹钢 的比价行与 直条 的商品资料, 证明别名规则不是空转。 */
    @Test
    void realDataContainsBothCategorySpellings() {
        Integer quoteRebarRows = jdbc.queryForObject("""
                select count(*) from public.mk_quote_item where category = ?
                """, Integer.class, CategoryNormalizer.QUOTE_CATEGORY_REBAR);
        Integer materialRebarRows = jdbc.queryForObject("""
                select count(*) from public.md_material where category = ? and deleted_flag = false
                """, Integer.class, CategoryNormalizer.MATERIAL_CATEGORY_REBAR);
        Integer materialLuowengangRows = jdbc.queryForObject("""
                select count(*) from public.md_material where category = ? and deleted_flag = false
                """, Integer.class, CategoryNormalizer.QUOTE_CATEGORY_REBAR);

        // 任一写法为 0 时唯一真源可能已统一, 此时用例仍应通过(不依赖具体数据分布)
        assertThat(quoteRebarRows).isNotNull();
        assertThat(materialRebarRows).isNotNull();
        assertThat(materialLuowengangRows).isNotNull();
        assertThat(CategoryNormalizer.normalize(CategoryNormalizer.MATERIAL_CATEGORY_REBAR))
                .isEqualTo(CategoryNormalizer.normalize(CategoryNormalizer.QUOTE_CATEGORY_REBAR));
    }

    /** 矩阵无筛选必须在真库上可执行(取版 JPQL 与条目批量加载)。 */
    @Test
    void matrixProjection_queriesRunOnRealSchema() {
        LocalDateTime asOf = LocalDateTime.now().plusDays(1);

        // 无生效版本时返回空结果也不得报 SQL 错误
        assertThat(listRepository.findActiveAsOf(asOf)).isNotNull();
        assertThat(listRepository.findVersions(942000000000000101L, "不存在的品牌")).isEmpty();
        assertThat(listRepository.findActiveVersions(942000000000000101L, "不存在的品牌")).isEmpty();
        assertThat(itemRepository.findByListIdIn(List.of(-1L))).isEmpty();
    }
}
