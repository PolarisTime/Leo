package com.leo.erp.statement.freight.service;

import com.leo.erp.attachment.api.AttachmentView;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.BillSnapshot;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class FreightStatementPageAssembler {

    private final FreightStatementViewAssembler viewAssembler;

    public FreightStatementPageAssembler(FreightStatementViewAssembler viewAssembler) {
        this.viewAssembler = viewAssembler;
    }

    /**
     * 列表组装：一次性预取整页明细引用的**来源物流单快照**。
     *
     * <p>列表行本身就带 items，前端打开详情/编辑时若行里已有 items 就不再请求详情
     * （见 {@code useDetailSupport} 的 shouldLoad 口径），因此列表组装必须补齐来源物流单的
     * 运费单价与运费总价，否则「单据明细」分组头里的运费单价 / 运费总价恒显示 0.00。</p>
     */
    Page<FreightStatementView> toViewPage(Page<FreightStatement> entityPage) {
        List<FreightStatement> entities = entityPage.getContent();
        Map<Long, List<AttachmentView>> attachmentsByStatementId =
                viewAssembler.resolveAttachmentsByStatement(entities);
        Map<Long, BillSnapshot> sourceBillsById = viewAssembler.resolveSourceBills(
                entities.stream()
                        .flatMap(entity -> entity.getItems().stream())
                        .map(FreightStatementItem::getSourceFreightBillId)
                        .filter(Objects::nonNull)
                        .toList()
        );
        List<FreightStatementView> responses = entities.stream()
                .map(entity -> viewAssembler.toView(
                        entity,
                        attachmentsByStatementId.getOrDefault(entity.getId(), List.of()),
                        sourceBillsById
                ))
                .toList();
        return new PageImpl<>(responses, entityPage.getPageable(), entityPage.getTotalElements());
    }
}
