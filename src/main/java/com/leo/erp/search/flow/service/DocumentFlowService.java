package com.leo.erp.search.flow.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.search.flow.web.dto.DocumentFlowLink;
import com.leo.erp.search.flow.web.dto.DocumentFlowNode;
import com.leo.erp.search.flow.web.dto.DocumentFlowResponse;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 通用单据流只读查询：输入采购、销售、物流任一单号，按引用关系推导整条核心业务链。
 * <p>
 * 链路方向统一为上游 → 下游：采购单 → 采购入库 → 销售单 → 销售出库 → 销售退货 / 物流单。
 * 单号引用字段（如 {@code purchase_order_no}）允许逗号分隔多值，先按 {@code LIKE} 缩小范围，
 * 再在应用侧按分隔符精确匹配，避免子串误命中。ID 输出为十进制字符串以保留雪花精度。
 * <p>
 * 为避免逐节点反查的 N+1，扩展按"层"进行：同层同类型的引用反查与按单号查询均批量执行
 * （引用列已由 V152 建立 pg_trgm GIN 索引，{@code LIKE '%单号%'} 走索引）。
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

    /** 输入单号所在单据为起点，按层广度扩展出整个连通链路的节点与关系边。 */
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
        List<FlowNode> currentLayer = new ArrayList<>();
        for (FlowNode seed : seeds) {
            if (nodes.putIfAbsent(seed.key(), seed) == null) {
                currentLayer.add(seed);
            }
        }
        boolean truncated = false;
        while (!currentLayer.isEmpty() && !truncated) {
            if (nodes.size() >= MAX_NODES) {
                truncated = true;
                break;
            }
            List<FlowNode> nextLayer = new ArrayList<>();
            for (FlowEdge edge : expandLayer(currentLayer)) {
                if (!nodes.containsKey(edge.from().key())) {
                    if (nodes.size() >= MAX_NODES) {
                        truncated = true;
                        continue;
                    }
                    nodes.put(edge.from().key(), edge.from());
                    nextLayer.add(edge.from());
                }
                if (!nodes.containsKey(edge.to().key())) {
                    if (nodes.size() >= MAX_NODES) {
                        truncated = true;
                        continue;
                    }
                    nodes.put(edge.to().key(), edge.to());
                    nextLayer.add(edge.to());
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
            currentLayer = nextLayer;
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

    /** 按层展开：同层同类型的引用反查与按单号查询合并为批量 SQL, 消除逐节点 N+1。 */
    private List<FlowEdge> expandLayer(List<FlowNode> layer) {
        Map<String, List<FlowNode>> byType = new LinkedHashMap<>();
        for (FlowNode node : layer) {
            byType.computeIfAbsent(node.type(), key -> new ArrayList<>()).add(node);
        }
        List<FlowEdge> edges = new ArrayList<>();
        List<FlowNode> purchaseOrders = byType.getOrDefault(TYPE_PURCHASE_ORDER, List.of());
        List<FlowNode> purchaseInbounds = byType.getOrDefault(TYPE_PURCHASE_INBOUND, List.of());
        List<FlowNode> salesOrders = byType.getOrDefault(TYPE_SALES_ORDER, List.of());
        List<FlowNode> salesOutbounds = byType.getOrDefault(TYPE_SALES_OUTBOUND, List.of());
        List<FlowNode> salesReturns = byType.getOrDefault(TYPE_SALES_RETURN, List.of());
        List<FlowNode> freightBills = byType.getOrDefault(TYPE_FREIGHT_BILL, List.of());

        if (!purchaseOrders.isEmpty()) {
            addReferencingEdges(purchaseOrders, TYPE_PURCHASE_INBOUND, "purchase_order_no", LINK_INBOUND, edges);
            addReferencingEdges(purchaseOrders, TYPE_SALES_ORDER, "purchase_order_no", LINK_SALES, edges);
        }
        if (!purchaseInbounds.isEmpty()) {
            addForwardEdges(purchaseInbounds, "purchase_order_no", TYPE_PURCHASE_ORDER, LINK_INBOUND, edges);
            addReferencingEdges(purchaseInbounds, TYPE_SALES_ORDER, "purchase_inbound_no", LINK_SALES, edges);
        }
        if (!salesOrders.isEmpty()) {
            addForwardEdges(salesOrders, "purchase_inbound_no", TYPE_PURCHASE_INBOUND, LINK_SALES, edges);
            addForwardEdges(salesOrders, "purchase_order_no", TYPE_PURCHASE_ORDER, LINK_SALES, edges);
            addReferencingEdges(salesOrders, TYPE_SALES_OUTBOUND, "sales_order_no", LINK_OUTBOUND, edges);
            addReferencingEdges(salesOrders, TYPE_SALES_RETURN, "sales_order_no", LINK_RETURN, edges);
            addFreightBySalesOrderNoEdges(salesOrders, edges);
        }
        if (!salesOutbounds.isEmpty()) {
            addForwardEdges(salesOutbounds, "sales_order_no", TYPE_SALES_ORDER, LINK_OUTBOUND, edges);
            addReturnsByOutboundIdEdges(salesOutbounds, edges);
            addFreightByOutboundIdEdges(salesOutbounds, edges);
        }
        if (!salesReturns.isEmpty()) {
            addForwardEdges(salesReturns, "sales_order_no", TYPE_SALES_ORDER, LINK_RETURN, edges);
            addOutboundsByReturnIdEdges(salesReturns, edges);
            addFreightByReturnIdEdges(salesReturns, edges);
        }
        if (!freightBills.isEmpty()) {
            addFreightToSalesOrderEdges(freightBills, edges);
            addOutboundsByFreightBillIdEdges(freightBills, edges);
        }
        return edges;
    }

    /** 源节点按单号被下游引用(反向): 批量查目标表中引用列包含任一源单号的记录。边方向 源 → 目标。 */
    private void addReferencingEdges(List<FlowNode> sources, String targetType, String refColumn,
                                     String linkType, List<FlowEdge> edges) {
        Set<String> nos = singleNos(sources);
        if (nos.isEmpty()) {
            return;
        }
        Map<String, List<FlowNode>> byRef = referencingNodesBatch(targetType, refColumn, nos);
        for (FlowNode source : sources) {
            for (FlowNode target : byRef.getOrDefault(source.no(), List.of())) {
                edges.add(new FlowEdge(source, target, linkType));
            }
        }
    }

    /**
     * 源节点通过自身引用列指向上游(正向): 批量取引用列 -> 批量按单号查目标。
     * 源是下游、目标是上游, 故边方向固定为 目标 → 源。
     */
    private void addForwardEdges(List<FlowNode> sources, String refColumn, String targetType,
                                 String linkType, List<FlowEdge> edges) {
        List<Long> ids = idsOf(sources);
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> refById = referenceValuesBatch(sources.get(0).type(), refColumn, ids);
        Set<String> targetNos = new LinkedHashSet<>();
        for (String raw : refById.values()) {
            targetNos.addAll(splitReferences(raw));
        }
        Map<String, FlowNode> targets = findByNosBatch(targetType, targetNos);
        for (FlowNode source : sources) {
            for (String no : splitReferences(refById.get(Long.parseLong(source.id())))) {
                FlowNode target = targets.get(no);
                if (target != null) {
                    edges.add(new FlowEdge(target, source, linkType));
                }
            }
        }
    }

    /** 物流单按明细 source_no(多值) 关联销售单。 */
    private void addFreightBySalesOrderNoEdges(List<FlowNode> salesOrders, List<FlowEdge> edges) {
        Set<String> nos = singleNos(salesOrders);
        if (nos.isEmpty()) {
            return;
        }
        Map<String, List<FlowNode>> bySeller = freightBillsBySalesOrderNos(nos);
        for (FlowNode salesOrder : salesOrders) {
            for (FlowNode bill : bySeller.getOrDefault(salesOrder.no(), List.of())) {
                edges.add(new FlowEdge(salesOrder, bill, LINK_FREIGHT));
            }
        }
    }

    /** 物流单按自身明细 source_no 反查上游销售单。 */
    private void addFreightToSalesOrderEdges(List<FlowNode> freightBills, List<FlowEdge> edges) {
        Map<Long, List<String>> sourceNosByBill = sourceNosByBill(freightBills);
        Set<String> orderNos = new LinkedHashSet<>();
        for (List<String> raws : sourceNosByBill.values()) {
            for (String raw : raws) {
                orderNos.addAll(splitReferences(raw));
            }
        }
        Map<String, FlowNode> orders = findByNosBatch(TYPE_SALES_ORDER, orderNos);
        for (FlowNode bill : freightBills) {
            Set<String> matched = new LinkedHashSet<>();
            for (String raw : sourceNosByBill.getOrDefault(Long.parseLong(bill.id()), List.of())) {
                matched.addAll(splitReferences(raw));
            }
            for (String no : matched) {
                FlowNode order = orders.get(no);
                if (order != null) {
                    edges.add(new FlowEdge(order, bill, LINK_FREIGHT));
                }
            }
        }
    }

    private void addReturnsByOutboundIdEdges(List<FlowNode> salesOutbounds, List<FlowEdge> edges) {
        String sql = "SELECT DISTINCT " + aliasColumns(SALES_RETURN_COLUMNS, "ret")
                + ", outbound_item.outbound_id AS source_id FROM so_sales_return ret"
                + " JOIN so_sales_return_item return_item ON return_item.return_id = ret.id"
                + " JOIN so_sales_outbound_item outbound_item"
                + " ON outbound_item.id = return_item.source_sales_outbound_item_id"
                + " WHERE ret.deleted_flag = FALSE AND outbound_item.outbound_id IN (:ids)";
        Map<Long, List<FlowNode>> bySource = queryNodesBySourceId(TYPE_SALES_RETURN, sql, idsOf(salesOutbounds));
        for (FlowNode outbound : salesOutbounds) {
            for (FlowNode salesReturn : bySource.getOrDefault(Long.parseLong(outbound.id()), List.of())) {
                edges.add(new FlowEdge(outbound, salesReturn, LINK_RETURN));
            }
        }
    }

    private void addOutboundsByReturnIdEdges(List<FlowNode> salesReturns, List<FlowEdge> edges) {
        String sql = "SELECT DISTINCT " + aliasColumns(SALES_OUTBOUND_COLUMNS, "outbound")
                + ", return_item.return_id AS source_id FROM so_sales_outbound outbound"
                + " JOIN so_sales_outbound_item outbound_item ON outbound_item.outbound_id = outbound.id"
                + " JOIN so_sales_return_item return_item"
                + " ON return_item.source_sales_outbound_item_id = outbound_item.id"
                + " WHERE outbound.deleted_flag = FALSE AND return_item.return_id IN (:ids)";
        Map<Long, List<FlowNode>> bySource = queryNodesBySourceId(TYPE_SALES_OUTBOUND, sql, idsOf(salesReturns));
        for (FlowNode salesReturn : salesReturns) {
            for (FlowNode outbound : bySource.getOrDefault(Long.parseLong(salesReturn.id()), List.of())) {
                edges.add(new FlowEdge(outbound, salesReturn, LINK_RETURN));
            }
        }
    }

    private void addFreightByOutboundIdEdges(List<FlowNode> salesOutbounds, List<FlowEdge> edges) {
        String sql = "SELECT DISTINCT " + aliasColumns(FREIGHT_BILL_COLUMNS, "bill")
                + ", outbound_item.outbound_id AS source_id FROM lg_freight_bill bill"
                + " JOIN lg_freight_bill_item bill_item ON bill_item.bill_id = bill.id"
                + " JOIN so_sales_outbound_item outbound_item"
                + " ON outbound_item.id = bill_item.source_sales_outbound_item_id"
                + " WHERE bill.deleted_flag = FALSE AND outbound_item.outbound_id IN (:ids)";
        Map<Long, List<FlowNode>> bySource = queryNodesBySourceId(TYPE_FREIGHT_BILL, sql, idsOf(salesOutbounds));
        for (FlowNode outbound : salesOutbounds) {
            for (FlowNode bill : bySource.getOrDefault(Long.parseLong(outbound.id()), List.of())) {
                edges.add(new FlowEdge(outbound, bill, LINK_FREIGHT));
            }
        }
    }

    private void addFreightByReturnIdEdges(List<FlowNode> salesReturns, List<FlowEdge> edges) {
        String sql = "SELECT DISTINCT " + aliasColumns(FREIGHT_BILL_COLUMNS, "bill")
                + ", return_item.return_id AS source_id FROM lg_freight_bill bill"
                + " JOIN so_sales_return_item return_item ON return_item.source_freight_bill_id = bill.id"
                + " WHERE bill.deleted_flag = FALSE AND return_item.return_id IN (:ids)";
        Map<Long, List<FlowNode>> bySource = queryNodesBySourceId(TYPE_FREIGHT_BILL, sql, idsOf(salesReturns));
        for (FlowNode salesReturn : salesReturns) {
            for (FlowNode bill : bySource.getOrDefault(Long.parseLong(salesReturn.id()), List.of())) {
                edges.add(new FlowEdge(bill, salesReturn, LINK_FREIGHT));
            }
        }
    }

    private void addOutboundsByFreightBillIdEdges(List<FlowNode> freightBills, List<FlowEdge> edges) {
        String sql = "SELECT DISTINCT " + aliasColumns(SALES_OUTBOUND_COLUMNS, "outbound")
                + ", bill_item.bill_id AS source_id FROM so_sales_outbound outbound"
                + " JOIN so_sales_outbound_item outbound_item ON outbound_item.outbound_id = outbound.id"
                + " JOIN lg_freight_bill_item bill_item"
                + " ON bill_item.source_sales_outbound_item_id = outbound_item.id"
                + " WHERE outbound.deleted_flag = FALSE AND bill_item.bill_id IN (:ids)";
        Map<Long, List<FlowNode>> bySource = queryNodesBySourceId(TYPE_SALES_OUTBOUND, sql, idsOf(freightBills));
        for (FlowNode bill : freightBills) {
            for (FlowNode outbound : bySource.getOrDefault(Long.parseLong(bill.id()), List.of())) {
                edges.add(new FlowEdge(outbound, bill, LINK_FREIGHT));
            }
        }
    }

    /** 显式指定 RowCallbackHandler, 避免与 ResultSetExtractor 重载歧义。 */
    private void forEachRow(String sql, MapSqlParameterSource params, RowCallbackHandler handler) {
        jdbcTemplate.query(sql, params, handler);
    }

    /** 批量反查引用列(逗号分隔多值): LIKE 预筛后在应用侧精确拆分, 返回 单号 -> 命中节点。 */
    private Map<String, List<FlowNode>> referencingNodesBatch(String type, String column, Set<String> nos) {
        StringBuilder where = new StringBuilder();
        MapSqlParameterSource params = new MapSqlParameterSource();
        int index = 0;
        for (String no : nos) {
            if (index > 0) {
                where.append(" OR ");
            }
            where.append(column).append(" LIKE :p").append(index);
            params.addValue("p" + index, likePattern(no));
            index++;
        }
        String sql = "SELECT " + columnsOf(type) + ", " + column + " AS ref_value FROM " + tableOf(type)
                + " WHERE deleted_flag = FALSE AND (" + where + ")";
        Map<String, List<FlowNode>> result = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        forEachRow(sql, params, resultSet -> {
            FlowNode node = mapNode(type, resultSet);
            for (String no : splitReferences(resultSet.getString("ref_value"))) {
                if (nos.contains(no) && seen.add(no + "|" + node.key())) {
                    result.computeIfAbsent(no, key -> new ArrayList<>()).add(node);
                }
            }
        });
        return result;
    }

    /** 物流单按明细 source_no 反查销售单号。 */
    private Map<String, List<FlowNode>> freightBillsBySalesOrderNos(Set<String> nos) {
        StringBuilder where = new StringBuilder();
        MapSqlParameterSource params = new MapSqlParameterSource();
        int index = 0;
        for (String no : nos) {
            if (index > 0) {
                where.append(" OR ");
            }
            where.append("bill_item.source_no LIKE :p").append(index);
            params.addValue("p" + index, likePattern(no));
            index++;
        }
        String sql = "SELECT DISTINCT " + aliasColumns(FREIGHT_BILL_COLUMNS, "bill")
                + ", bill_item.source_no AS ref_value FROM lg_freight_bill bill"
                + " JOIN lg_freight_bill_item bill_item ON bill_item.bill_id = bill.id"
                + " WHERE bill.deleted_flag = FALSE AND (" + where + ")";
        Map<String, List<FlowNode>> result = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        forEachRow(sql, params, resultSet -> {
            FlowNode node = mapNode(TYPE_FREIGHT_BILL, resultSet);
            for (String no : splitReferences(resultSet.getString("ref_value"))) {
                if (nos.contains(no) && seen.add(no + "|" + node.key())) {
                    result.computeIfAbsent(no, key -> new ArrayList<>()).add(node);
                }
            }
        });
        return result;
    }

    /** 批量按 id 读取引用列值(带软删过滤), 返回 id -> 原始引用字符串。 */
    private Map<Long, String> referenceValuesBatch(String type, String column, Collection<Long> ids) {
        String sql = "SELECT id, " + column + " AS ref_value FROM " + tableOf(type)
                + " WHERE deleted_flag = FALSE AND id IN (:ids)";
        Map<Long, String> result = new LinkedHashMap<>();
        forEachRow(sql, new MapSqlParameterSource("ids", ids), resultSet ->
                result.put(resultSet.getLong("id"), resultSet.getString("ref_value")));
        return result;
    }

    /** 批量按单号查询节点, 返回 单号 -> 节点。 */
    private Map<String, FlowNode> findByNosBatch(String type, Set<String> nos) {
        if (nos.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT " + columnsOf(type) + " FROM " + tableOf(type)
                + " WHERE deleted_flag = FALSE AND " + noColumnOf(type) + " IN (:nos)";
        Map<String, FlowNode> result = new LinkedHashMap<>();
        forEachRow(sql, new MapSqlParameterSource("nos", nos), resultSet -> {
            FlowNode node = mapNode(type, resultSet);
            result.putIfAbsent(node.no(), node);
        });
        return result;
    }

    /** 物流单明细的 source_no 集合, 返回 单据id -> 原始 source_no 列表。 */
    private Map<Long, List<String>> sourceNosByBill(List<FlowNode> freightBills) {
        String sql = "SELECT bill_id, source_no FROM lg_freight_bill_item"
                + " WHERE bill_id IN (:ids) AND COALESCE(BTRIM(source_no), '') <> ''";
        Map<Long, List<String>> result = new LinkedHashMap<>();
        forEachRow(sql, new MapSqlParameterSource("ids", idsOf(freightBills)), resultSet ->
                result.computeIfAbsent(resultSet.getLong("bill_id"), key -> new ArrayList<>())
                        .add(resultSet.getString("source_no")));
        return result;
    }

    /** 按关联列批量查询节点, 返回 源单据id -> 节点列表(用于明细级 id 关联)。 */
    private Map<Long, List<FlowNode>> queryNodesBySourceId(String type, String sql, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<FlowNode>> result = new LinkedHashMap<>();
        forEachRow(sql, new MapSqlParameterSource("ids", ids), resultSet ->
                result.computeIfAbsent(resultSet.getLong("source_id"), key -> new ArrayList<>())
                        .add(mapNode(type, resultSet)));
        return result;
    }

    private static Set<String> singleNos(List<FlowNode> nodes) {
        Set<String> nos = new LinkedHashSet<>();
        for (FlowNode node : nodes) {
            if (node.no() != null && !node.no().isBlank()) {
                nos.add(node.no());
            }
        }
        return nos;
    }

    private static List<Long> idsOf(List<FlowNode> nodes) {
        List<Long> ids = new ArrayList<>();
        for (FlowNode node : nodes) {
            ids.add(Long.parseLong(node.id()));
        }
        return ids;
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
