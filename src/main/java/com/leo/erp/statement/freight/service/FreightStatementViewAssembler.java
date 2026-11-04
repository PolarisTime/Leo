package com.leo.erp.statement.freight.service;

import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.attachment.api.AttachmentQuery;
import com.leo.erp.attachment.api.AttachmentView;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.BillSnapshot;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FreightStatementViewAssembler {

    private static final String MODULE_KEY = ModuleKeys.FREIGHT_STATEMENT;

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

    /**
     * 组装视图（可带**批量预取**的来源物流单快照）。
     *
     * <p>列表页与详情页共用同一份 item 结构：列表行本身就带 items，前端打开详情/编辑时
     * 若行里已有 items 就不再请求详情（见 useDetailSupport 的 shouldLoad 口径），
     * 因此列表组装也必须带来源物流单快照，否则「运费单价 / 运费总价」在详情里恒为 0。</p>
     */
    FreightStatementView toView(FreightStatement entity,
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
                sourceBillNos(entity),
                entity.getItems().stream()
                        .map(item -> toItemView(item, sourceBill(sourceBillsById, item)))
                        .toList(),
                entity.getCarrierId()
        );
    }

    /**
     * 关联物流单号：按明细来源单号（{@code sourceNo}）去重并保持明细顺序。
     * 列表页「关联物流单」列与详情共用该唯一口径（前端不再自行推导）。
     */
    private List<String> sourceBillNos(FreightStatement entity) {
        return entity.getItems().stream()
                .map(FreightStatementItem::getSourceNo)
                .filter(Objects::nonNull)
                .filter(sourceNo -> !sourceNo.isBlank())
                .distinct()
                .toList();
    }

    /**
     * 明细来源物流单快照：{@code sourceFreightBillId} 可能为空（手工录入明细），
     * 且列表路径传入的是不可变空 Map（{@code Map.of().get(null)} 会抛 NPE），必须先判空。
     */
    private BillSnapshot sourceBill(Map<Long, BillSnapshot> sourceBillsById, FreightStatementItem item) {
        Long sourceFreightBillId = item.getSourceFreightBillId();
        if (sourceFreightBillId == null) {
            return null;
        }
        return sourceBillsById.get(sourceFreightBillId);
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
                sourceBill == null ? null : sourceBill.totalFreight(),
                // 优先用保存时落库的来源单日期快照；历史行（迁移前写入）与来源单被软删的场景
                // 用实时快照兜底，避免详情日期为空
                item.getSourceFreightBillTime() != null
                        ? item.getSourceFreightBillTime()
                        : (sourceBill == null ? null : sourceBill.billTime())
        );
    }

    private Map<Long, BillSnapshot> resolveSourceBills(FreightStatement entity) {
        return resolveSourceBills(entity.getItems().stream()
                .map(FreightStatementItem::getSourceFreightBillId)
                .toList());
    }

    /** 批量解析来源物流单快照：列表页对整页明细只查一次，避免逐行查询。 */
    Map<Long, BillSnapshot> resolveSourceBills(Collection<Long> billIds) {
        LinkedHashSet<Long> sourceBillIds = billIds == null
                ? new LinkedHashSet<>()
                : billIds.stream()
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
