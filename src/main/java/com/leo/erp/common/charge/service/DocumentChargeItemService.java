package com.leo.erp.common.charge.service;

import com.leo.erp.common.charge.api.DocumentChargeItemRequest;
import com.leo.erp.common.charge.api.DocumentChargeItemResponse;
import com.leo.erp.common.charge.domain.entity.DocumentChargeItem;
import com.leo.erp.common.charge.repository.DocumentChargeItemRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通用单据附加费用同步服务：各单据 Service 在 create/update 同一事务内调用
 * {@link #sync}，以请求费用行全量替换当前有效费用行（软删旧行 + 重编号写入）。
 */
@Service
public class DocumentChargeItemService {

    /** 允许接入费用明细的单据模块白名单，防止通用表被任意 moduleKey 写入。 */
    private static final Set<String> ALLOWED_MODULE_KEYS = Set.of(
            "purchase-order", "sales-order", "freight-bill"
    );

    private final DocumentChargeItemRepository repository;
    private final SnowflakeIdGenerator idGenerator;

    public DocumentChargeItemService(DocumentChargeItemRepository repository,
                                     SnowflakeIdGenerator idGenerator) {
        this.repository = repository;
        this.idGenerator = idGenerator;
    }

    /**
     * 全量同步单据的费用行：请求列表为空时清空现有费用行；
     * 存量行按 id 复用（保留审计轨迹），新行分配新 ID；line_no 按请求顺序重编号。
     */
    @Transactional
    public void sync(String moduleKey, Long documentId, List<DocumentChargeItemRequest> requests) {
        requireAllowedModule(moduleKey);
        List<DocumentChargeItemRequest> safeRequests = requests == null ? List.of() : requests;
        List<DocumentChargeItem> existing = repository
                .findByModuleKeyAndDocumentIdAndDeletedFlagFalseOrderByLineNoAscIdAsc(moduleKey, documentId);

        Map<Long, DocumentChargeItem> existingById = existing.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(DocumentChargeItem::getId, Function.identity()));
        Set<Long> retainedIds = new HashSet<>();

        int lineNo = 0;
        for (DocumentChargeItemRequest request : safeRequests) {
            lineNo += 1;
            DocumentChargeItem item = request.id() == null ? null : existingById.get(request.id());
            if (item == null) {
                item = new DocumentChargeItem();
                item.setId(idGenerator.nextId());
                item.setModuleKey(moduleKey);
                item.setDocumentId(documentId);
            } else {
                requireSameDocument(item, moduleKey, documentId);
                retainedIds.add(item.getId());
            }
            apply(item, request, lineNo);
            repository.save(item);
        }

        for (DocumentChargeItem stale : existing) {
            if (stale.getId() != null && !retainedIds.contains(stale.getId())) {
                stale.setDeletedFlag(true);
                repository.save(stale);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentChargeItemResponse> list(String moduleKey, Long documentId) {
        return repository
                .findByModuleKeyAndDocumentIdAndDeletedFlagFalseOrderByLineNoAscIdAsc(moduleKey, documentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<DocumentChargeItemResponse>> listByDocumentIds(String moduleKey,
                                                                         Collection<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByModuleKeyAndDocumentIdInAndDeletedFlagFalse(moduleKey, documentIds)
                .stream()
                .collect(Collectors.groupingBy(
                        DocumentChargeItem::getDocumentId,
                        Collectors.mapping(this::toResponse, Collectors.toList())
                ));
    }

    /** 单据删除时级联软删其费用行。 */
    @Transactional
    public void removeAll(String moduleKey, Long documentId) {
        requireAllowedModule(moduleKey);
        for (DocumentChargeItem item : repository
                .findByModuleKeyAndDocumentIdAndDeletedFlagFalseOrderByLineNoAscIdAsc(moduleKey, documentId)) {
            item.setDeletedFlag(true);
            repository.save(item);
        }
    }

    public BigDecimal sumAmount(List<DocumentChargeItemResponse> chargeItems) {
        BigDecimal total = BigDecimal.ZERO;
        for (DocumentChargeItemResponse item : chargeItems == null ? List.<DocumentChargeItemResponse>of() : chargeItems) {
            if (item.amount() != null) {
                total = total.add(item.amount());
            }
        }
        return total;
    }

    private void apply(DocumentChargeItem item, DocumentChargeItemRequest request, int lineNo) {
        if (request.chargeName() == null || request.chargeName().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "费用名称不能为空");
        }
        if (request.amount() == null || request.amount().signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "费用金额不能小于0");
        }
        item.setLineNo(lineNo);
        item.setChargeName(request.chargeName().trim());
        item.setMaterialId(request.materialId());
        item.setAmount(request.amount());
        item.setUnit(request.unit() == null || request.unit().isBlank() ? null : request.unit().trim());
        item.setRemark(request.remark() == null || request.remark().isBlank() ? null : request.remark().trim());
    }

    private void requireSameDocument(DocumentChargeItem item, String moduleKey, Long documentId) {
        if (!item.getModuleKey().equals(moduleKey) || !item.getDocumentId().equals(documentId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "费用行不属于当前单据");
        }
    }

    private void requireAllowedModule(String moduleKey) {
        if (moduleKey == null || !ALLOWED_MODULE_KEYS.contains(moduleKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前模块不支持附加费用");
        }
    }

    private DocumentChargeItemResponse toResponse(DocumentChargeItem item) {
        return new DocumentChargeItemResponse(
                item.getId(),
                item.getLineNo(),
                item.getChargeName(),
                item.getMaterialId(),
                item.getAmount(),
                item.getUnit(),
                item.getRemark()
        );
    }
}
