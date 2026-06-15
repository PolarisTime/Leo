package com.leo.erp.statement.freight.service;

import com.leo.erp.attachment.service.AttachmentView;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class FreightStatementPageAssembler {

    private final FreightStatementViewAssembler viewAssembler;

    public FreightStatementPageAssembler(FreightStatementViewAssembler viewAssembler) {
        this.viewAssembler = viewAssembler;
    }

    Page<FreightStatementView> toViewPage(Page<FreightStatement> entityPage) {
        Map<Long, List<AttachmentView>> attachmentsByStatementId =
                viewAssembler.resolveAttachmentsByStatement(entityPage.getContent());
        List<FreightStatementView> responses = entityPage.getContent().stream()
                .map(entity -> viewAssembler.toView(entity, attachmentsByStatementId.getOrDefault(entity.getId(), List.of())))
                .toList();
        return new PageImpl<>(responses, entityPage.getPageable(), entityPage.getTotalElements());
    }
}
