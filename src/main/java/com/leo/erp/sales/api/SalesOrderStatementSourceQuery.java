package com.leo.erp.sales.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface SalesOrderStatementSourceQuery {

    CandidatePage findCandidates(CandidateCriteria criteria);

    List<OrderSnapshot> findBySourceItemIds(Collection<Long> sourceItemIds);

    record CandidateCriteria(
            int page,
            int size,
            String sortBy,
            String direction,
            String keyword,
            Long customerId,
            Long projectId,
            String customerName,
            String projectName,
            Long settlementCompanyId,
            LocalDate startDate,
            LocalDate endDate,
            List<Long> excludedSourceItemIds
    ) {
        public CandidateCriteria {
            excludedSourceItemIds = excludedSourceItemIds == null
                    ? List.of()
                    : List.copyOf(excludedSourceItemIds);
        }
    }

    record CandidatePage(
            List<CandidateSnapshot> content,
            long totalElements,
            int totalPages,
            int page,
            int size
    ) {
        public CandidatePage {
            content = List.copyOf(content);
        }
    }

    record CandidateSnapshot(
            Long id,
            String orderNo,
            String customerName,
            String projectName,
            Long settlementCompanyId,
            String settlementCompanyName,
            LocalDate deliveryDate,
            String salesName,
            BigDecimal totalWeight,
            BigDecimal totalAmount,
            String status,
            Long customerId,
            Long projectId
    ) {
    }

    record OrderSnapshot(
            Long id,
            String orderNo,
            String customerCode,
            Long customerId,
            String customerName,
            Long projectId,
            String projectName,
            Long settlementCompanyId,
            String settlementCompanyName,
            String status,
            List<ItemSnapshot> items
    ) {
        public OrderSnapshot {
            items = List.copyOf(items);
        }
    }

    record ItemSnapshot(
            Long id,
            Long materialId,
            String materialCode,
            String brand,
            String category,
            String material,
            String spec,
            String length,
            String unit,
            Long warehouseId,
            String batchNo,
            Integer quantity,
            String quantityUnit,
            BigDecimal pieceWeightTon,
            Integer piecesPerBundle,
            BigDecimal weightTon,
            BigDecimal unitPrice,
            BigDecimal amount,
            AuditedOutboundActualSnapshot auditedOutboundActual
    ) {
    }

    record AuditedOutboundActualSnapshot(
            long quantity,
            BigDecimal weightTon,
            BigDecimal amount
    ) {
    }
}
