package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.sales.order.web.dto.SalesOrderDocumentFlowLink;
import com.leo.erp.sales.order.web.dto.SalesOrderDocumentFlowNode;
import com.leo.erp.sales.order.web.dto.SalesOrderDocumentFlowResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SalesOrderDocumentFlowService 单据流装配测试。
 * <p>
 * 覆盖：订单缺失 NOT_FOUND、订单+出库+退货+对账单节点与关系边、
 * 无关联单据时仅返回订单节点、以及雪花 ID 十进制字符串化。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderDocumentFlowServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private SalesOrderDocumentFlowService service;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void documentFlow_shouldThrowNotFoundWhenOrderAbsent() throws Exception {
        ResultSet absent = mock(ResultSet.class);
        lenient().when(absent.next()).thenReturn(false);
        stubQueries(absent, null, null, null, null);

        assertThatThrownBy(() -> service.documentFlow(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售订单不存在");
    }

    @Test
    void documentFlow_shouldReturnOnlyOrderNodeWhenNoRelatedDocuments() throws Exception {
        long orderId = 5L;
        stubQueries(orderRow(orderId, "SO-5", "AUDITED", "0.00", "0.00", null), null, null, null, null);

        SalesOrderDocumentFlowResponse response = service.documentFlow(orderId);

        assertThat(response.salesOrderId()).isEqualTo("5");
        assertThat(response.nodes()).hasSize(1);
        assertThat(response.nodes().get(0).type()).isEqualTo("sales-order");
        assertThat(response.links()).isEmpty();
    }

    @Test
    void documentFlow_shouldBuildNodesAndLinksWithStringifiedSnowflakeIds() throws Exception {
        long orderId = 9007199254740993L;
        long outboundId = 9007199254740995L;
        long returnId = 9007199254740997L;
        long statementId = 9007199254740999L;
        stubQueries(
                orderRow(orderId, "SO-1", "AUDITED", "1000.00", "12.5", LocalDate.of(2026, 8, 1)),
                outboundRow(outboundId, "OUT-1", "AUDITED", "1000.00", "12.5", LocalDate.of(2026, 8, 2)),
                returnRow(returnId, "RET-1", "AUDITED", "100.00", "1.5", LocalDate.of(2026, 8, 3)),
                statementRow(statementId, "ST-1", "CONFIRMED", "900.00", LocalDate.of(2026, 8, 4)),
                returnOutboundRow(returnId, outboundId));

        SalesOrderDocumentFlowResponse response = service.documentFlow(1L);

        assertThat(response.salesOrderId()).isEqualTo(Long.toString(orderId));
        assertThat(response.nodes()).extracting(SalesOrderDocumentFlowNode::type)
                .containsExactly("sales-order", "sales-outbound", "sales-return", "customer-statement");
        assertThat(response.nodes()).extracting(SalesOrderDocumentFlowNode::id)
                .containsExactly(
                        Long.toString(orderId),
                        Long.toString(outboundId),
                        Long.toString(returnId),
                        Long.toString(statementId));
        assertThat(response.links()).extracting(SalesOrderDocumentFlowLink::linkType)
                .containsExactly("出库", "退货", "对账", "退货");
        assertThat(response.links().get(3).fromType()).isEqualTo("sales-outbound");
        assertThat(response.links().get(3).toType()).isEqualTo("sales-return");

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, atLeastOnce()).query(anyString(), paramsCaptor.capture(), any(RowCallbackHandler.class));
        assertThat(paramsCaptor.getValue().getValue("id")).isEqualTo(1L);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubQueries(ResultSet orderRow,
                             ResultSet outboundRow,
                             ResultSet returnRow,
                             ResultSet statementRow,
                             ResultSet returnOutboundRow) {
        doAnswer(invocation -> ((ResultSetExtractor<?>) invocation.getArgument(2)).extractData(orderRow))
                .when(jdbcTemplate).query(anyString(), any(SqlParameterSource.class), any(ResultSetExtractor.class));

        lenient().doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            RowCallbackHandler handler = invocation.getArgument(2);
            ResultSet row = null;
            if (sql.contains("outbound.outbound_no")) {
                row = outboundRow;
            } else if (sql.contains("ret.return_no")) {
                row = returnRow;
            } else if (sql.contains("statement.statement_no")) {
                row = statementRow;
            } else if (sql.contains("AS outbound_id")) {
                row = returnOutboundRow;
            }
            if (row != null) {
                handler.processRow(row);
            }
            return null;
        }).when(jdbcTemplate).query(anyString(), any(SqlParameterSource.class), any(RowCallbackHandler.class));
    }

    private static ResultSet orderRow(long id, String no, String status, String amount, String weight, LocalDate date)
            throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getLong("id")).thenReturn(id);
        when(rs.getString("order_no")).thenReturn(no);
        when(rs.getString("status")).thenReturn(status);
        when(rs.getBigDecimal("total_amount")).thenReturn(new BigDecimal(amount));
        when(rs.getBigDecimal("total_weight")).thenReturn(new BigDecimal(weight));
        when(rs.getObject("delivery_date", LocalDate.class)).thenReturn(date);
        return rs;
    }

    private static ResultSet outboundRow(long id, String no, String status, String amount, String weight, LocalDate date)
            throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(id);
        when(rs.getString("outbound_no")).thenReturn(no);
        when(rs.getString("status")).thenReturn(status);
        when(rs.getBigDecimal("total_amount")).thenReturn(new BigDecimal(amount));
        when(rs.getBigDecimal("total_weight")).thenReturn(new BigDecimal(weight));
        when(rs.getObject("outbound_date", LocalDate.class)).thenReturn(date);
        return rs;
    }

    private static ResultSet returnRow(long id, String no, String status, String amount, String weight, LocalDate date)
            throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(id);
        when(rs.getString("return_no")).thenReturn(no);
        when(rs.getString("status")).thenReturn(status);
        when(rs.getBigDecimal("total_amount")).thenReturn(new BigDecimal(amount));
        when(rs.getBigDecimal("total_weight")).thenReturn(new BigDecimal(weight));
        when(rs.getObject("return_date", LocalDate.class)).thenReturn(date);
        return rs;
    }

    private static ResultSet statementRow(long id, String no, String status, String amount, LocalDate date)
            throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(id);
        when(rs.getString("statement_no")).thenReturn(no);
        when(rs.getString("status")).thenReturn(status);
        when(rs.getBigDecimal("sales_amount")).thenReturn(new BigDecimal(amount));
        when(rs.getObject("start_date", LocalDate.class)).thenReturn(date);
        return rs;
    }

    private static ResultSet returnOutboundRow(long returnId, long outboundId) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(returnId);
        when(rs.getLong("outbound_id")).thenReturn(outboundId);
        return rs;
    }
}
