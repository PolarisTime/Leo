package com.leo.erp.common.charge.service;

import com.leo.erp.common.charge.domain.entity.DocumentChargeItem;
import com.leo.erp.common.charge.repository.DocumentChargeItemRepository;
import com.leo.erp.common.charge.web.dto.DocumentChargeItemRequest;
import com.leo.erp.common.charge.web.dto.DocumentChargeItemResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DocumentChargeItemService {

    private static final Set<String> CHARGE_DIRECTIONS = Set.of("RECEIVABLE", "PAYABLE", "INTERNAL");
    private static final Set<String> SETTLEMENT_PARTY_TYPES = Set.of("CUSTOMER", "SUPPLIER", "CARRIER", "COMPANY");

    private final DocumentChargeItemRepository repository;
    private final SnowflakeIdGenerator idGenerator;

    public DocumentChargeItemService(DocumentChargeItemRepository repository,
                                     SnowflakeIdGenerator idGenerator) {
        this.repository = repository;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public List<DocumentChargeItemResponse> sync(String moduleKey,
                                                 Long documentId,
                                                 List<DocumentChargeItemRequest> requests) {
        String normalizedModuleKey = DocumentChargeModuleRegistry.requireSupported(moduleKey);
        Long normalizedDocumentId = requireDocumentId(documentId);
        List<DocumentChargeItem> existingItems = activeItems(normalizedModuleKey, normalizedDocumentId);
        Map<Long, DocumentChargeItem> existingById = existingItems.stream()
                .collect(Collectors.toMap(DocumentChargeItem::getId, Function.identity()));
        Set<Long> retainedIds = new HashSet<>();
        List<DocumentChargeItem> activeItems = new ArrayList<>();

        List<DocumentChargeItemRequest> safeRequests = requests == null ? List.of() : requests;
        for (int index = 0; index < safeRequests.size(); index++) {
            DocumentChargeItem item = resolveItem(
                    normalizedModuleKey,
                    normalizedDocumentId,
                    safeRequests.get(index),
                    existingById
            );
            applyItem(item, safeRequests.get(index), index + 1);
            retainedIds.add(item.getId());
            activeItems.add(item);
        }

        List<DocumentChargeItem> changedItems = new ArrayList<>(activeItems);
        for (DocumentChargeItem existing : existingItems) {
            if (!retainedIds.contains(existing.getId())) {
                existing.setDeletedFlag(true);
                changedItems.add(existing);
            }
        }
        if (!changedItems.isEmpty()) {
            repository.saveAll(changedItems);
        }
        return activeItems.stream()
                .sorted(Comparator.comparing(DocumentChargeItem::getLineNo))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentChargeItemResponse> listResponses(String moduleKey, Long documentId) {
        String normalizedModuleKey = DocumentChargeModuleRegistry.requireSupported(moduleKey);
        return activeItems(normalizedModuleKey, requireDocumentId(documentId)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal totalChargeAmount(String moduleKey, Long documentId) {
        String normalizedModuleKey = DocumentChargeModuleRegistry.requireSupported(moduleKey);
        return totalChargeAmount(normalizedModuleKey, activeItems(normalizedModuleKey, requireDocumentId(documentId)));
    }

    @Transactional
    public List<DocumentChargeItemResponse> copyFromSource(String sourceModuleKey,
                                                           Long sourceDocumentId,
                                                           String targetModuleKey,
                                                           Long targetDocumentId) {
        String normalizedSourceModuleKey = DocumentChargeModuleRegistry.requireSupported(sourceModuleKey);
        Long normalizedSourceDocumentId = requireDocumentId(sourceDocumentId);
        String normalizedTargetModuleKey = DocumentChargeModuleRegistry.requireSupported(targetModuleKey);
        Long normalizedTargetDocumentId = requireDocumentId(targetDocumentId);
        List<DocumentChargeItem> sourceItems = activeItems(normalizedSourceModuleKey, normalizedSourceDocumentId);
        List<DocumentChargeItem> targetItems = activeItems(normalizedTargetModuleKey, normalizedTargetDocumentId);
        Set<String> existingSourceKeys = targetItems.stream()
                .map(this::sourceKey)
                .filter(key -> key != null)
                .collect(Collectors.toSet());
        List<DocumentChargeItem> createdItems = new ArrayList<>();

        for (DocumentChargeItem sourceItem : sourceItems) {
            String sourceKey = sourceKey(normalizedSourceModuleKey, normalizedSourceDocumentId, sourceItem.getId());
            if (existingSourceKeys.contains(sourceKey)) {
                continue;
            }
            DocumentChargeItem targetItem = copySourceItem(
                    sourceItem,
                    normalizedTargetModuleKey,
                    normalizedTargetDocumentId,
                    targetItems.size() + createdItems.size() + 1
            );
            createdItems.add(targetItem);
            existingSourceKeys.add(sourceKey);
        }

        if (!createdItems.isEmpty()) {
            repository.saveAll(createdItems);
            targetItems = new ArrayList<>(targetItems);
            targetItems.addAll(createdItems);
        }
        return targetItems.stream()
                .sorted(Comparator.comparing(DocumentChargeItem::getLineNo).thenComparing(DocumentChargeItem::getId))
                .map(this::toResponse)
                .toList();
    }

    public BigDecimal totalChargeAmount(String moduleKey, List<DocumentChargeItem> items) {
        String settlementDirection = DocumentChargeModuleRegistry.settlementDirection(moduleKey);
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return items.stream()
                .filter(item -> item != null && !item.isDeletedFlag())
                .filter(DocumentChargeItem::isBillable)
                .filter(item -> settlementDirection.equals(item.getChargeDirection()))
                .map(DocumentChargeItem::getAmount)
                .filter(amount -> amount != null)
                .map(this::scaleAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private List<DocumentChargeItem> activeItems(String moduleKey, Long documentId) {
        return repository.findByModuleKeyAndDocumentIdAndDeletedFlagFalseOrderByLineNoAscIdAsc(moduleKey, documentId);
    }

    private DocumentChargeItem resolveItem(String moduleKey,
                                           Long documentId,
                                           DocumentChargeItemRequest request,
                                           Map<Long, DocumentChargeItem> existingById) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "费用明细不能为空");
        }
        Long requestId = request.id();
        if (requestId == null) {
            DocumentChargeItem item = new DocumentChargeItem();
            item.setId(idGenerator.nextId());
            item.setModuleKey(moduleKey);
            item.setDocumentId(documentId);
            return item;
        }
        if (requestId <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "费用明细ID不合法");
        }
        DocumentChargeItem item = existingById.get(requestId);
        if (item == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "费用明细不存在或不属于当前单据");
        }
        return item;
    }

    private void applyItem(DocumentChargeItem item, DocumentChargeItemRequest request, int lineNo) {
        item.setLineNo(lineNo);
        item.setChargeName(requireChargeName(request.chargeName()));
        item.setChargeDirection(requireChargeDirection(request.chargeDirection()));
        item.setSettlementPartyType(normalizeSettlementPartyType(request.settlementPartyType()));
        item.setSettlementPartyId(request.settlementPartyId());
        item.setSettlementPartyName(trimToNull(request.settlementPartyName()));
        item.setAmount(requireAmount(request.amount()));
        item.setBillable(request.billable() == null || request.billable());
        item.setRemark(trimToNull(request.remark()));
        item.setDeletedFlag(false);
    }

    private DocumentChargeItem copySourceItem(DocumentChargeItem sourceItem,
                                              String targetModuleKey,
                                              Long targetDocumentId,
                                              int lineNo) {
        DocumentChargeItem targetItem = new DocumentChargeItem();
        targetItem.setId(idGenerator.nextId());
        targetItem.setModuleKey(targetModuleKey);
        targetItem.setDocumentId(targetDocumentId);
        targetItem.setLineNo(lineNo);
        targetItem.setChargeName(sourceItem.getChargeName());
        targetItem.setChargeDirection(sourceItem.getChargeDirection());
        targetItem.setSettlementPartyType(sourceItem.getSettlementPartyType());
        targetItem.setSettlementPartyId(sourceItem.getSettlementPartyId());
        targetItem.setSettlementPartyName(sourceItem.getSettlementPartyName());
        targetItem.setAmount(scaleAmount(sourceItem.getAmount()));
        targetItem.setBillable(sourceItem.isBillable());
        targetItem.setSourceModuleKey(sourceItem.getModuleKey());
        targetItem.setSourceDocumentId(sourceItem.getDocumentId());
        targetItem.setSourceChargeItemId(sourceItem.getId());
        targetItem.setRemark(sourceItem.getRemark());
        return targetItem;
    }

    private String sourceKey(DocumentChargeItem item) {
        if (item.getSourceModuleKey() == null || item.getSourceDocumentId() == null || item.getSourceChargeItemId() == null) {
            return null;
        }
        return sourceKey(item.getSourceModuleKey(), item.getSourceDocumentId(), item.getSourceChargeItemId());
    }

    private String sourceKey(String moduleKey, Long documentId, Long chargeItemId) {
        return moduleKey + ":" + documentId + ":" + chargeItemId;
    }

    private Long requireDocumentId(Long documentId) {
        if (documentId == null || documentId <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "单据ID不合法");
        }
        return documentId;
    }

    private String requireChargeName(String chargeName) {
        String normalized = trimToNull(chargeName);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "费用名称不能为空");
        }
        return normalized;
    }

    private String requireChargeDirection(String direction) {
        String normalized = normalizeEnum(direction);
        if (normalized == null || !CHARGE_DIRECTIONS.contains(normalized)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "费用方向不合法");
        }
        return normalized;
    }

    private String normalizeSettlementPartyType(String partyType) {
        String normalized = normalizeEnum(partyType);
        if (normalized == null) {
            return null;
        }
        if (!SETTLEMENT_PARTY_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "结算对象类型不合法");
        }
        return normalized;
    }

    private BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "费用金额不能为空");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "费用金额不能小于 0");
        }
        return scaleAmount(amount);
    }

    private BigDecimal scaleAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeEnum(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private DocumentChargeItemResponse toResponse(DocumentChargeItem item) {
        return new DocumentChargeItemResponse(
                item.getId(),
                item.getLineNo(),
                item.getChargeName(),
                item.getChargeDirection(),
                item.getSettlementPartyType(),
                item.getSettlementPartyId(),
                item.getSettlementPartyName(),
                item.getAmount(),
                item.isBillable(),
                item.getSourceModuleKey(),
                item.getSourceDocumentId(),
                item.getSourceChargeItemId(),
                item.getRemark()
        );
    }
}
