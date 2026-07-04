package com.leo.erp.statement.freight.service;

import com.leo.erp.attachment.service.AttachmentBindingService;
import com.leo.erp.attachment.service.AttachmentView;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class FreightStatementViewAssemblerTest {

    @Test
    void resolveAttachmentsByStatementShouldReturnEmptyMapWhenStatementsAreEmpty() {
        AttachmentBindingService attachmentBindingService = mock(AttachmentBindingService.class);
        FreightStatementViewAssembler assembler = new FreightStatementViewAssembler(attachmentBindingService);

        var result = assembler.resolveAttachmentsByStatement(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(attachmentBindingService);
    }

    @Test
    void toViewShouldUseEmptyAttachmentNameWhenAttachmentsAreNull() {
        FreightStatementViewAssembler assembler = new FreightStatementViewAssembler(mock(AttachmentBindingService.class));

        FreightStatementView result = assembler.toView(statement(), null);

        assertThat(result.attachment()).isEmpty();
        assertThat(result.attachments()).isNull();
    }

    @Test
    void toViewShouldJoinOnlyPresentAttachmentNames() {
        FreightStatementViewAssembler assembler = new FreightStatementViewAssembler(mock(AttachmentBindingService.class));
        List<AttachmentView> attachments = List.of(
                attachment(1L, "运单.pdf"),
                attachment(2L, null),
                attachment(3L, "   "),
                attachment(4L, "回单.pdf")
        );

        FreightStatementView result = assembler.toView(statement(), attachments);

        assertThat(result.attachment()).isEqualTo("运单.pdf, 回单.pdf");
        assertThat(result.attachments()).containsExactlyElementsOf(attachments);
    }

    private FreightStatement statement() {
        FreightStatement statement = new FreightStatement();
        statement.setId(1L);
        statement.setStatementNo("FS-001");
        return statement;
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
