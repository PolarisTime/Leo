package com.leo.erp.statement.customer.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.sales.api.SalesReturnReversalCommand;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import com.leo.erp.sales.returns.domain.entity.SalesReturnItem;
import com.leo.erp.sales.returns.repository.SalesReturnRepository;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.service.StatementBalanceRule;
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
 * 直接按退货明细构建，不复用 {@link CustomerStatementSourceService}（其要求来源订单完成销售且存在出库实际）。
 */
@Service
public class CustomerStatementReversalService implements SalesReturnReversalCommand {

    private static final Logger log = LoggerFactory.getLogger(CustomerStatementReversalService.class);

    private final CustomerStatementRepository repository;
    private final SalesReturnRepository salesReturnRepository;
    private final SnowflakeIdGenerator idGenerator;

    public CustomerStatementReversalService(CustomerStatementRepository repository,
                                            SalesReturnRepository salesReturnRepository,
                                            SnowflakeIdGenerator idGenerator) {
        this.repository = repository;
        this.salesReturnRepository = salesReturnRepository;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public void reverseForAuditedReturn(Long salesReturnId) {
        if (salesReturnId == null
                || repository.existsBySourceSalesReturnIdAndDeletedFlagFalse(salesReturnId)) {
            return;
        }
        SalesReturn salesReturn = salesReturnRepository.findByIdAndDeletedFlagFalse(salesReturnId)
                .orElse(null);
        if (salesReturn == null
                || !StatusConstants.AUDITED.equals(normalize(salesReturn.getStatus()))) {
            return;
        }
        List<SalesReturnItem> reversalItems = resolveOccupiedReturnItems(salesReturn);
        if (reversalItems.isEmpty()) {
            log.debug("销售退货单未占用蓝字对账单，跳过红字生成: returnId={}", salesReturnId);
            return;
        }
        CustomerStatement reversal = buildReversalStatement(salesReturn, reversalItems);
        repository.save(reversal);
        log.info("红字对账单已生成: statementId={}, returnNo={}",
                reversal.getId(), salesReturn.getReturnNo());
    }

    private List<SalesReturnItem> resolveOccupiedReturnItems(SalesReturn salesReturn) {
        List<SalesReturnItem> items = salesReturn.getItems() == null
                ? List.of()
                : salesReturn.getItems();
        List<Long> sourceOrderItemIds = items.stream()
                .map(SalesReturnItem::getSourceSalesOrderItemId)
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
                .filter(item -> item.getSourceSalesOrderItemId() != null)
                .filter(item -> occupied.contains(item.getSourceSalesOrderItemId()))
                .toList();
    }

    private CustomerStatement buildReversalStatement(SalesReturn salesReturn, List<SalesReturnItem> reversalItems) {
        long entityId = idGenerator.nextId();
        CustomerStatement entity = new CustomerStatement();
        entity.setId(entityId);
        entity.setStatementNo(String.valueOf(entityId));
        entity.setDirection(StatusConstants.STATEMENT_DIRECTION_RED);
        entity.setSourceSalesReturnId(salesReturn.getId());
        entity.setSourceSalesReturnNo(salesReturn.getReturnNo());
        entity.setCustomerId(salesReturn.getCustomerId());
        entity.setCustomerName(salesReturn.getCustomerName());
        entity.setProjectId(salesReturn.getProjectId());
        entity.setProjectName(salesReturn.getProjectName());
        entity.setSettlementCompanyId(salesReturn.getSettlementCompanyId());
        entity.setSettlementCompanyName(salesReturn.getSettlementCompanyName());
        entity.setStartDate(salesReturn.getReturnDate());
        entity.setEndDate(salesReturn.getReturnDate());
        entity.setStatus(StatusConstants.CONFIRMED);
        entity.setRemark("销售退货 " + salesReturn.getReturnNo() + " 红字冲销");

        BigDecimal totalAmount = BigDecimal.ZERO;
        int lineNo = 1;
        for (List<SalesReturnItem> group : groupBySourceOrderItem(reversalItems).values()) {
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
    private Map<Long, List<SalesReturnItem>> groupBySourceOrderItem(List<SalesReturnItem> items) {
        Map<Long, List<SalesReturnItem>> grouped = new LinkedHashMap<>();
        for (SalesReturnItem item : items) {
            grouped.computeIfAbsent(item.getSourceSalesOrderItemId(), key -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private CustomerStatementItem toReversalItem(CustomerStatement entity,
                                                 List<SalesReturnItem> group,
                                                 int lineNo) {
        SalesReturnItem first = group.get(0);
        int quantity = 0;
        BigDecimal weightTon = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        for (SalesReturnItem source : group) {
            quantity += source.getQuantity() == null ? 0 : source.getQuantity();
            weightTon = weightTon.add(TradeItemCalculator.scaleWeightTon(source.getWeightTon()));
            amount = amount.add(TradeItemCalculator.scaleAmount(source.getAmount()));
        }
        CustomerStatementItem item = new CustomerStatementItem();
        item.setId(idGenerator.nextId());
        item.setCustomerStatement(entity);
        item.setLineNo(lineNo);
        item.setSourceNo(entity.getSourceSalesReturnNo());
        item.setSourceSalesOrderItemId(first.getSourceSalesOrderItemId());
        item.setCustomerId(entity.getCustomerId());
        item.setProjectId(entity.getProjectId());
        item.setMaterialId(first.getMaterialId());
        item.setWarehouseId(first.getWarehouseId());
        item.setMaterialCode(first.getMaterialCode());
        item.setBrand(first.getBrand());
        item.setCategory(first.getCategory());
        item.setMaterial(first.getMaterial());
        item.setSpec(first.getSpec());
        item.setLength(first.getLength());
        item.setUnit(first.getUnit());
        item.setBatchNo(sameText(group, SalesReturnItem::getBatchNo) ? first.getBatchNo() : null);
        item.setQuantity(-quantity);
        item.setQuantityUnit(first.getQuantityUnit());
        item.setPieceWeightTon(TradeItemCalculator.scaleWeightTon(first.getPieceWeightTon()));
        item.setPiecesPerBundle(first.getPiecesPerBundle());
        item.setWeightTon(TradeItemCalculator.scaleWeightTon(weightTon).negate());
        item.setUnitPrice(TradeItemCalculator.scaleAmount(first.getUnitPrice()));
        item.setAmount(TradeItemCalculator.scaleAmount(amount).negate());
        return item;
    }

    private boolean sameText(List<SalesReturnItem> group, java.util.function.Function<SalesReturnItem, String> getter) {
        String first = getter.apply(group.get(0));
        return group.stream().allMatch(item -> Objects.equals(first, getter.apply(item)));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
