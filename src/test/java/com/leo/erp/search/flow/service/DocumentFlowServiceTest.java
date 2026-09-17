package com.leo.erp.search.flow.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.search.flow.web.dto.DocumentFlowLink;
import com.leo.erp.search.flow.web.dto.DocumentFlowNode;
import com.leo.erp.search.flow.web.dto.DocumentFlowResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DocumentFlowService 单据流装配测试。
 * <p>
 * 覆盖：空单号与未命中单号的错误语义、采购订单 → 采购入库 → 销售订单 → 销售出库 → 物流单
 * 的节点/关系边装配、以及雪花 ID 十进制字符串化与多值引用拆分。
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
    void documentFlow_shouldThrowNotFoundWhenNoDocumentMatches() {
        stubRows(rowMapper -> List.of());
        DocumentFlowService service = new DocumentFlowService(jdbcTemplate);

        assertThatThrownBy(() -> service.documentFlow("UNKNOWN-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到单号对应的单据");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void documentFlow_shouldBuildCoreChainWithStringifiedSnowflakeIds() throws Exception {
        ResultSet order = nodeRow(ORDER_ID, "PO-1", "已审核", "1000.00", "10.00000000", LocalDate.of(2026, 9, 1));
        ResultSet inbound = nodeRow(INBOUND_ID, "IN-1", "已审核", "1000.00", "10.00000000", LocalDate.of(2026, 9, 2));
        ResultSet salesOrder = nodeRow(SALES_ORDER_ID, "SO-1", "已审核", "1200.00", "10.00000000", LocalDate.of(2026, 9, 3));
        ResultSet outbound = nodeRow(OUTBOUND_ID, "OUT-1", "已审核", "1200.00", "10.00000000", LocalDate.of(2026, 9, 4));
        ResultSet freightBill = nodeRow(FREIGHT_BILL_ID, "BILL-1", "已审核", "500.00", "10.00000000", LocalDate.of(2026, 9, 5));

        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    RowMapper<Object> mapper = invocation.getArgument(2);
                    ResultSet row = null;
                    if (sql.contains("FROM po_purchase_order")) {
                        row = order;
                    } else if (sql.contains("FROM po_purchase_inbound")) {
                        row = inbound;
                    } else if (sql.contains("FROM so_sales_order")) {
                        row = salesOrder;
                    } else if (sql.contains("FROM so_sales_outbound")) {
                        row = outbound;
                    } else if (sql.contains("FROM lg_freight_bill bill") && !sql.contains("source_no = :no")) {
                        row = freightBill;
                    }
                    return row == null ? List.of() : List.of(mapper.mapRow(row, 0));
                });

        lenient().doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            RowCallbackHandler handler = invocation.getArgument(2);
            if (sql.contains("FROM po_purchase_inbound") && sql.contains("purchase_order_no LIKE")) {
                handler.processRow(refRow(INBOUND_ID, "PO-1"));
            } else if (sql.contains("FROM so_sales_order") && sql.contains("purchase_inbound_no LIKE")) {
                handler.processRow(refRow(SALES_ORDER_ID, "IN-1"));
            } else if (sql.contains("FROM so_sales_outbound") && sql.contains("sales_order_no LIKE")) {
                handler.processRow(refRow(OUTBOUND_ID, "SO-1"));
            }
            return null;
        }).when(jdbcTemplate).query(anyString(), any(SqlParameterSource.class), any(RowCallbackHandler.class));

        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(String.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("FROM so_sales_order") && sql.contains("purchase_order_no")) {
                        return null;
                    }
                    if (sql.contains("SELECT purchase_inbound_no")) {
                        return "IN-1";
                    }
                    if (sql.contains("SELECT purchase_order_no")) {
                        return "PO-1";
                    }
                    return "SO-1";
                });

        lenient().when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class), eq(String.class)))
                .thenReturn(List.of("SO-1"));

        DocumentFlowResponse response = new DocumentFlowService(jdbcTemplate).documentFlow("PO-1");

        assertThat(response.documentNo()).isEqualTo("PO-1");
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
        assertThat(response.links().get(3).fromType()).isEqualTo("sales-outbound");
        assertThat(response.links().get(3).toType()).isEqualTo("freight-bill");
    }

    /** 种子查询：仅采购订单按单号命中。 */
    private void stubRows(java.util.function.Function<RowMapper<Object>, List<Object>> answer) {
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> answer.apply(invocation.getArgument(2)));
    }

    private static ResultSet nodeRow(long id, String no, String status, String amount, String weight, LocalDate date)
            throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        lenient().when(resultSet.getLong("id")).thenReturn(id);
        lenient().when(resultSet.getString("no")).thenReturn(no);
        lenient().when(resultSet.getString("status")).thenReturn(status);
        lenient().when(resultSet.getBigDecimal("amount")).thenReturn(new BigDecimal(amount));
        lenient().when(resultSet.getBigDecimal("weight")).thenReturn(new BigDecimal(weight));
        lenient().when(resultSet.getObject("business_date", LocalDate.class)).thenReturn(date);
        return resultSet;
    }

    /** 处理引用列的 LIKE 预筛回调：只填充引用列，其它列与 id 用于映射节点。 */
    private static ResultSet refRow(long id, String reference) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        lenient().when(resultSet.getLong("id")).thenReturn(id);
        lenient().when(resultSet.getString("purchase_order_no")).thenReturn(reference);
        lenient().when(resultSet.getString("purchase_inbound_no")).thenReturn(reference);
        lenient().when(resultSet.getString("sales_order_no")).thenReturn(reference);
        lenient().when(resultSet.getString("no")).thenReturn(reference);
        lenient().when(resultSet.getString("status")).thenReturn("已审核");
        lenient().when(resultSet.getBigDecimal("amount")).thenReturn(BigDecimal.ZERO);
        lenient().when(resultSet.getBigDecimal("weight")).thenReturn(BigDecimal.ZERO);
        lenient().when(resultSet.getObject("business_date", LocalDate.class)).thenReturn(Date.valueOf("2026-09-01").toLocalDate());
        return resultSet;
    }
}
