package com.leo.erp.market.pricelist.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 规格全集归一化边界测试: 规格归一化为整数、非数字规格跳过、定尺截断 16、重复键去重、排序号连续。
 */
@ExtendWith(MockitoExtension.class)
class MaterialSpecCatalogQueryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void find_normalizesSpecSkipsNonNumericAndDeduplicatesKeys() throws Exception {
        // 模拟数据库已按 category, material, spec_sort, length_sort 排序返回
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    RowMapper<MaterialSpecCatalogQuery.MaterialSpecSnapshot> mapper =
                            invocation.getArgument(1);
                    List<MaterialSpecCatalogQuery.MaterialSpecSnapshot> mapped = new ArrayList<>();
                    // Φ12 / 12 归一化后同为 12 → 只保留一行
                    mapped.add(mapper.mapRow(row("螺纹钢", "抗震钢E", 12, "9米"), 0));
                    mapped.add(mapper.mapRow(row("螺纹钢", "抗震钢E", 12, "9米"), 1));
                    // "无"/"其他" 不含数字 → spec_sort 为 null, 必须整行跳过而不是当成 0
                    mapped.add(mapper.mapRow(row("螺纹钢", "其他", null, "9米"), 2));
                    // 定尺 varchar(32) 超 16 必须截断, 保证与价格条目 varchar(16) 键一致
                    mapped.add(mapper.mapRow(row("盘螺", "HRB400E", 8, "1234567890ABCDEFGHIJ"), 3));
                    mapped.add(mapper.mapRow(row("盘螺", "HRB400E", 10, ""), 4));
                    return mapped;
                });

        List<MaterialSpecCatalogQuery.MaterialSpecSnapshot> result =
                new MaterialSpecCatalogQuery(jdbcTemplate).find(null, null);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).category()).isEqualTo("螺纹钢");
        assertThat(result.get(0).spec()).isEqualTo(12);
        assertThat(result.get(0).sortOrder()).isEqualTo(0);
        assertThat(result.get(1).length()).isEqualTo("1234567890ABCDEF");
        assertThat(result.get(1).sortOrder()).isEqualTo(1);
        assertThat(result.get(2).length()).isEmpty();
        assertThat(result).noneMatch(snapshot -> "其他".equals(snapshot.material()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void find_passesFiltersThroughToSqlParameters() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any()))
                .thenReturn(List.of());

        new MaterialSpecCatalogQuery(jdbcTemplate).find("螺纹钢", " ");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        org.mockito.Mockito.verify(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), captor.capture(), captor.capture(),
                        captor.capture(), captor.capture());
        List<Object> params = captor.getAllValues();
        assertThat(params.get(0)).isEqualTo("螺纹钢");
        assertThat(params.get(1)).isEqualTo("螺纹钢");
        // 空白材质视为不筛选(null)
        assertThat(params.get(2)).isNull();
        assertThat(params.get(3)).isNull();
    }

    @Test
    void key_isStableAcrossNormalization() {
        assertThat(MaterialSpecCatalogQuery.key("螺纹钢", "抗震钢E", 12, "9米"))
                .isEqualTo("螺纹钢|抗震钢E|12|9米");
        assertThat(MaterialSpecCatalogQuery.lengthSortKey("12米")).isEqualTo("12");
        assertThat(MaterialSpecCatalogQuery.lengthSortKey("无")).isEmpty();
    }

    private static ResultSet row(String category, String material, Integer spec, String length)
            throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("category")).thenReturn(category);
        when(rs.getString("material")).thenReturn(material);
        when(rs.getObject("spec_sort")).thenReturn(spec);
        when(rs.getString("length_raw")).thenReturn(length);
        return rs;
    }
}
