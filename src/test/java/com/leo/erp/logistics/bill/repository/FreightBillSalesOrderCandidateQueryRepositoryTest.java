package com.leo.erp.logistics.bill.repository;

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
 * 物流候选查询语义测试：以 {@code lg_freight_bill_item} 行级聚合判断剩余量，
 * 不再引用已删除的 {@code lg_freight_bill_source_item}；当前单自身占用需被排除。
 * <p>
 * 本仓库未引入 H2/Testcontainers，无法执行 PostgreSQL 相关子查询，故此处对生成的 SQL 做结构断言。
 */
@ExtendWith(MockitoExtension.class)
class FreightBillSalesOrderCandidateQueryRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private FreightBillSalesOrderCandidateQueryRepository repository;

    @Test
    void pageIds_shouldAggregateRemainingFromFreightBillItems() {
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(List.of());

        repository.pageIds(new PageQuery(0, 30, null, null), PageFilter.byKeyword(null));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce())
                .queryForObject(sqlCaptor.capture(), any(SqlParameterSource.class), eq(Long.class));
        String sql = sqlCaptor.getValue();

        assertThat(sql).doesNotContain("lg_freight_bill_source_item");
        assertThat(sql).contains("FROM lg_freight_bill_item item");
        assertThat(sql).contains("item.source_sales_order_item_id = source_item.id");
        assertThat(sql).contains("bill.deleted_flag = FALSE");
        assertThat(sql).contains(":currentRecordId IS NULL OR bill.id <> :currentRecordId");
    }
}
