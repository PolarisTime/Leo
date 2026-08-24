package com.leo.erp.statement.customer.service;

import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.sales.api.SalesOrderLogisticsSourceQuery;
import com.leo.erp.statement.customer.mapper.CustomerStatementMapper;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomerStatementResponseAssembler {

    private final CustomerStatementMapper customerStatementMapper;
    private final SalesOrderLogisticsSourceQuery salesOrderSourceQuery;

    public CustomerStatementResponseAssembler(CustomerStatementMapper customerStatementMapper,
                                              SalesOrderLogisticsSourceQuery salesOrderSourceQuery) {
        this.customerStatementMapper = customerStatementMapper;
        this.salesOrderSourceQuery = salesOrderSourceQuery;
    }

    CustomerStatementResponse toSummaryResponse(CustomerStatement entity) {
        return customerStatementMapper.toResponse(entity);
    }

    CustomerStatementResponse toDetailResponse(CustomerStatement entity) {
        CustomerStatementResponse response = customerStatementMapper.toResponse(entity);
        Set<Long> sourceItemIds = entity.getItems().stream()
                .map(CustomerStatementItem::getSourceSalesOrderItemId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, LocalDate> deliveryDateByItemId = resolveDeliveryDates(sourceItemIds);
        return new CustomerStatementResponse(
                response.id(),
                response.statementNo(),
                response.customerCode(),
                response.customerName(),
                response.projectId(),
                response.projectName(),
                response.settlementCompanyId(),
                response.settlementCompanyName(),
                response.startDate(),
                response.endDate(),
                response.salesAmount(),
                response.receiptAmount(),
                response.closingAmount(),
                response.status(),
                response.deletedFlag(),
                response.remark(),
                entity.getItems().stream()
                        .map(item -> toItemResponse(item, deliveryDateByItemId))
                        .toList(),
                entity.getCustomerId()
        );
    }

    private Map<Long, LocalDate> resolveDeliveryDates(Set<Long> sourceItemIds) {
        if (sourceItemIds.isEmpty()) {
            return Map.of();
        }
        return salesOrderSourceQuery.findBySourceItemIds(sourceItemIds)
                .stream()
                .flatMap(order -> order.items().stream()
                        .map(item -> Map.entry(item.id(), order.deliveryDate())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left));
    }

    private CustomerStatementItemResponse toItemResponse(CustomerStatementItem item,
                                                         Map<Long, LocalDate> deliveryDateByItemId) {
        LocalDate deliveryDate = item.getSourceSalesOrderItemId() == null
                ? null
                : deliveryDateByItemId.get(item.getSourceSalesOrderItemId());
        return new CustomerStatementItemResponse(
                item.getId(),
                item.getLineNo(),
                item.getSourceNo(),
                item.getSourceSalesOrderItemId(),
                deliveryDate,
                item.getMaterialCode(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getUnit(),
                item.getBatchNo(),
                item.getQuantity(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getWeightTon(),
                item.getUnitPrice(),
                item.getAmount(),
                item.getCustomerId(),
                item.getProjectId(),
                item.getMaterialId(),
                item.getWarehouseId(),
                item.getBatchNoNormalized()
        );
    }
}
