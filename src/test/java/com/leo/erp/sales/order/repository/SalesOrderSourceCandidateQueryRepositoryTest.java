package com.leo.erp.sales.order.repository;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.sales.order.web.dto.SalesOrderSourceCandidateResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售来源候选查询语义测试（6.1B）：
 * 候选只要求来源采购入库单有效且有剩余量，不再要求采购订单“完成采购”。
 * <p>
 * 本仓库未引入 H2/Testcontainers，无法执行 PostgreSQL 相关子查询，故此处对生成的 SQL 做结构断言。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderSourceCandidateQueryRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private SalesOrderSourceCandidateQueryRepository repository;

    @Test
    void page_shouldNotRequirePurchaseCompletedAndKeepInboundEligibility() {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.page("钢材", null, null, null, null, null, new PageQuery(0, 30, null, null));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        String sql = sqlCaptor.getValue();
        assertThat(sql).doesNotContain("purchase_order.status = '完成采购'");
        assertThat(sql).contains("inbound.status IN ('已审核', '完成入库')");
        assertThat(sql).contains("inbound.deleted_flag = FALSE");
        assertThat(sql).contains("remaining_quantity > 0");
    }

    @Test
    void page_shouldReturnEmptyPageWhenNoCandidates() {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(null);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        var page = repository.page(null, null, null, null, null, null, new PageQuery(0, 30, null, null));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void page_shouldGroupLinesByPurchaseOrder() {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        var page = repository.page(null, null, null, null, null, null, new PageQuery(0, 30, null, null));

        assertThat(page.content()).isInstanceOf(List.class);
        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.totalPages()).isEqualTo(1);
    }
}
