package com.leo.erp.statement.customer.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.sales.api.SalesReturnReversalCommand;
import com.leo.erp.sales.api.SalesReturnReversalSourceQuery;
import com.leo.erp.sales.api.SalesReturnReversalSourceQuery.ItemSnapshot;
import com.leo.erp.sales.api.SalesReturnReversalSourceQuery.ReturnSnapshot;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.service.StatementBalanceRule;
import com.leo.erp.statement.service.StatementSettlementMutationGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 红字对账单生成服务。
 * <p>
 * 退货审核后调用：仅当退货明细的来源销售订单明细已被非删除蓝字对账单占用时，
 * 才按退货明细生成负金额红字对账单；未被占用则不生成（后续蓝字对账直接走净额）。
 * <ul>
 *   <li>红字对账单自动创建为 {@code 已确认}，amount/weight/quantity 为负；</li>
 *   <li>不占用来源销售订单明细（占用查询按 direction=蓝字 过滤），不参与收款核销；</li>
 *   <li>按来源退货单幂等：同一退货单只生成一次。</li>
 * </ul>
 * 退货单头与明细通过 {@link SalesReturnReversalSourceQuery} 只读端口获取快照，
 * 避免对账模块直接依赖退货模块内部实体与仓储。
 */
@Service
public class CustomerStatementReversalService implements SalesReturnReversalCommand {

    private static final Logger log = LoggerFactory.getLogger(CustomerStatementReversalService.class);

    private final CustomerStatementRepository repository;
    private final SalesReturnReversalSourceQuery salesReturnSourceQuery;
    private final SnowflakeIdGenerator idGenerator;
    private final StatementSettlementMutationGuard settlementMutationGuard;

    public CustomerStatementReversalService(CustomerStatementRepository repository,
                                            SalesReturnReversalSourceQuery salesReturnSourceQuery,
                                            SnowflakeIdGenerator idGenerator,
                                            StatementSettlementMutationGuard settlementMutationGuard) {
        this.repository = repository;
        this.salesReturnSourceQuery = salesReturnSourceQuery;
        this.idGenerator = idGenerator;
        this.settlementMutationGuard = settlementMutationGuard;
    }

    @Override
    @Transactional
    public void reverseForAuditedReturn(Long salesReturnId) {
        if (salesReturnId == null) {
            return;
        }
        ReturnSnapshot salesReturn = salesReturnSourceQuery.findById(salesReturnId);
        if (salesReturn == null
                || !StatusConstants.AUDITED.equals(normalize(salesReturn.status()))) {
            return;
        }
        // 幂等重建：先清理该退货单已有有效红字（可能因异常流程残留），再按最新明细重建。
        softDeleteActiveReversals(salesReturnId, "重新审核销售退货单");
        List<ItemSnapshot> reversalItems = resolveOccupiedReturnItems(salesReturn);
        if (reversalItems.isEmpty()) {
            log.debug("销售退货单未占用蓝字对账单，跳过红字生成: returnId={}", salesReturnId);
            return;
        }
        CustomerStatement reversal = buildReversalStatement(salesReturn, reversalItems);
        repository.save(reversal);
        log.info("红字对账单已生成: statementId={}, returnNo={}",
                reversal.getId(), salesReturn.returnNo());
    }

    @Override
    @Transactional
    public void revertForReturn(Long salesReturnId) {
        if (salesReturnId == null) {
            return;
        }
        softDeleteActiveReversals(salesReturnId, "反审核销售退货单");
    }

    /**
     * 软删该退货单当前所有有效红字；已被收款核销引用时通过
     * {@link StatementSettlementMutationGuard} 拒绝撤销。
     * <p>
     * 使用 {@code saveAndFlush} 保证软删 UPDATE 先于后续新红字 INSERT 落库，
     * 避免命中部分唯一索引 {@code uk_st_customer_statement_source_return_active}。
     */
    private void softDeleteActiveReversals(Long salesReturnId, String action) {
        List<CustomerStatement> activeReversals =
                repository.findBySourceSalesReturnIdAndDeletedFlagFalse(salesReturnId);
        for (CustomerStatement reversal : activeReversals) {
            settlementMutationGuard.assertNoSettledAllocations(
                    StatementSettlementMutationGuard.StatementType.CUSTOMER,
                    reversal.getId(),
                    action
            );
            reversal.setDeletedFlag(true);
            repository.saveAndFlush(reversal);
            log.info("红字对账单已撤销: statementId={}, returnId={}, action={}",
                    reversal.getId(), salesReturnId, action);
        }
    }

    private List<ItemSnapshot> resolveOccupiedReturnItems(ReturnSnapshot salesReturn) {
        List<ItemSnapshot> items = salesReturn.items();
        List<Long> sourceOrderItemIds = items.stream()
                .map(ItemSnapshot::sourceSalesOrderItemId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (sourceOrderItemIds.isEmpty()) {
            return List.of();
        }
        Set<Long> occupied = new LinkedHashSet<>(
                repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(
                        sourceOrderItemIds,
                        null
                )
        );
        if (occupied.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(item -> item.sourceSalesOrderItemId() != null)
                .filter(item -> occupied.contains(item.sourceSalesOrderItemId()))
                .toList();
    }

    private CustomerStatement buildReversalStatement(ReturnSnapshot salesReturn, List<ItemSnapshot> reversalItems) {
        long entityId = idGenerator.nextId();
        CustomerStatement entity = new CustomerStatement();
        entity.setId(entityId);
        entity.setStatementNo(String.valueOf(entityId));
        entity.setDirection(StatusConstants.STATEMENT_DIRECTION_RED);
        entity.setSourceSalesReturnId(salesReturn.id());
        entity.setSourceSalesReturnNo(salesReturn.returnNo());
        entity.setCustomerId(salesReturn.customerId());
        entity.setCustomerName(salesReturn.customerName());
        entity.setProjectId(salesReturn.projectId());
        entity.setProjectName(salesReturn.projectName());
        entity.setSettlementCompanyId(salesReturn.settlementCompanyId());
        entity.setSettlementCompanyName(salesReturn.settlementCompanyName());
        entity.setStartDate(salesReturn.returnDate());
        entity.setEndDate(salesReturn.returnDate());
        entity.setStatus(StatusConstants.CONFIRMED);
        entity.setRemark("销售退货 " + salesReturn.returnNo() + " 红字冲销");

        BigDecimal totalAmount = BigDecimal.ZERO;
        int lineNo = 1;
        for (List<ItemSnapshot> group : groupBySourceOrderItem(reversalItems).values()) {
            CustomerStatementItem item = toReversalItem(entity, group, lineNo++);
            entity.getItems().add(item);
            totalAmount = totalAmount.add(item.getAmount());
        }
        StatementBalanceRule.Balance balance = StatementBalanceRule.resolveReversal(
                TradeItemCalculator.scaleAmount(totalAmount)
        );
        entity.setSalesAmount(balance.sourceAmount());
        entity.setReceiptAmount(balance.settledAmount());
        entity.setClosingAmount(balance.closingAmount());
        return entity;
    }

    /**
     * 按来源销售订单明细聚合，避免同一订单明细对应多条出库明细时产生重复行
     * （{@code uk_st_customer_statement_item_source_line} 约束）。
     */
    private Map<Long, List<ItemSnapshot>> groupBySourceOrderItem(List<ItemSnapshot> items) {
        Map<Long, List<ItemSnapshot>> grouped = new LinkedHashMap<>();
        for (ItemSnapshot item : items) {
            grouped.computeIfAbsent(item.sourceSalesOrderItemId(), key -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private CustomerStatementItem toReversalItem(CustomerStatement entity,
                                                 List<ItemSnapshot> group,
                                                 int lineNo) {
        ItemSnapshot first = group.get(0);
        int quantity = 0;
        BigDecimal weightTon = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        for (ItemSnapshot source : group) {
            quantity += source.quantity() == null ? 0 : source.quantity();
            weightTon = weightTon.add(TradeItemCalculator.scaleWeightTon(source.weightTon()));
            amount = amount.add(TradeItemCalculator.scaleAmount(source.amount()));
        }
        CustomerStatementItem item = new CustomerStatementItem();
        item.setId(idGenerator.nextId());
        item.setCustomerStatement(entity);
        item.setLineNo(lineNo);
        item.setSourceNo(entity.getSourceSalesReturnNo());
        item.setSourceSalesOrderItemId(first.sourceSalesOrderItemId());
        item.setCustomerId(entity.getCustomerId());
        item.setProjectId(entity.getProjectId());
        item.setMaterialId(first.materialId());
        item.setWarehouseId(first.warehouseId());
        item.setMaterialCode(first.materialCode());
        item.setBrand(first.brand());
        item.setCategory(first.category());
        item.setMaterial(first.material());
        item.setSpec(first.spec());
        item.setLength(first.length());
        item.setUnit(first.unit());
        item.setBatchNo(sameText(group, ItemSnapshot::batchNo) ? first.batchNo() : null);
        item.setQuantity(-quantity);
        item.setQuantityUnit(first.quantityUnit());
        item.setPieceWeightTon(TradeItemCalculator.scaleWeightTon(first.pieceWeightTon()));
        item.setPiecesPerBundle(first.piecesPerBundle());
        item.setWeightTon(TradeItemCalculator.scaleWeightTon(weightTon).negate());
        item.setUnitPrice(TradeItemCalculator.scaleAmount(first.unitPrice()));
        item.setAmount(TradeItemCalculator.scaleAmount(amount).negate());
        return item;
    }

    private boolean sameText(List<ItemSnapshot> group, java.util.function.Function<ItemSnapshot, String> getter) {
        String first = getter.apply(group.get(0));
        return group.stream().allMatch(item -> Objects.equals(first, getter.apply(item)));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
