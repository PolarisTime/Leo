package com.leo.erp.market.pricelist.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 规格键字典(商品信息去品牌 ∪ 比价单实际行键)边界测试:
 * 商品信息"后续新增"即时可见、品牌差异只出一行、比价单独有键保留、
 * 定尺写法归一({@code -}/空/{@code 9m}/{@code 9 米})、类别别名不拆行、排序与筛选。
 */
@ExtendWith(MockitoExtension.class)
class MaterialSpecCatalogQueryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    /** 商品信息(去品牌)与比价单行键合并后: 品牌差异只一行、比价单独有键保留、定尺写法归一。 */
    @Test
    @SuppressWarnings("unchecked")
    void find_mergesMaterialDictionaryAndQuoteItemsWithDedupeAndNormalization() throws Exception {
        // 商品信息: 同一 (类别, 材质, 规格, 定尺) 因品牌不同会返回多行(Φ12/12 同理);
        // 直条 与 螺纹钢 两种写法必须归并为同一行
        when(jdbcTemplate.query(contains("md_material"), any(RowMapper.class)))
                .thenAnswer(invocation -> mapRows(invocation.getArgument(1),
                        row("直条", "抗震钢E", 12, "9米"),
                        row("直条", "抗震钢E", 12, "9米"),
                        row("螺纹钢", "抗震钢E", 12, "9 m"),
                        row("盘螺", "HRB400E", 8, "-"),
                        row("盘螺", "HRB400E", 8, null),
                        // 规格无数字(商品信息 spec_sort 为空) → 整行跳过
                        row("螺纹钢", "其他", null, "9米")));
        // 比价单: 与商品信息重复的键去重, 独有键必须保留, 定尺 9M 归一为 9米
        when(jdbcTemplate.query(contains("mk_quote_item"), any(RowMapper.class)))
                .thenAnswer(invocation -> mapRows(invocation.getArgument(1),
                        row("螺纹钢", "抗震钢E", 12, "9M"),
                        row("螺纹钢", "HRB400", 10, "9米"),
                        row("盘螺", "HRB400E", 6, "-")));

        List<MaterialSpecCatalogQuery.MaterialSpecSnapshot> result =
                new MaterialSpecCatalogQuery(jdbcTemplate).find(null, null);

        // 去重 + 归一: 直条/螺纹钢 合成一行; 9米/9 m/9M 合成一行; -/NULL 合成一行(空串)
        assertThat(result).noneSatisfy(row -> assertThat(row.category()).isEqualTo("直条"));
        assertThat(result).extracting(MaterialSpecCatalogQuery.MaterialSpecSnapshot::category)
                .containsOnly("螺纹钢", "盘螺");
        // 排序: 类别(盘螺 < 螺纹钢) → 材质(HRB400 < HRB400E < 抗震钢E) → 规格 → 定尺数值
        assertThat(result).extracting(row -> row.category() + "|" + row.material()
                        + "|" + row.spec() + "|" + row.length())
                .containsExactly(
                        "盘螺|HRB400E|6|",      // 比价单单独有键, 定尺 - → 空串
                        "盘螺|HRB400E|8|",      // 商品信息 -/NULL 归并为一行
                        "螺纹钢|HRB400|10|9米",
                        "螺纹钢|抗震钢E|12|9米");
        assertThat(result).extracting(MaterialSpecCatalogQuery.MaterialSpecSnapshot::sortOrder)
                .containsExactly(0, 1, 2, 3);
        assertThat(result).filteredOn(row -> "抗震钢E".equals(row.material())).hasSize(1);
    }

    /** 定尺归一: {@code -} / 空 / NULL → 空串; {@code 9m}/{@code 9M}/{@code 9 米} → {@code 9米}; 超长截断。 */
    @Test
    void normalizeLength_unifiesSpellingsAndTruncates() {
        assertThat(MaterialSpecCatalogQuery.normalizeLength(null)).isEmpty();
        assertThat(MaterialSpecCatalogQuery.normalizeLength("")).isEmpty();
        assertThat(MaterialSpecCatalogQuery.normalizeLength("   ")).isEmpty();
        assertThat(MaterialSpecCatalogQuery.normalizeLength("-")).isEmpty();
        assertThat(MaterialSpecCatalogQuery.normalizeLength(" - ")).isEmpty();
        assertThat(MaterialSpecCatalogQuery.normalizeLength("9米")).isEqualTo("9米");
        assertThat(MaterialSpecCatalogQuery.normalizeLength("9m")).isEqualTo("9米");
        assertThat(MaterialSpecCatalogQuery.normalizeLength("9M")).isEqualTo("9米");
        assertThat(MaterialSpecCatalogQuery.normalizeLength("9 米")).isEqualTo("9米");
        assertThat(MaterialSpecCatalogQuery.normalizeLength(" 12 m ")).isEqualTo("12米");
        // 非"数字+单位"写法原样保留(不做猜测)
        assertThat(MaterialSpecCatalogQuery.normalizeLength("9")).isEqualTo("9");
        assertThat(MaterialSpecCatalogQuery.normalizeLength("无")).isEqualTo("无");
        // 入库列宽 varchar(16)
        assertThat(MaterialSpecCatalogQuery.normalizeLength("1234567890ABCDEFGHIJ"))
                .isEqualTo("1234567890ABCDEF");
    }

    /** 商品信息新增物料(第二次查询才出现)必须立即进入字典 —— 字典是实时投影。 */
    @Test
    @SuppressWarnings("unchecked")
    void find_reflectsMaterialAddedAfterFirstQuery() throws Exception {
        AtomicInteger materialQueries = new AtomicInteger();
        when(jdbcTemplate.query(contains("md_material"), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<MaterialSpecCatalogQuery.MaterialSpecSnapshot> mapper =
                            invocation.getArgument(1);
                    return materialQueries.getAndIncrement() == 0
                            ? mapRows(mapper, row("螺纹钢", "HRB400E", 12, "9米"))
                            : mapRows(mapper, row("螺纹钢", "HRB400E", 12, "9米"),
                                    row("螺纹钢", "HRB400E", 14, "12米"));
                });
        when(jdbcTemplate.query(contains("mk_quote_item"), any(RowMapper.class)))
                .thenReturn(List.of());
        MaterialSpecCatalogQuery query = new MaterialSpecCatalogQuery(jdbcTemplate);

        assertThat(query.findAll()).hasSize(1);
        assertThat(query.findAll())
                .extracting(MaterialSpecCatalogQuery.MaterialSpecSnapshot::spec)
                .containsExactly(12, 14);
    }

    /** 类别筛选按别名等价: 传 螺纹钢 也能命中 直条 来源; 材质空白视为不筛选。 */
    @Test
    @SuppressWarnings("unchecked")
    void find_matchesCategoryAliasAndTrimsMaterial() throws Exception {
        when(jdbcTemplate.query(contains("md_material"), any(RowMapper.class)))
                .thenAnswer(invocation -> mapRows(invocation.getArgument(1),
                        row("直条", "抗震钢E", 12, "9米"),
                        row("盘螺", "HRB400E", 8, "-")));
        when(jdbcTemplate.query(contains("mk_quote_item"), any(RowMapper.class)))
                .thenReturn(List.of());

        List<MaterialSpecCatalogQuery.MaterialSpecSnapshot> result =
                new MaterialSpecCatalogQuery(jdbcTemplate).find("螺纹钢", " ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).category()).isEqualTo("螺纹钢");
        assertThat(result.get(0).material()).isEqualTo("抗震钢E");
    }

    @Test
    void key_isStableAcrossNormalization() {
        assertThat(MaterialSpecCatalogQuery.key("螺纹钢", "抗震钢E", 12, "9米"))
                .isEqualTo("螺纹钢|抗震钢E|12|9米");
        assertThat(MaterialSpecCatalogQuery.lengthSortKey("12米")).isEqualTo("12");
        assertThat(MaterialSpecCatalogQuery.lengthSortKey("无")).isEmpty();
        assertThat(CategoryNormalizer.sameCategory("直条", "螺纹钢")).isTrue();
    }

    // ---------------------------------------------------------------- 测试夹具

    /** 用传入的 mapper 映射预置行(模拟 SQL 已去品牌返回 distinct 行)。 */
    private static <T> List<T> mapRows(RowMapper<T> mapper, ResultSet... rows) {
        List<T> mapped = new ArrayList<>();
        try {
            for (int index = 0; index < rows.length; index++) {
                mapped.add(mapper.mapRow(rows[index], index));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
        return mapped;
    }

    /**
     * 同一夹具同时服务商品信息(读 spec/length_raw)与比价单(读 spec/length)两条 SQL 的映射,
     * 因此用 lenient 桩, 避免严格模式下未读取的列被判为多余桩。
     */
    private static ResultSet row(String category, String material, Integer spec, String length)
            throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        lenient().when(rs.getString("category")).thenReturn(category);
        lenient().when(rs.getString("material")).thenReturn(material);
        lenient().when(rs.getObject("spec")).thenReturn(spec);
        lenient().when(rs.getString("length")).thenReturn(length);
        lenient().when(rs.getString("length_raw")).thenReturn(length);
        return rs;
    }
}
