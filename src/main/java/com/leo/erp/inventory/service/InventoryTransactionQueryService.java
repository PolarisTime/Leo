package com.leo.erp.inventory.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.inventory.api.InventoryTransactionType;
import com.leo.erp.inventory.domain.entity.InventoryTransaction;
import com.leo.erp.inventory.repository.InventoryTransactionRepository;
import com.leo.erp.inventory.web.dto.InventoryTransactionResponse;
import com.leo.erp.master.material.domain.entity.Material;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 库存事务流水分页查询。
 */
@Service
public class InventoryTransactionQueryService {

    private static final String[] SEARCH_FIELDS = {
            "transactionNo", "materialCode", "warehouseName", "batchNo", "sourceDocumentNo"
    };

    private final InventoryTransactionRepository repository;

    public InventoryTransactionQueryService(InventoryTransactionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionResponse> page(PageQuery query,
                                                           String keyword,
                                                           Long materialId,
                                                           Long warehouseId,
                                                           String transactionType,
                                                           LocalDate startDate,
                                                           LocalDate endDate) {
        Specification<InventoryTransaction> spec = Specs
                .<InventoryTransaction>notDeleted()
                .and(fetchMaterial())
                .and(Specs.keywordLike(keyword, SEARCH_FIELDS))
                .and(Specs.equalValueIfPresent("materialId", materialId))
                .and(Specs.equalValueIfPresent("warehouseId", warehouseId))
                .and(Specs.equalIfPresent("transactionType", normalizeType(transactionType)))
                .and(Specs.betweenIfPresent("occurredAt", startDate, endDate));
        Page<InventoryTransaction> page = repository.findAll(spec, query.toPageable("id"));
        return PageResponse.from(page.map(this::toResponse));
    }

    /**
     * 只读 LEFT JOIN 物料主数据用于富集品牌/材质/规格/长度/单位快照；
     * 计数查询不执行 fetch，避免 count 查询携带无意义 join。
     */
    private static Specification<InventoryTransaction> fetchMaterial() {
        return (root, query, cb) -> {
            Class<?> resultType = query.getResultType();
            if (resultType != Long.class && resultType != long.class) {
                root.fetch("material", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }

    private String normalizeType(String transactionType) {
        if (transactionType == null || transactionType.isBlank()) {
            return null;
        }
        String normalized = transactionType.trim();
        try {
            return InventoryTransactionType.valueOf(normalized).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "transactionType 不合法");
        }
    }

    private InventoryTransactionResponse toResponse(InventoryTransaction entity) {
        Material material = entity.getMaterial();
        return new InventoryTransactionResponse(
                entity.getId(),
                entity.getTransactionNo(),
                entity.getTransactionType(),
                entity.getMaterialId(),
                entity.getMaterialCode(),
                material == null ? null : material.getBrand(),
                material == null ? null : material.getMaterial(),
                material == null ? null : material.getSpec(),
                material == null ? null : material.getLength(),
                material == null ? null : material.getUnit(),
                entity.getWarehouseId(),
                entity.getWarehouseName(),
                entity.getBatchNo(),
                entity.getDirection(),
                entity.getQuantity(),
                entity.getQuantityUnit(),
                entity.getUnitCost(),
                entity.getAmount(),
                entity.getSourceDocumentType(),
                entity.getSourceDocumentId(),
                entity.getSourceDocumentNo(),
                entity.getSourceItemId(),
                entity.getOccurredAt(),
                entity.getCreatedAt()
        );
    }
}
