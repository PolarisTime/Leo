package com.leo.erp.search.flow.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.search.flow.web.dto.DocumentFlowLink;
import com.leo.erp.search.flow.web.dto.DocumentFlowNode;
import com.leo.erp.search.flow.web.dto.DocumentFlowResponse;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 通用单据流只读查询：输入采购、销售、物流任一单号，按引用关系推导整条核心业务链。
 * <p>
 * 链路方向统一为上游 → 下游：采购单 → 采购入库 → 销售单 → 销售出库 → 销售退货 / 物流单。
 * 单号引用字段（如 {@code purchase_order_no}）允许逗号分隔多值，先按 {@code LIKE} 缩小范围，
 * 再在应用侧按分隔符精确匹配，避免子串误命中。ID 输出为十进制字符串以保留雪花精度。
 */
@Service
public class DocumentFlowService {

    public static final String TYPE_PURCHASE_ORDER = "purchase-order";
    public static final String TYPE_PURCHASE_INBOUND = "purchase-inbound";
    public static final String TYPE_SALES_ORDER = "sales-order";
    public static final String TYPE_SALES_OUTBOUND = "sales-outbound";
    public static final String TYPE_SALES_RETURN = "sales-return";
    public static final String TYPE_FREIGHT_BILL = "freight-bill";

    private static final Pattern REFERENCE_SPLITTER = Pattern.compile("[,，、;；\\s]+");

    /** 单号长度上限, 防止超长输入放大 LIKE 扫描开销。 */
    private static final int MAX_DOCUMENT_NO_LENGTH = 64;
    /** 链路节点/关系边上限, 避免热门单据级联出超大规模图与响应。 */
    private static final int MAX_NODES = 500;
    private static final int MAX_LINKS = 1000;

    private static final String PURCHASE_ORDER_COLUMNS =
            "id, order_no AS no, status, total_amount AS amount, total_weight AS weight, "
                    + "order_date::date AS business_date";
    private static final String PURCHASE_INBOUND_COLUMNS =
            "id, inbound_no AS no, status, total_amount AS amount, total_weight AS weight, "
                    + "inbound_date::date AS business_date";
    private static final String SALES_ORDER_COLUMNS =
            "id, order_no AS no, status, total_amount AS amount, total_weight AS weight, "
                    + "delivery_date::date AS business_date";
    private static final String SALES_OUTBOUND_COLUMNS =
            "id, outbound_no AS no, status, total_amount AS amount, total_weight AS weight, "
                    + "outbound_date::date AS business_date";
    private static final String SALES_RETURN_COLUMNS =
            "id, return_no AS no, status, total_amount AS amount, total_weight AS weight, "
                    + "return_date AS business_date";
    private static final String FREIGHT_BILL_COLUMNS =
            "id, bill_no AS no, status, total_freight AS amount, total_weight AS weight, "
                    + "bill_time::date AS business_date";

    private static final String LINK_INBOUND = "入库";
    private static final String LINK_SALES = "销售";
    private static final String LINK_OUTBOUND = "出库";
    private static final String LINK_RETURN = "退货";
    private static final String LINK_FREIGHT = "物流";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DocumentFlowService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 输入单号所在单据为起点，广度扩展出整个连通链路的节点与关系边。 */
    @Transactional(readOnly = true)
    public DocumentFlowResponse documentFlow(String documentNo) {
        String target = documentNo == null ? "" : documentNo.trim();
        if (target.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "单号不能为空");
        }
        if (target.length() > MAX_DOCUMENT_NO_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "单号长度不能超过 " + MAX_DOCUMENT_NO_LENGTH + " 个字符");
        }
        List<FlowNode> seeds = findSeeds(target);
        if (seeds.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到单号对应的单据: " + target);
        }

        Map<String, FlowNode> nodes = new LinkedHashMap<>();
        Map<String, DocumentFlowLink> links = new LinkedHashMap<>();
        Deque<FlowNode> queue = new ArrayDeque<>();
        for (FlowNode seed : seeds) {
            if (nodes.putIfAbsent(seed.key(), seed) == null) {
                queue.add(seed);
            }
        }
        boolean truncated = false;
        while (!queue.isEmpty()) {
            if (nodes.size() >= MAX_NODES) {
                truncated = true;
                break;
            }
            FlowNode current = queue.poll();
            for (FlowEdge edge : relatedEdges(current)) {
                if (!nodes.containsKey(edge.from().key())) {
                    if (nodes.size() >= MAX_NODES) {
                        truncated = true;
                        continue;
                    }
                    nodes.put(edge.from().key(), edge.from());
                    queue.add(edge.from());
                }
                if (!nodes.containsKey(edge.to().key())) {
                    if (nodes.size() >= MAX_NODES) {
                        truncated = true;
                        continue;
                    }
                    nodes.put(edge.to().key(), edge.to());
                    queue.add(edge.to());
                }
                if (links.size() >= MAX_LINKS) {
                    truncated = true;
                    continue;
                }
                DocumentFlowLink link = new DocumentFlowLink(
                        edge.from().type(), edge.from().id(),
                        edge.to().type(), edge.to().id(),
                        edge.linkType());
                links.putIfAbsent(edge.from().key() + "->" + edge.to().key(), link);
            }
        }

        List<DocumentFlowNode> nodeList = nodes.values().stream()
                .map(node -> new DocumentFlowNode(
                        node.type(), node.id(), node.no(), node.status(),
                        node.amount(), node.weight(), node.date()))
                .toList();
        return new DocumentFlowResponse(
                target, nodeList, List.copyOf(links.values()), truncated);
    }

    private List<FlowNode> findSeeds(String no) {
        List<FlowNode> seeds = new ArrayList<>();
        for (String type : List.of(
                TYPE_PURCHASE_ORDER, TYPE_PURCHASE_INBOUND, TYPE_SALES_ORDER,
                TYPE_SALES_OUTBOUND, TYPE_SALES_RETURN, TYPE_FREIGHT_BILL)) {
            findByNo(type, no).ifPresent(seeds::add);
        }
        return seeds;
    }

    private Optional<FlowNode> findByNo(String type, String no) {
        String sql = "SELECT " + columnsOf(type) + " FROM " + tableOf(type)
                + " WHERE deleted_flag = FALSE AND " + noColumnOf(type) + " = :no";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("no", no), nodeMapper(type))
                .stream().findFirst();
    }

    private List<FlowEdge> relatedEdges(FlowNode node) {
        return switch (node.type()) {
            case TYPE_PURCHASE_ORDER -> purchaseOrderEdges(node);
            case TYPE_PURCHASE_INBOUND -> purchaseInboundEdges(node);
            case TYPE_SALES_ORDER -> salesOrderEdges(node);
            case TYPE_SALES_OUTBOUND -> salesOutboundEdges(node);
            case TYPE_SALES_RETURN -> salesReturnEdges(node);
            case TYPE_FREIGHT_BILL -> freightBillEdges(node);
            default -> List.of();
        };
    }

    private List<FlowEdge> purchaseOrderEdges(FlowNode order) {
        List<FlowEdge> edges = new ArrayList<>();
        for (FlowNode inbound : referencingNodes(TYPE_PURCHASE_INBOUND, "purchase_order_no", order.no())) {
            edges.add(new FlowEdge(order, inbound, LINK_INBOUND));
        }
        for (FlowNode salesOrder : referencingNodes(TYPE_SALES_ORDER, "purchase_order_no", order.no())) {
            edges.add(new FlowEdge(order, salesOrder, LINK_SALES));
        }
        return edges;
    }

    private List<FlowEdge> purchaseInboundEdges(FlowNode inbound) {
        List<FlowEdge> edges = new ArrayList<>();
        for (String orderNo : splitReferences(referenceValue(TYPE_PURCHASE_INBOUND, inbound.id(), "purchase_order_no"))) {
            findByNo(TYPE_PURCHASE_ORDER, orderNo)
                    .ifPresent(order -> edges.add(new FlowEdge(order, inbound, LINK_INBOUND)));
        }
        for (FlowNode salesOrder : referencingNodes(TYPE_SALES_ORDER, "purchase_inbound_no", inbound.no())) {
            edges.add(new FlowEdge(inbound, salesOrder, LINK_SALES));
        }
        return edges;
    }

    private List<FlowEdge> salesOrderEdges(FlowNode salesOrder) {
        List<FlowEdge> edges = new ArrayList<>();
        for (String inboundNo : splitReferences(referenceValue(TYPE_SALES_ORDER, salesOrder.id(), "purchase_inbound_no"))) {
            findByNo(TYPE_PURCHASE_INBOUND, inboundNo)
                    .ifPresent(inbound -> edges.add(new FlowEdge(inbound, salesOrder, LINK_SALES)));
        }
        for (String orderNo : splitReferences(referenceValue(TYPE_SALES_ORDER, salesOrder.id(), "purchase_order_no"))) {
            findByNo(TYPE_PURCHASE_ORDER, orderNo)
                    .ifPresent(order -> edges.add(new FlowEdge(order, salesOrder, LINK_SALES)));
        }
        for (FlowNode outbound : referencingNodes(TYPE_SALES_OUTBOUND, "sales_order_no", salesOrder.no())) {
            edges.add(new FlowEdge(salesOrder, outbound, LINK_OUTBOUND));
        }
        for (FlowNode salesReturn : referencingNodes(TYPE_SALES_RETURN, "sales_order_no", salesOrder.no())) {
            edges.add(new FlowEdge(salesOrder, salesReturn, LINK_RETURN));
        }
        for (FlowNode bill : freightBillsBySalesOrderNo(salesOrder.no())) {
            edges.add(new FlowEdge(salesOrder, bill, LINK_FREIGHT));
        }
        return edges;
    }

    private List<FlowEdge> salesOutboundEdges(FlowNode outbound) {
        List<FlowEdge> edges = new ArrayList<>();
        for (String orderNo : splitReferences(referenceValue(TYPE_SALES_OUTBOUND, outbound.id(), "sales_order_no"))) {
            findByNo(TYPE_SALES_ORDER, orderNo)
                    .ifPresent(salesOrder -> edges.add(new FlowEdge(salesOrder, outbound, LINK_OUTBOUND)));
        }
        for (FlowNode salesReturn : returnsByOutboundId(outbound.id())) {
            edges.add(new FlowEdge(outbound, salesReturn, LINK_RETURN));
        }
        for (FlowNode bill : freightBillsByOutboundId(outbound.id())) {
            edges.add(new FlowEdge(outbound, bill, LINK_FREIGHT));
        }
        return edges;
    }

    private List<FlowEdge> salesReturnEdges(FlowNode salesReturn) {
        List<FlowEdge> edges = new ArrayList<>();
        for (String orderNo : splitReferences(referenceValue(TYPE_SALES_RETURN, salesReturn.id(), "sales_order_no"))) {
            findByNo(TYPE_SALES_ORDER, orderNo)
                    .ifPresent(salesOrder -> edges.add(new FlowEdge(salesOrder, salesReturn, LINK_RETURN)));
        }
        for (FlowNode outbound : outboundsByReturnId(salesReturn.id())) {
            edges.add(new FlowEdge(outbound, salesReturn, LINK_RETURN));
        }
        for (FlowNode bill : freightBillsByReturnId(salesReturn.id())) {
            edges.add(new FlowEdge(bill, salesReturn, LINK_FREIGHT));
        }
        return edges;
    }

    private List<FlowEdge> freightBillEdges(FlowNode bill) {
        List<FlowEdge> edges = new ArrayList<>();
        for (String salesOrderNo : sourceNosOfFreightBill(bill.id())) {
            findByNo(TYPE_SALES_ORDER, salesOrderNo)
                    .ifPresent(salesOrder -> edges.add(new FlowEdge(salesOrder, bill, LINK_FREIGHT)));
        }
        for (FlowNode outbound : outboundsByFreightBillId(bill.id())) {
            edges.add(new FlowEdge(outbound, bill, LINK_FREIGHT));
        }
        return edges;
    }

    /** 引用列（逗号分隔多值）包含指定单号的单据；LIKE 预筛后在应用侧精确拆分校验。 */
    private List<FlowNode> referencingNodes(String type, String column, String no) {
        String sql = "SELECT " + columnsOf(type) + ", " + column + " FROM " + tableOf(type)
                + " WHERE deleted_flag = FALSE AND " + column + " LIKE :pattern";
        List<FlowNode> nodes = new ArrayList<>();
        jdbcTemplate.query(sql, new MapSqlParameterSource("pattern", likePattern(no)), resultSet -> {
            if (splitReferences(resultSet.getString(column)).contains(no)) {
                nodes.add(mapNode(type, resultSet));
            }
        });
        return nodes;
    }

    private String referenceValue(String type, String id, String column) {
        // 与 findByNo/referencingNodes 保持软删过滤口径一致; 记录不存在或已软删时返回 null。
        String sql = "SELECT " + column + " FROM " + tableOf(type)
                + " WHERE id = :id AND deleted_flag = FALSE";
        List<String> values = jdbcTemplate.queryForList(
                sql, new MapSqlParameterSource("id", Long.parseLong(id)), String.class);
        return values.isEmpty() ? null : values.get(0);
    }

    private List<FlowNode> returnsByOutboundId(String outboundId) {
        String sql = "SELECT DISTINCT " + aliasColumns(SALES_RETURN_COLUMNS, "ret") + " FROM so_sales_return ret"
                + " JOIN so_sales_return_item return_item ON return_item.return_id = ret.id"
                + " JOIN so_sales_outbound_item outbound_item ON outbound_item.id = return_item.source_sales_outbound_item_id"
                + " WHERE ret.deleted_flag = FALSE AND outbound_item.outbound_id = :id";
        return queryNodes(TYPE_SALES_RETURN, sql, outboundId);
    }

    private List<FlowNode> outboundsByReturnId(String returnId) {
        String sql = "SELECT DISTINCT " + aliasColumns(SALES_OUTBOUND_COLUMNS, "outbound") + " FROM so_sales_outbound outbound"
                + " JOIN so_sales_outbound_item outbound_item ON outbound_item.outbound_id = outbound.id"
                + " JOIN so_sales_return_item return_item ON return_item.source_sales_outbound_item_id = outbound_item.id"
                + " WHERE outbound.deleted_flag = FALSE AND return_item.return_id = :id";
        return queryNodes(TYPE_SALES_OUTBOUND, sql, returnId);
    }

    /**
     * source_no 与其它引用字段口径一致: 允许逗号分隔多值, 先 LIKE 预筛再在应用侧精确拆分校验,
     * 避免单号含多值时精确等值漏匹配。
     */
    private List<FlowNode> freightBillsBySalesOrderNo(String salesOrderNo) {
        String sql = "SELECT DISTINCT " + aliasColumns(FREIGHT_BILL_COLUMNS, "bill")
                + ", bill_item.source_no AS source_no FROM lg_freight_bill bill"
                + " JOIN lg_freight_bill_item bill_item ON bill_item.bill_id = bill.id"
                + " WHERE bill.deleted_flag = FALSE AND bill_item.source_no LIKE :pattern";
        Map<String, FlowNode> matched = new LinkedHashMap<>();
        jdbcTemplate.query(sql, new MapSqlParameterSource("pattern", likePattern(salesOrderNo)), resultSet -> {
            if (splitReferences(resultSet.getString("source_no")).contains(salesOrderNo)) {
                FlowNode node = mapNode(TYPE_FREIGHT_BILL, resultSet);
                matched.putIfAbsent(node.key(), node);
            }
        });
        return List.copyOf(matched.values());
    }

    private List<FlowNode> freightBillsByOutboundId(String outboundId) {
        String sql = "SELECT DISTINCT " + aliasColumns(FREIGHT_BILL_COLUMNS, "bill") + " FROM lg_freight_bill bill"
                + " JOIN lg_freight_bill_item bill_item ON bill_item.bill_id = bill.id"
                + " JOIN so_sales_outbound_item outbound_item ON outbound_item.id = bill_item.source_sales_outbound_item_id"
                + " WHERE bill.deleted_flag = FALSE AND outbound_item.outbound_id = :id";
        return queryNodes(TYPE_FREIGHT_BILL, sql, outboundId);
    }

    private List<FlowNode> freightBillsByReturnId(String returnId) {
        String sql = "SELECT DISTINCT " + aliasColumns(FREIGHT_BILL_COLUMNS, "bill") + " FROM lg_freight_bill bill"
                + " JOIN so_sales_return_item return_item ON return_item.source_freight_bill_id = bill.id"
                + " WHERE bill.deleted_flag = FALSE AND return_item.return_id = :id";
        return queryNodes(TYPE_FREIGHT_BILL, sql, returnId);
    }

    private List<FlowNode> outboundsByFreightBillId(String billId) {
        String sql = "SELECT DISTINCT " + aliasColumns(SALES_OUTBOUND_COLUMNS, "outbound") + " FROM so_sales_outbound outbound"
                + " JOIN so_sales_outbound_item outbound_item ON outbound_item.outbound_id = outbound.id"
                + " JOIN lg_freight_bill_item bill_item ON bill_item.source_sales_outbound_item_id = outbound_item.id"
                + " WHERE outbound.deleted_flag = FALSE AND bill_item.bill_id = :id";
        return queryNodes(TYPE_SALES_OUTBOUND, sql, billId);
    }

    private List<String> sourceNosOfFreightBill(String billId) {
        String sql = "SELECT DISTINCT bill_item.source_no FROM lg_freight_bill_item bill_item"
                + " WHERE bill_item.bill_id = :id AND COALESCE(BTRIM(bill_item.source_no), '') <> ''";
        List<String> result = new ArrayList<>();
        for (String raw : jdbcTemplate.queryForList(sql, new MapSqlParameterSource("id", Long.parseLong(billId)), String.class)) {
            for (String no : splitReferences(raw)) {
                if (!result.contains(no)) {
                    result.add(no);
                }
            }
        }
        return result;
    }

    private List<FlowNode> queryNodes(String type, String sql, String id) {
        return jdbcTemplate.query(sql, new MapSqlParameterSource("id", Long.parseLong(id)), nodeMapper(type));
    }

    /** 为列清单逐项加上表别名与 AS 别名，用于 JOIN 查询。 */
    private static String aliasColumns(String columns, String alias) {
        List<String> aliased = new ArrayList<>();
        for (String item : columns.split(",")) {
            String trimmed = item.trim();
            int asIndex = trimmed.toUpperCase().indexOf(" AS ");
            if (asIndex < 0) {
                aliased.add(alias + "." + trimmed);
                continue;
            }
            String column = trimmed.substring(0, asIndex).trim();
            String aliasName = trimmed.substring(asIndex + 4).trim();
            aliased.add(alias + "." + column + " AS " + aliasName);
        }
        return String.join(", ", aliased);
    }

    private static RowMapper<FlowNode> nodeMapper(String type) {
        return (resultSet, rowNum) -> mapNode(type, resultSet);
    }

    private static FlowNode mapNode(String type, ResultSet resultSet) throws SQLException {
        return new FlowNode(
                type,
                Long.toString(resultSet.getLong("id")),
                resultSet.getString("no"),
                resultSet.getString("status"),
                resultSet.getBigDecimal("amount"),
                resultSet.getBigDecimal("weight"),
                resultSet.getObject("business_date", LocalDate.class));
    }

    private static String tableOf(String type) {
        return switch (type) {
            case TYPE_PURCHASE_ORDER -> "po_purchase_order";
            case TYPE_PURCHASE_INBOUND -> "po_purchase_inbound";
            case TYPE_SALES_ORDER -> "so_sales_order";
            case TYPE_SALES_OUTBOUND -> "so_sales_outbound";
            case TYPE_SALES_RETURN -> "so_sales_return";
            case TYPE_FREIGHT_BILL -> "lg_freight_bill";
            default -> throw new IllegalArgumentException("未知单据类型: " + type);
        };
    }

    private static String columnsOf(String type) {
        return switch (type) {
            case TYPE_PURCHASE_ORDER -> PURCHASE_ORDER_COLUMNS;
            case TYPE_PURCHASE_INBOUND -> PURCHASE_INBOUND_COLUMNS;
            case TYPE_SALES_ORDER -> SALES_ORDER_COLUMNS;
            case TYPE_SALES_OUTBOUND -> SALES_OUTBOUND_COLUMNS;
            case TYPE_SALES_RETURN -> SALES_RETURN_COLUMNS;
            case TYPE_FREIGHT_BILL -> FREIGHT_BILL_COLUMNS;
            default -> throw new IllegalArgumentException("未知单据类型: " + type);
        };
    }

    private static String noColumnOf(String type) {
        return switch (type) {
            case TYPE_PURCHASE_ORDER -> "order_no";
            case TYPE_PURCHASE_INBOUND -> "inbound_no";
            case TYPE_SALES_ORDER -> "order_no";
            case TYPE_SALES_OUTBOUND -> "outbound_no";
            case TYPE_SALES_RETURN -> "return_no";
            case TYPE_FREIGHT_BILL -> "bill_no";
            default -> throw new IllegalArgumentException("未知单据类型: " + type);
        };
    }

    /** 按逗号/顿号/分号/空白拆分引用字段中的多单号。 */
    static List<String> splitReferences(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return REFERENCE_SPLITTER.splitAsStream(raw)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private static String likePattern(String value) {
        return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private record FlowNode(String type,
                            String id,
                            String no,
                            String status,
                            BigDecimal amount,
                            BigDecimal weight,
                            LocalDate date) {

        String key() {
            return type + ":" + id;
        }
    }

    private record FlowEdge(FlowNode from, FlowNode to, String linkType) {
    }
}
