package com.leo.erp.statement.freight.service;

import com.leo.erp.attachment.service.AttachmentBindingService;
import com.leo.erp.attachment.service.AttachmentView;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FreightStatementViewAssembler {

    private static final String MODULE_KEY = "freight-statement";

    private final AttachmentBindingService attachmentBindingService;

    public FreightStatementViewAssembler(AttachmentBindingService attachmentBindingService) {
        this.attachmentBindingService = attachmentBindingService;
    }

    FreightStatementView toDetailView(FreightStatement entity) {
        return toView(entity, resolveAttachments(entity));
    }

    FreightStatementView toView(FreightStatement entity, List<AttachmentView> attachments) {
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
                entity.getSignStatus(),
                joinAttachmentNames(attachments),
                attachments,
                entity.getRemark(),
                entity.getItems().stream().map(this::toItemView).toList(),
                entity.getCarrierId()
        );
    }

    FreightStatementItemView toItemView(FreightStatementItem item) {
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
                item.getBatchNoNormalized()
        );
    }

    Map<Long, List<AttachmentView>> resolveAttachmentsByStatement(List<FreightStatement> statements) {
        if (statements.isEmpty()) {
            return Map.of();
        }
        List<Long> statementIds = statements.stream().map(FreightStatement::getId).toList();
        Map<Long, List<AttachmentView>> boundAttachments =
                attachmentBindingService.listByRecordIds(MODULE_KEY, statementIds);
        Map<Long, List<AttachmentView>> result = new LinkedHashMap<>(boundAttachments);
        for (FreightStatement statement : statements) {
            result.putIfAbsent(statement.getId(), List.of());
        }
        return result;
    }

    private List<AttachmentView> resolveAttachments(FreightStatement entity) {
        return attachmentBindingService.list(MODULE_KEY, entity.getId());
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
