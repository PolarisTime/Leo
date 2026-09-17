package com.leo.erp.search.flow.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.search.flow.web.dto.DocumentFlowLink;
import com.leo.erp.search.flow.web.dto.DocumentFlowNode;
import com.leo.erp.search.flow.web.dto.DocumentFlowResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DocumentFlowService 单据流装配测试(分层批量查询版)。
 * <p>
 * 覆盖：空单号与未命中单号的错误语义、采购订单 → 采购入库 → 销售订单 → 销售出库 → 物流单
 * 的节点/关系边装配、以及雪花 ID 十进制字符串化与多值引用拆分。
 * <p>
 * 通过按 SQL 特征分派 mock 结果, 验证分层批量反查/按单号查询能正确装配整条链路。
 */
@ExtendWith(MockitoExtension.class)
class DocumentFlowServiceTest {

    private static final long ORDER_ID = 9007199254740993L;
    private static final long INBOUND_ID = 9007199254740995L;
    private static final long SALES_ORDER_ID = 9007199254740997L;
    private static final long OUTBOUND_ID = 9007199254740999L;
    private static final long FREIGHT_BILL_ID = 9007199254741001L;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void stubQueries() {
        // seeds: findByNo 走 RowMapper, 仅采购订单按单号命中
        lenient().when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    RowMapper<Object> mapper = invocation.getArgument(2);
                    if (sql.contains("FROM po_purchase_order") && sql.contains("order_no = :no")) {
                        return List.of(mapper.mapRow(row(ORDER_ID, "PO-1", null), 0));
                    }
                    return List.of();
                });

        // 分层批量查询走 RowCallbackHandler, 按 SQL 特征分派
        lenient().doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            RowCallbackHandler handler = invocation.getArgument(2);
            ResultSet row = null;
            if (sql.contains("LIKE :p")) {
                if (sql.contains("FROM po_purchase_inbound")) {
                    row = row(INBOUND_ID, "IN-1", "PO-1");
                } else if (sql.contains("FROM so_sales_order") && sql.contains("purchase_inbound_no LIKE")) {
                    row = row(SALES_ORDER_ID, "SO-1", "IN-1");
                } else if (sql.contains("FROM so_sales_outbound")) {
                    row = row(OUTBOUND_ID, "OUT-1", "SO-1");
                } else if (sql.contains("FROM lg_freight_bill bill") && sql.contains("bill_item.source_no LIKE")) {
                    row = row(FREIGHT_BILL_ID, "BILL-1", "SO-1");
                }
            } else if (sql.contains(" AS ref_value FROM")) {
                if (sql.contains("FROM po_purchase_inbound")) {
                    row = row(INBOUND_ID, null, "PO-1");
                } else if (sql.contains("FROM so_sales_order") && sql.contains("purchase_inbound_no AS ref_value")) {
                    row = row(SALES_ORDER_ID, null, "IN-1");
                } else if (sql.contains("FROM so_sales_order") && sql.contains("purchase_order_no AS ref_value")) {
                    row = row(SALES_ORDER_ID, null, null);
                } else if (sql.contains("FROM so_sales_outbound")) {
                    row = row(OUTBOUND_ID, null, "SO-1");
                }
            } else if (sql.contains(" IN (:nos)")) {
                if (sql.contains("FROM po_purchase_order")) {
                    row = row(ORDER_ID, "PO-1", null);
                } else if (sql.contains("FROM po_purchase_inbound")) {
                    row = row(INBOUND_ID, "IN-1", null);
                } else if (sql.contains("FROM so_sales_order")) {
                    row = row(SALES_ORDER_ID, "SO-1", null);
                }
            } else if (sql.contains(" IN (:ids)") && sql.contains("outbound_item.outbound_id IN")) {
                // 出库 → 物流; 出库 → 退货返回空(无退货)
                if (!sql.contains("FROM so_sales_return ret")) {
                    row = rowWithSource(FREIGHT_BILL_ID, "BILL-1", OUTBOUND_ID);
                }
            }
            if (row != null) {
                handler.processRow(row);
            }
            return null;
        }).when(jdbcTemplate).query(anyString(), any(SqlParameterSource.class), any(RowCallbackHandler.class));
    }

    @Test
    void splitReferences_shouldSplitMultipleSeparatorsAndIgnoreBlanks() {
        assertThat(DocumentFlowService.splitReferences("PO-1, PO-2")).containsExactly("PO-1", "PO-2");
        assertThat(DocumentFlowService.splitReferences("PO-1，PO-2、PO-3;PO-4 PO-5"))
                .containsExactly("PO-1", "PO-2", "PO-3", "PO-4", "PO-5");
        assertThat(DocumentFlowService.splitReferences("  ")).isEmpty();
        assertThat(DocumentFlowService.splitReferences(null)).isEmpty();
    }

    @Test
    void documentFlow_shouldRejectBlankDocumentNo() {
        DocumentFlowService service = new DocumentFlowService(jdbcTemplate);

        assertThatThrownBy(() -> service.documentFlow("  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("单号不能为空");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void documentFlow_shouldThrowNotFoundWhenNoDocumentMatches() {
        // 覆盖 mock: 任意单号都未命中
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        DocumentFlowService service = new DocumentFlowService(jdbcTemplate);

        assertThatThrownBy(() -> service.documentFlow("UNKNOWN-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到单号对应的单据");
    }

    @Test
    void documentFlow_shouldBuildCoreChainWithStringifiedSnowflakeIds() {
        DocumentFlowResponse response = new DocumentFlowService(jdbcTemplate).documentFlow("PO-1");

        assertThat(response.documentNo()).isEqualTo("PO-1");
        assertThat(response.truncated()).isFalse();
        assertThat(response.nodes()).extracting(DocumentFlowNode::type)
                .containsExactly("purchase-order", "purchase-inbound", "sales-order", "sales-outbound", "freight-bill");
        assertThat(response.nodes()).extracting(DocumentFlowNode::id)
                .containsExactly(
                        Long.toString(ORDER_ID),
                        Long.toString(INBOUND_ID),
                        Long.toString(SALES_ORDER_ID),
                        Long.toString(OUTBOUND_ID),
                        Long.toString(FREIGHT_BILL_ID));
        assertThat(response.links()).extracting(DocumentFlowLink::linkType)
                .containsExactly("入库", "销售", "出库", "物流", "物流");
        // 两条物流边: 销售单按 sales_order_no 引用(sales-order → freight-bill),
        // 销售出库按明细关联(sales-outbound → freight-bill)。
        assertThat(response.links().get(3).fromType()).isEqualTo("sales-order");
        assertThat(response.links().get(3).toType()).isEqualTo("freight-bill");
        assertThat(response.links().get(4).fromType()).isEqualTo("sales-outbound");
        assertThat(response.links().get(4).toType()).isEqualTo("freight-bill");
    }

    private static ResultSet row(long id, String no, String refValue) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        lenient().when(resultSet.getLong("id")).thenReturn(id);
        lenient().when(resultSet.getString("no")).thenReturn(no);
        lenient().when(resultSet.getString("status")).thenReturn("已审核");
        lenient().when(resultSet.getBigDecimal("amount")).thenReturn(new BigDecimal("1000.00"));
        lenient().when(resultSet.getBigDecimal("weight")).thenReturn(new BigDecimal("10.00000000"));
        lenient().when(resultSet.getObject("business_date", LocalDate.class)).thenReturn(LocalDate.of(2026, 9, 1));
        lenient().when(resultSet.getString("ref_value")).thenReturn(refValue);
        return resultSet;
    }

    private static ResultSet rowWithSource(long id, String no, long sourceId) throws Exception {
        ResultSet resultSet = row(id, no, null);
        lenient().when(resultSet.getLong("source_id")).thenReturn(sourceId);
        return resultSet;
    }
}
