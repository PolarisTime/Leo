package com.leo.erp.statement.freight.service;

import com.leo.erp.attachment.service.AttachmentView;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FreightStatementPageAssemblerTest {

    @Test
    void shouldResolveAttachmentsAndKeepPageMetadata() {
        FreightStatementViewAssembler viewAssembler = mock(FreightStatementViewAssembler.class);
        FreightStatementPageAssembler pageAssembler = new FreightStatementPageAssembler(viewAssembler);
        FreightStatement statement = statement(1L, "FS-001");
        AttachmentView attachment = attachment(11L, "回单.pdf");
        FreightStatementView view = view(statement, List.of(attachment));

        when(viewAssembler.resolveAttachmentsByStatement(List.of(statement)))
                .thenReturn(Map.of(1L, List.of(attachment)));
        when(viewAssembler.toView(statement, List.of(attachment))).thenReturn(view);

        var result = pageAssembler.toViewPage(new PageImpl<>(
                List.of(statement),
                PageRequest.of(2, 20),
                41
        ));

        assertThat(result.getContent()).containsExactly(view);
        assertThat(result.getNumber()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotalElements()).isEqualTo(41);
    }

    private FreightStatement statement(Long id, String statementNo) {
        FreightStatement statement = new FreightStatement();
        statement.setId(id);
        statement.setStatementNo(statementNo);
        return statement;
    }

    private FreightStatementView view(FreightStatement statement, List<AttachmentView> attachments) {
        return new FreightStatementView(
                statement.getId(),
                statement.getStatementNo(),
                "物流甲",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "待审核",
                "未签署",
                "回单.pdf",
                attachments,
                null,
                List.of()
        );
    }

    private AttachmentView attachment(Long id, String name) {
        return new AttachmentView(
                id,
                name,
                name,
                name,
                "application/pdf",
                1024L,
                "LOCAL",
                "tester",
                null,
                true,
                "pdf",
                "/preview/" + id,
                "/download/" + id
        );
    }
}
