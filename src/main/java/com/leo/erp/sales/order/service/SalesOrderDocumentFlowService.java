package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.sales.order.web.dto.SalesOrderDocumentFlowLink;
import com.leo.erp.sales.order.web.dto.SalesOrderDocumentFlowNode;
import com.leo.erp.sales.order.web.dto.SalesOrderDocumentFlowResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售订单单据流只读查询：按 {@code source_*_id} 关系推导订单、出库、退货与客户对账单节点。
 * 全部通过 JDBC 批量查询，ID 输出为十进制字符串以保留雪花精度。
 */
@Service
public class SalesOrderDocumentFlowService {

    private static final String TYPE_SALES_ORDER = "sales-order";
    private static final String TYPE_SALES_OUTBOUND = "sales-outbound";
    private static final String TYPE_SALES_RETURN = "sales-return";
    private static final String TYPE_CUSTOMER_STATEMENT = "customer-statement";

    private static final String ORDER_SQL = """
            SELECT id, order_no, status, total_amount, total_weight, delivery_date
              FROM so_sales_order
             WHERE id = :id
               AND deleted_flag = FALSE
            """;
    private static final String OUTBOUND_SQL = """
            SELECT DISTINCT outbound.id, outbound.outbound_no, outbound.status,
                   outbound.total_amount, outbound.total_weight, outbound.outbound_date
              FROM so_sales_outbound outbound
              JOIN so_sales_outbound_item outbound_item ON outbound_item.outbound_id = outbound.id
              JOIN so_sales_order_item order_item ON order_item.id = outbound_item.source_sales_order_item_id
             WHERE order_item.order_id = :id
               AND outbound.deleted_flag = FALSE
             ORDER BY outbound.id
            """;
    private static final String RETURN_SQL = """
            SELECT DISTINCT ret.id, ret.return_no, ret.status,
                   ret.total_amount, ret.total_weight, ret.return_date
              FROM so_sales_return ret
              JOIN so_sales_return_item return_item ON return_item.return_id = ret.id
              JOIN so_sales_order_item order_item ON order_item.id = return_item.source_sales_order_item_id
             WHERE order_item.order_id = :id
               AND ret.deleted_flag = FALSE
             ORDER BY ret.id
            """;
    private static final String STATEMENT_SQL = """
            SELECT DISTINCT statement.id, statement.statement_no, statement.status,
                   statement.sales_amount, statement.start_date
              FROM st_customer_statement statement
              JOIN st_customer_statement_item statement_item ON statement_item.statement_id = statement.id
              JOIN so_sales_order_item order_item ON order_item.id = statement_item.source_sales_order_item_id
             WHERE order_item.order_id = :id
               AND statement.deleted_flag = FALSE
             ORDER BY statement.id
            """;
    private static final String RETURN_OUTBOUND_LINK_SQL = """
            SELECT DISTINCT ret.id, outbound.id AS outbound_id
              FROM so_sales_return ret
              JOIN so_sales_return_item return_item ON return_item.return_id = ret.id
              JOIN so_sales_outbound_item outbound_item ON outbound_item.id = return_item.source_sales_outbound_item_id
              JOIN so_sales_outbound outbound ON outbound.id = outbound_item.outbound_id
              JOIN so_sales_order_item order_item ON order_item.id = return_item.source_sales_order_item_id
             WHERE order_item.order_id = :id
               AND ret.deleted_flag = FALSE
               AND outbound.deleted_flag = FALSE
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SalesOrderDocumentFlowService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public SalesOrderDocumentFlowResponse documentFlow(Long salesOrderId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("id", salesOrderId);
        OrderNode order = jdbcTemplate.query(ORDER_SQL, parameters, resultSet -> {
            if (!resultSet.next()) {
                return null;
            }
            return new OrderNode(
                    Long.toString(resultSet.getLong("id")),
                    resultSet.getString("order_no"),
                    resultSet.getString("status"),
                    resultSet.getBigDecimal("total_amount"),
                    resultSet.getBigDecimal("total_weight"),
                    resultSet.getObject("delivery_date", LocalDate.class)
            );
        });
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "销售订单不存在");
        }

        List<SalesOrderDocumentFlowNode> nodes = new ArrayList<>();
        nodes.add(new SalesOrderDocumentFlowNode(
                TYPE_SALES_ORDER, order.id(), order.no(), order.status(),
                order.amount(), order.weight(), order.date()));
        List<SalesOrderDocumentFlowLink> links = new ArrayList<>();

        jdbcTemplate.query(OUTBOUND_SQL, parameters, (org.springframework.jdbc.core.RowCallbackHandler) resultSet ->
                nodes.add(new SalesOrderDocumentFlowNode(
                        TYPE_SALES_OUTBOUND,
                        Long.toString(resultSet.getLong("id")),
                        resultSet.getString("outbound_no"),
                        resultSet.getString("status"),
                        resultSet.getBigDecimal("total_amount"),
                        resultSet.getBigDecimal("total_weight"),
                        resultSet.getObject("outbound_date", LocalDate.class))));

        jdbcTemplate.query(RETURN_SQL, parameters, (org.springframework.jdbc.core.RowCallbackHandler) resultSet ->
                nodes.add(new SalesOrderDocumentFlowNode(
                        TYPE_SALES_RETURN,
                        Long.toString(resultSet.getLong("id")),
                        resultSet.getString("return_no"),
                        resultSet.getString("status"),
                        resultSet.getBigDecimal("total_amount"),
                        resultSet.getBigDecimal("total_weight"),
                        resultSet.getObject("return_date", LocalDate.class))));

        jdbcTemplate.query(STATEMENT_SQL, parameters, (org.springframework.jdbc.core.RowCallbackHandler) resultSet ->
                nodes.add(new SalesOrderDocumentFlowNode(
                        TYPE_CUSTOMER_STATEMENT,
                        Long.toString(resultSet.getLong("id")),
                        resultSet.getString("statement_no"),
                        resultSet.getString("status"),
                        resultSet.getBigDecimal("sales_amount"),
                        null,
                        resultSet.getObject("start_date", LocalDate.class))));

        for (SalesOrderDocumentFlowNode node : nodes) {
            if (TYPE_SALES_OUTBOUND.equals(node.type())) {
                links.add(new SalesOrderDocumentFlowLink(
                        TYPE_SALES_ORDER, order.id(), TYPE_SALES_OUTBOUND, node.id(), "出库"));
            } else if (TYPE_SALES_RETURN.equals(node.type())) {
                links.add(new SalesOrderDocumentFlowLink(
                        TYPE_SALES_ORDER, order.id(), TYPE_SALES_RETURN, node.id(), "退货"));
            } else if (TYPE_CUSTOMER_STATEMENT.equals(node.type())) {
                links.add(new SalesOrderDocumentFlowLink(
                        TYPE_SALES_ORDER, order.id(), TYPE_CUSTOMER_STATEMENT, node.id(), "对账"));
            }
        }

        Map<Long, String> returnOutboundLinks = new LinkedHashMap<>();
        jdbcTemplate.query(RETURN_OUTBOUND_LINK_SQL, parameters,
                (org.springframework.jdbc.core.RowCallbackHandler) resultSet -> returnOutboundLinks.put(
                        resultSet.getLong("id"), Long.toString(resultSet.getLong("outbound_id"))));
        returnOutboundLinks.forEach((returnId, outboundId) -> links.add(new SalesOrderDocumentFlowLink(
                TYPE_SALES_OUTBOUND, outboundId, TYPE_SALES_RETURN, Long.toString(returnId), "退货")));

        return new SalesOrderDocumentFlowResponse(order.id(), nodes, links);
    }

    private record OrderNode(String id,
                             String no,
                             String status,
                             java.math.BigDecimal amount,
                             java.math.BigDecimal weight,
                             LocalDate date) {
    }
}
