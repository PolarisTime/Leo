package com.leo.erp.purchase.order.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采购订单引用/货值聚合口径测试。
 *
 * <p>本仓库未引入 H2/Testcontainers, 无法执行 PostgreSQL 相关子查询, 故此处对生成的 SQL
 * 做结构断言; 逐行取整的行为另以真实数据核对(暂定重量与实测重量一致时差额必须为 0)。</p>
 */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderReferenceQueryRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private PurchaseOrderReferenceQueryRepository repository;

    private String captureSql() {
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.findByOrderIds(List.of(1L));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        return sqlCaptor.getValue();
    }

    @Test
    void actualAmount_shouldRoundEachLineToCentsBeforeSumming() {
        String sql = captureSql();

        // 暂定金额是逐行 amount(numeric(14,2)) 之和: 实际货值必须同口径, 否则同一批重量
        // 会因行级取整产生 ±0.01 伪差额。
        assertThat(sql).contains("SUM(ROUND(poi.actual_weight_ton * poi.unit_price, 2))");
        assertThat(sql).doesNotContain("SUM(poi.actual_weight_ton * poi.unit_price)");
    }

    @Test
    void amountDifference_shouldSettlePerLineAgainstProvisionalLineAmount() {
        String sql = captureSql();

        assertThat(sql).contains("AS actual_amount");
        // 逐行结算(行实际金额 − 行暂定金额): 全部过磅且无附加费用时等价于 实际货值 − 暂定金额,
        // 部分过磅时不会把未入库货物算成一笔退款。
        assertThat(sql).contains("- COALESCE(poi.amount, 0)) END");
        assertThat(sql).doesNotContain("- COALESCE(po.total_amount, 0) AS amount_difference");
        assertThat(sql).contains("AS amount_difference");
    }

    @Test
    void query_shouldReturnNullAmountsWhenNothingWeighed() {
        String sql = captureSql();

        // 一行都没过磅时返回 NULL(前端显示 —), 而不是 0: 否则"0 − 暂定金额"会把整单
        // 预付款显示成一笔退款。
        assertThat(sql).contains("WHEN COUNT(poi.actual_weight_ton) = 0 THEN NULL");
        assertThat(sql).doesNotContain("AND poi.actual_weight_ton IS NOT NULL");
    }

    @Test
    void findByOrderIds_shouldReturnEmptyMapWithoutQueryingWhenIdsMissing() {
        assertThat(repository.findByOrderIds(List.of())).isEmpty();
        assertThat(repository.findByOrderIds(null)).isEmpty();

        verify(jdbcTemplate, never())
                .query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
    }
}
