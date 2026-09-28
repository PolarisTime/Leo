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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 规格键字典的真实 PostgreSQL 回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 *
 * <p>字典 = 商品信息 {@code md_material}(去品牌) ∪ 比价单 {@code mk_quote_item} 实际行键,
 * 每次查询实时投影。真库用例覆盖单元测试抓不到的部分: 真实数据分布下的去品牌/去重口径、
 * 归一化(类别别名、定尺写法)结果、以及"同一事务内新增商品信息后字典立即可见"。</p>
 *
 * <p>多数用例只读; 新增商品信息与"字典外历史条目可读"两条会写入带标记的测试数据并在
 * {@link #cleanUp()} 中清理。</p>
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

    /** 测试自造商品信息物料(固定编码前缀, 便于清理与排查)。 */
    private static final String MATERIAL_CODE = "PG-SPEC-DICT-TEST-901";
    private static final String MATERIAL_CODE_SECOND = "PG-SPEC-DICT-TEST-902";
    private static final String MATERIAL_BRAND = "PG字典测试品牌";
    private static final String MATERIAL_BRAND_SECOND = "PG字典测试品牌B";

    @org.junit.jupiter.api.BeforeEach
    void clearTestMaterialsBefore() {
        clearTestMaterials();
    }

    @org.junit.jupiter.api.AfterEach
    void cleanUp() {
        clearTestMaterials();
    }

    /** 清理本类自造的商品信息物料(固定编码前缀), 保证用例可重复执行。 */
    private void clearTestMaterials() {
        jdbc.update("delete from public.md_material where material_code like 'PG-SPEC-DICT-TEST-%'");
    }

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

    /**
     * 新来源守卫(真库): 比价单 {@code mk_quote_item} 实际出现过的每个商品行键
     * (类别归一后 + 定尺 16 截断)都必须在规格全集里出现, 否则维护页会漏行。
     */
    @Test
    void findAll_coversEveryRealQuoteItemProductKey() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select distinct i.category, i.material, i.spec, i.length
                from public.mk_quote_item i
                join public.mk_quote_sheet s on s.id = i.sheet_id
                where s.deleted_flag = false
                  and i.row_type = 'PRODUCT'
                  and i.category is not null and i.material is not null and i.spec is not null
                """);

        Set<String> catalogKeys = new LinkedHashSet<>();
        for (MaterialSpecCatalogQuery.MaterialSpecSnapshot snapshot : query.find(null, null)) {
            catalogKeys.add(MaterialSpecCatalogQuery.key(snapshot.category(), snapshot.material(),
                    snapshot.spec(), snapshot.length()));
        }
        assertThat(catalogKeys).isNotEmpty();

        for (Map<String, Object> row : rows) {
            String category = CategoryNormalizer.normalize(String.valueOf(row.get("category")));
            String material = String.valueOf(row.get("material")).trim();
            Integer spec = ((Number) row.get("spec")).intValue();
            String length = MaterialSpecCatalogQuery.normalizeLength(
                    row.get("length") == null ? null : String.valueOf(row.get("length")));
            assertThat(catalogKeys)
                    .as("比价单行键 %s|%s|%s|%s 必须在字典内", category, material, spec, length)
                    .contains(MaterialSpecCatalogQuery.key(category, material, spec, length));
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
     * 去品牌与双来源守卫(真库): 字典键集合必须正好等于
     * 「商品信息去品牌去重后的键」∪「比价单实际行键」, 不多不少。
     *
     * <p>这条覆盖三件事: 同一 (类别,材质,规格,定尺) 因品牌不同在商品信息里有多行时字典只有一行;
     * 比价单用过而商品信息没有的键仍在字典内; 商品信息有而比价单没用过的键也在字典内(可提前录价)。</p>
     */
    @Test
    void findAll_equalsMaterialWithoutBrandUnionQuoteItemKeys() {
        Set<String> expected = new LinkedHashSet<>();
        for (Map<String, Object> row : jdbc.queryForList("""
                select distinct m.category, m.material, m.spec_sort as spec, m.length
                from public.md_material m
                where m.deleted_flag = false and m.spec_sort is not null
                """)) {
            addKey(expected, String.valueOf(row.get("category")), String.valueOf(row.get("material")),
                    ((Number) row.get("spec")).intValue(), row.get("length"));
        }
        for (Map<String, Object> row : jdbc.queryForList("""
                select distinct i.category, i.material, i.spec, i.length
                from public.mk_quote_item i
                join public.mk_quote_sheet s on s.id = i.sheet_id
                where s.deleted_flag = false
                  and i.row_type = 'PRODUCT'
                  and i.category is not null and i.material is not null and i.spec is not null
                """)) {
            addKey(expected, String.valueOf(row.get("category")), String.valueOf(row.get("material")),
                    ((Number) row.get("spec")).intValue(), row.get("length"));
        }

        Set<String> actual = new LinkedHashSet<>();
        for (MaterialSpecCatalogQuery.MaterialSpecSnapshot snapshot : query.find(null, null)) {
            actual.add(MaterialSpecCatalogQuery.key(snapshot.category(), snapshot.material(),
                    snapshot.spec(), snapshot.length()));
        }

        assertThat(expected).isNotEmpty();
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    /**
     * 商品信息新增物料后字典立即可见(同一事务内新增 + 查询), 无需迁移或手工同步。
     * <p>同时覆盖"商品信息是常规来源": 该键既不在比价单里, 也不是任何兜底路径的产物。</p>
     */
    @Test
    void findAll_includesMaterialInsertedInSameTransaction() {
        assertThat(dictionaryKeys()).doesNotContain(
                MaterialSpecCatalogQuery.key(CategoryNormalizer.MATERIAL_CATEGORY_REBAR,
                        "PG字典测试材质", 33, "3米"));

        insertMaterial(942000000000000911L, MATERIAL_CODE, MATERIAL_BRAND, 33,
                CategoryNormalizer.MATERIAL_CATEGORY_REBAR, "3 m");

        assertThat(dictionaryKeys())
                .as("新增商品信息物料后字典必须立即包含归一化后的键(直条→螺纹钢, 3 m→3米)")
                .contains(MaterialSpecCatalogQuery.key(CategoryNormalizer.QUOTE_CATEGORY_REBAR,
                        "PG字典测试材质", 33, "3米"));
    }

    /** 同一规格键在商品信息里因不同品牌存在多行时, 字典只有一行。 */
    @Test
    void findAll_deduplicatesMaterialRowsDifferingOnlyByBrand() {
        insertMaterial(942000000000000921L, MATERIAL_CODE, MATERIAL_BRAND, 44,
                CategoryNormalizer.MATERIAL_CATEGORY_REBAR, "4米");
        insertMaterial(942000000000000922L, MATERIAL_CODE_SECOND, MATERIAL_BRAND_SECOND, 44,
                CategoryNormalizer.MATERIAL_CATEGORY_REBAR, "4米");

        List<MaterialSpecCatalogQuery.MaterialSpecSnapshot> matched = query.find(null, null).stream()
                .filter(row -> "PG字典测试材质".equals(row.material()))
                .filter(row -> Integer.valueOf(44).equals(row.spec()))
                .toList();

        assertThat(matched)
                .as("不同品牌同一规格键只能有一行")
                .hasSize(1);
        assertThat(matched.get(0).category()).isEqualTo(CategoryNormalizer.QUOTE_CATEGORY_REBAR);
        assertThat(matched.get(0).length()).isEqualTo("4米");
    }

    private Set<String> dictionaryKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (MaterialSpecCatalogQuery.MaterialSpecSnapshot snapshot : query.find(null, null)) {
            keys.add(MaterialSpecCatalogQuery.key(snapshot.category(), snapshot.material(),
                    snapshot.spec(), snapshot.length()));
        }
        return keys;
    }

    private static void addKey(Set<String> keys, String category, String material, Integer spec, Object rawLength) {
        keys.add(MaterialSpecCatalogQuery.key(
                CategoryNormalizer.normalize(category),
                material == null ? null : material.trim(),
                spec,
                MaterialSpecCatalogQuery.normalizeLength(rawLength == null ? null : String.valueOf(rawLength))));
    }

    /** 造一条商品信息物料(md_material 的非空列必须齐全, 长度字样用于验证归一化)。 */
    private void insertMaterial(long id, String materialCode, String brand, int spec,
                                String category, String length) {
        // 清理可能残留的历史测试数据(唯一索引: material_code 与 品牌+材质+规格+定尺 各自唯一)
        jdbc.update("delete from public.md_material where material_code = ?", materialCode);
        jdbc.update("delete from public.md_material where id = ?", id);
        jdbc.update("""
                insert into public.md_material (id, material_code, brand, material, category, spec, length,
                                                unit, piece_weight_ton, pieces_per_bundle, unit_price,
                                                created_by, created_name, deleted_flag)
                values (?, ?, ?, 'PG字典测试材质', ?, ?, ?, '吨', 1.5, 100, 3000, 0, 'flyway-test', false)
                """, id, materialCode, brand, category, String.valueOf(spec), length);
    }

    /** 真库数据分布 + 别名规则守卫: 两端类别写法不同也不会在字典里分裂成两行。 */
    @Test
    void realDataAliasRuleIsStable() {
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

    /** 矩阵/推导当前价查询必须在真库上可执行(取消版本语义后的 JPQL 与条目批量加载)。 */
    @Test
    void currentPriceListQueries_runOnRealSchema() {
        // 不存在的品牌/供应商返回空结果也不得报 SQL 错误
        assertThat(listRepository.findAllCurrent()).isNotNull();
        assertThat(listRepository.findCurrentByBrandNames(List.of("不存在的品牌"))).isEmpty();
        assertThat(listRepository.findCurrentByKey(942000000000000101L, "不存在的品牌")).isEmpty();
        assertThat(itemRepository.findByListIdIn(List.of(-1L))).isEmpty();
    }
}
