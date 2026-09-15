package com.leo.erp.common.concurrency;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * 来源占用加锁服务。
 *
 * <p>事务级行锁必须按确定顺序获取，否则并发多来源交叠时会低概率死锁。
 * PostgreSQL 不保证多表 JOIN 的 {@code FOR UPDATE OF a, b} 按 {@code ORDER BY} 的顺序加锁，
 * 因此这里不再使用「JOIN + ORDER BY + FOR UPDATE OF 多表」，而是拆成单表语句：
 * 每条语句只锁一张表，且 {@code WHERE id IN (...)} 配 {@code ORDER BY id FOR UPDATE}，
 * 由主键索引自然按 id 升序逐行加锁。
 *
 * <p>跨方法的全局顺序取「表名字典序」，父表恰好先于对应子表。所有公开方法都先把本方法
 * 涉及的锁步骤按该序排序后串行执行，从而消除方法之间的 AB-BA 死锁。
 */
@Service
public class SourceAllocationLockService {

    // 全局固定加锁序（表名字典序）：父表先于子表，跨方法一致。
    private static final int RANK_FREIGHT_BILL = 10;
    private static final int RANK_PURCHASE_INBOUND = 20;
    private static final int RANK_PURCHASE_INBOUND_ITEM = 21;
    private static final int RANK_PURCHASE_ORDER = 30;
    private static final int RANK_PURCHASE_ORDER_ITEM = 31;
    private static final int RANK_SALES_ORDER = 40;
    private static final int RANK_SALES_ORDER_ITEM = 41;
    private static final int RANK_SALES_OUTBOUND = 50;
    private static final int RANK_SALES_OUTBOUND_ITEM = 51;
    private static final int RANK_CUSTOMER_STATEMENT = 60;
    private static final int RANK_FREIGHT_STATEMENT = 61;

    /** 单表按主键升序加锁：字典/对账单等文档表。 */
    private static final String LOCK_DOCUMENT_SQL = """
            SELECT source_record.id
            FROM %s source_record
            WHERE source_record.id IN (:sourceIds)
            ORDER BY source_record.id
            FOR UPDATE OF source_record
            """;

    /** 单表按主键升序加锁：明细表。 */
    private static final String LOCK_ITEM_SQL = """
            SELECT source_item.id
            FROM %s source_item
            WHERE source_item.id IN (:sourceIds)
            ORDER BY source_item.id
            FOR UPDATE OF source_item
            """;

    /**
     * 单据头行锁：按子表给出的 sourceIds 反查父表主键后，仅对父表单表升序加锁。
     * 子查询只用于取父表主键集合，不参与加锁。
     */
    private static final String LOCK_ITEM_PARENT_SQL = """
            SELECT source_parent.id
            FROM %s source_parent
            WHERE source_parent.id IN (
                SELECT source_item.%s
                FROM %s source_item
                WHERE source_item.id IN (:sourceIds)
            )
            ORDER BY source_parent.id
            FOR UPDATE OF source_parent
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SourceAllocationLockService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockTradeItemSources(Collection<Long> purchaseOrderItemIds,
                                     Collection<Long> purchaseInboundItemIds,
                                     Collection<Long> salesOrderItemIds) {
        List<LockStep> steps = new ArrayList<>();
        steps.addAll(itemSteps(RANK_PURCHASE_ORDER, "po_purchase_order", "order_id",
                RANK_PURCHASE_ORDER_ITEM, "po_purchase_order_item", "采购订单明细", purchaseOrderItemIds));
        steps.addAll(itemSteps(RANK_PURCHASE_INBOUND, "po_purchase_inbound", "inbound_id",
                RANK_PURCHASE_INBOUND_ITEM, "po_purchase_inbound_item", "采购入库明细", purchaseInboundItemIds));
        steps.addAll(itemSteps(RANK_SALES_ORDER, "so_sales_order", "order_id",
                RANK_SALES_ORDER_ITEM, "so_sales_order_item", "销售订单明细", salesOrderItemIds));
        execute(steps);
    }

    /**
     * 锁定销售出库明细及其父出库单行。用于销售退货审核前封死并发超退窗口。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockSalesOutboundItemSources(Collection<Long> salesOutboundItemIds) {
        execute(itemSteps(RANK_SALES_OUTBOUND, "so_sales_outbound", "outbound_id",
                RANK_SALES_OUTBOUND_ITEM, "so_sales_outbound_item", "销售出库明细", salesOutboundItemIds));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockDocumentSources(Collection<Long> purchaseInboundIds,
                                    Collection<Long> salesOrderIds,
                                    Collection<Long> salesOutboundIds,
                                    Collection<Long> freightBillIds) {
        execute(List.of(
                documentStep(RANK_PURCHASE_INBOUND, "po_purchase_inbound", "采购入库单", purchaseInboundIds),
                documentStep(RANK_SALES_ORDER, "so_sales_order", "销售订单", salesOrderIds),
                documentStep(RANK_SALES_OUTBOUND, "so_sales_outbound", "销售出库单", salesOutboundIds),
                documentStep(RANK_FREIGHT_BILL, "lg_freight_bill", "物流单", freightBillIds)
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockStatementSources(Collection<Long> customerStatementIds,
                                     Collection<Long> freightStatementIds) {
        execute(List.of(
                documentStep(RANK_CUSTOMER_STATEMENT, "st_customer_statement", "客户对账单", customerStatementIds),
                documentStep(RANK_FREIGHT_STATEMENT, "st_freight_statement", "物流对账单", freightStatementIds)
        ));
    }

    private void execute(List<LockStep> steps) {
        steps.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(LockStep::rank))
                .forEach(this::lock);
    }

    private void lock(LockStep step) {
        if (step.sourceIds().isEmpty()) {
            return;
        }
        List<Long> lockedIds = jdbcTemplate.queryForList(
                step.sql(),
                new MapSqlParameterSource("sourceIds", step.sourceIds()),
                Long.class
        ).stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (step.verifyExistence() && !lockedIds.equals(step.sourceIds())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, step.sourceName() + "不存在或已失效");
        }
    }

    /**
     * 生成「先父表后子表」的两个锁步骤，分别只锁一张表、按主键升序。
     * 父表步骤不校验存在性（外键保证父行存在），子表步骤校验全部 ID 命中。
     */
    private List<LockStep> itemSteps(int parentRank,
                                     String parentTable,
                                     String parentForeignKey,
                                     int itemRank,
                                     String itemTable,
                                     String sourceName,
                                     Collection<Long> sourceIds) {
        List<Long> normalizedIds = normalizeIds(sourceIds);
        return List.of(
                new LockStep(parentRank, sourceName,
                        LOCK_ITEM_PARENT_SQL.formatted(parentTable, parentForeignKey, itemTable),
                        normalizedIds, false),
                new LockStep(itemRank, sourceName,
                        LOCK_ITEM_SQL.formatted(itemTable),
                        normalizedIds, true)
        );
    }

    private LockStep documentStep(int rank, String tableName, String sourceName, Collection<Long> sourceIds) {
        return new LockStep(rank, sourceName, LOCK_DOCUMENT_SQL.formatted(tableName), normalizeIds(sourceIds), true);
    }

    private List<Long> normalizeIds(Collection<Long> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return List.of();
        }
        TreeSet<Long> orderedIds = new TreeSet<>();
        sourceIds.stream().filter(Objects::nonNull).forEach(orderedIds::add);
        return List.copyOf(orderedIds);
    }

    /**
     * 单个加锁步骤：rank 决定全局加锁顺序，sourceIds 为该步骤要锁定的主键集合。
     */
    private record LockStep(int rank, String sourceName, String sql, List<Long> sourceIds,
                            boolean verifyExistence) {
    }
}
