package com.leo.erp.sales.order.repository;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 出库导入候选查询语义测试：候选人应包含“尚有明细未出满”的订单，
 * 而不是命中任意出库即排除整个订单。
 * <p>
 * 本仓库未引入 H2/Testcontainers，无法执行 PostgreSQL 相关子查询，故此处对生成的 SQL 做结构断言。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderOutboundCandidateQueryRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private SalesOrderOutboundCandidateQueryRepository repository;

    @Test
    void pageIds_shouldFilterByPerItemRemainingInsteadOfAnyOutboundExistence() {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(List.of());

        repository.pageIds(
                new PageQuery(0, 30, null, null),
                PageFilter.of(null, null, null, null, null, null, null));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce())
                .queryForObject(sqlCaptor.capture(), any(SqlParameterSource.class), eq(Long.class));
        String sql = sqlCaptor.getValue();

        assertThat(sql).doesNotContain("NOT EXISTS");
        assertThat(sql).contains("COALESCE(source_item.quantity, 0) > COALESCE((");
        assertThat(sql).contains("outbound_item.source_sales_order_item_id = source_item.id");
    }
}
