package com.leo.erp.statement.freight.service;

import com.leo.erp.attachment.api.AttachmentQuery;
import com.leo.erp.attachment.api.AttachmentView;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.BillSnapshot;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FreightStatementViewAssembler {

    private static final String MODULE_KEY = "freight-statement";

    private final AttachmentQuery attachmentQuery;
    private final FreightBillStatementSourceQuery sourceQuery;

    public FreightStatementViewAssembler(AttachmentQuery attachmentQuery,
                                         FreightBillStatementSourceQuery sourceQuery) {
        this.attachmentQuery = attachmentQuery;
        this.sourceQuery = sourceQuery;
    }

    FreightStatementView toDetailView(FreightStatement entity) {
        return toView(entity, resolveAttachments(entity), resolveSourceBills(entity));
    }

    FreightStatementView toView(FreightStatement entity, List<AttachmentView> attachments) {
        return toView(entity, attachments, Map.of());
    }

    private FreightStatementView toView(FreightStatement entity,
                                        List<AttachmentView> attachments,
                                        Map<Long, BillSnapshot> sourceBillsById) {
        return new FreightStatementView(
                entity.getId(),
                entity.getStatementNo(),
                entity.getCarrierCode(),
                entity.getCarrierName(),
                entity.getSettlementCompanyId(),
                entity.getSettlementCompanyName(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getTotalWeight(),
                entity.getTotalFreight(),
                entity.getPaidAmount(),
                entity.getUnpaidAmount(),
                entity.getStatus(),
                entity.isDeletedFlag(),
                joinAttachmentNames(attachments),
                attachments,
                entity.getRemark(),
                entity.getItems().stream()
                        .map(item -> toItemView(item, sourceBillsById.get(item.getSourceFreightBillId())))
                        .toList(),
                entity.getCarrierId()
        );
    }

    private FreightStatementItemView toItemView(FreightStatementItem item, BillSnapshot sourceBill) {
        return new FreightStatementItemView(
                item.getId(),
                item.getLineNo(),
                item.getSourceNo(),
                item.getSettlementCompanyId(),
                item.getSettlementCompanyName(),
                item.getCustomerName(),
                item.getProjectName(),
                item.getMaterialCode(),
                item.getMaterialName(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getQuantity(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getBatchNo(),
                item.getWeightTon(),
                item.getWarehouseName(),
                item.getSourceFreightBillId(),
                item.getSourceFreightBillItemId(),
                item.getCustomerId(),
                item.getProjectId(),
                item.getMaterialId(),
                item.getWarehouseId(),
                item.getBatchNoNormalized(),
                sourceBill == null ? null : sourceBill.unitPrice(),
                sourceBill == null ? null : sourceBill.totalFreight()
        );
    }

    private Map<Long, BillSnapshot> resolveSourceBills(FreightStatement entity) {
        LinkedHashSet<Long> sourceBillIds = entity.getItems().stream()
                .map(FreightStatementItem::getSourceFreightBillId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (sourceBillIds.isEmpty()) {
            return Map.of();
        }
        return sourceQuery.findByBillIds(sourceBillIds).stream()
                .collect(Collectors.toMap(
                        BillSnapshot::id,
                        snapshot -> snapshot,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
    }

    Map<Long, List<AttachmentView>> resolveAttachmentsByStatement(List<FreightStatement> statements) {
        if (statements.isEmpty()) {
            return Map.of();
        }
        List<Long> statementIds = statements.stream().map(FreightStatement::getId).toList();
        Map<Long, List<AttachmentView>> boundAttachments =
                attachmentQuery.listByRecordIds(MODULE_KEY, statementIds);
        Map<Long, List<AttachmentView>> result = new LinkedHashMap<>(boundAttachments);
        for (FreightStatement statement : statements) {
            result.putIfAbsent(statement.getId(), List.of());
        }
        return result;
    }

    private List<AttachmentView> resolveAttachments(FreightStatement entity) {
        return attachmentQuery.list(MODULE_KEY, entity.getId());
    }

    private String joinAttachmentNames(List<AttachmentView> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }
        return attachments.stream()
                .map(AttachmentView::name)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
    }
}
