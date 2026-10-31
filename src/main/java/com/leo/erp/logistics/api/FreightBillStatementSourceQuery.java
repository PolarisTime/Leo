package com.leo.erp.logistics.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface FreightBillStatementSourceQuery {

    CandidatePage findCandidates(CandidateCriteria criteria);

    List<BillSnapshot> findByBillIds(Collection<Long> billIds);

    record CandidateCriteria(
            int page,
            int size,
            String sortBy,
            String direction,
            String keyword,
            Long carrierId,
            String carrierCode,
            String carrierName,
            Long settlementCompanyId,
            LocalDate startDate,
            LocalDate endDate,
            List<Long> excludedBillIds
    ) {
        public CandidateCriteria {
            excludedBillIds = excludedBillIds == null ? List.of() : List.copyOf(excludedBillIds);
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

    /**
     * 物流单候选行快照。
     *
     * <p>不包含客户/项目：{@code lg_freight_bill.customer_name/project_name} 已废弃并在 V208 删除，
     * 物流单的客户/项目信息只保存在明细（{@code lg_freight_bill_item}）与结算主体字段上。</p>
     */
    record CandidateSnapshot(
            Long id,
            String billNo,
            String carrierCode,
            String carrierName,
            Long settlementCompanyId,
            String settlementCompanyName,
            LocalDate billTime,
            BigDecimal totalWeight,
            BigDecimal totalFreight,
            String status,
            Long carrierId
    ) {
    }

    record BillSnapshot(
            Long id,
            String billNo,
            Long carrierId,
            String carrierCode,
            String carrierName,
            Long settlementCompanyId,
            String settlementCompanyName,
            LocalDate billTime,
            BigDecimal unitPrice,
            BigDecimal totalFreight,
            String status,
            List<ItemSnapshot> items
    ) {
        public BillSnapshot {
            items = List.copyOf(items);
        }
    }

    record ItemSnapshot(
            Long id,
            Long settlementCompanyId,
            String settlementCompanyName,
            Long customerId,
            String customerName,
            Long projectId,
            String projectName,
            Long materialId,
            String materialCode,
            String materialName,
            String brand,
            String category,
            String material,
            String spec,
            String length,
            Integer quantity,
            String quantityUnit,
            BigDecimal pieceWeightTon,
            Integer piecesPerBundle,
            String batchNo,
            BigDecimal weightTon,
            Long warehouseId,
            String warehouseName
    ) {
    }
}
