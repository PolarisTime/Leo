package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrintTemplateFileSyncRunnerTest {

    @Test
    void shouldSyncFileManagedTemplateFromClasspath() {
        PrintTemplate template = template("old", null);
        PrintTemplateRepository repository = mock(PrintTemplateRepository.class);
        List<PrintTemplate> templates = new ArrayList<>(List.of(template));
        when(repository.findAllBySyncModeAndDeletedFlagFalse(PrintTemplateFileSyncRunner.SYNC_MODE_FILE))
                .thenReturn(templates);
        when(repository.save(any(PrintTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        runner(repository).run(new DefaultApplicationArguments());

        assertThat(template.getTemplateHtml()).contains("\"page\"");
        assertThat(template.getTemplateHtml()).contains("嘉兴颖捷建材有限公司");
        assertThat(template.getSourceChecksum()).hasSize(64);
        assertThat(template.getVersionNo()).isEqualTo(2);
        verify(repository).save(template);
    }

    @Test
    void shouldSkipWhenChecksumAndContentAreCurrent() {
        PrintTemplate template = template(currentContent(), null);
        runner(repositoryWithSingle(template)).run(new DefaultApplicationArguments());
        String checksum = template.getSourceChecksum();
        int versionNo = template.getVersionNo();

        PrintTemplateRepository repository = repositoryWithSingle(template);
        runner(repository).run(new DefaultApplicationArguments());

        assertThat(template.getSourceChecksum()).isEqualTo(checksum);
        assertThat(template.getVersionNo()).isEqualTo(versionNo);
        verify(repository, never()).save(any(PrintTemplate.class));
    }

    @Test
    void shouldRejectSourceRefOutsidePrintFormsDirectory() {
        PrintTemplate template = template("old", null);
        template.setSourceRef("application.yml");
        PrintTemplateRepository repository = repositoryWithSingle(template);

        assertThatThrownBy(() -> runner(repository).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("源文件路径不合法");
        verify(repository, never()).save(any(PrintTemplate.class));
    }

    @Test
    void shouldRejectInvalidPdfFormLayoutFromFile() {
        PrintTemplate template = template("old", null);
        template.setSourceRef("print-forms/invalid.layout.json");
        PrintTemplateRepository repository = repositoryWithSingle(template);

        assertThatThrownBy(() -> runner(repository).run(new DefaultApplicationArguments()))
                .isInstanceOf(com.leo.erp.common.error.BusinessException.class)
                .hasMessageContaining("必须配置通用字段或明细布局");
        verify(repository, never()).save(any(PrintTemplate.class));
    }

    private PrintTemplateRepository repositoryWithSingle(PrintTemplate template) {
        PrintTemplateRepository repository = mock(PrintTemplateRepository.class);
        when(repository.findAllBySyncModeAndDeletedFlagFalse(PrintTemplateFileSyncRunner.SYNC_MODE_FILE))
                .thenReturn(List.of(template));
        when(repository.save(any(PrintTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return repository;
    }

    private PrintTemplate template(String templateHtml, String checksum) {
        PrintTemplate template = new PrintTemplate();
        template.setId(1L);
        template.setBillType("sales-order");
        template.setTemplateCode("SALES_ORDER_YINGJIE_A4_REMARK_PDF");
        template.setTemplateName("颖捷A4打印_带备注 PDF");
        template.setTemplateType("PDF_FORM");
        template.setEngine("PDF_FORM");
        template.setTemplateHtml(templateHtml);
        template.setVersionNo(1);
        template.setStatus("ACTIVE");
        template.setSyncMode(PrintTemplateFileSyncRunner.SYNC_MODE_FILE);
        template.setSourceRef("print-forms/yingjie-a4-remark.layout.json");
        template.setSourceChecksum(checksum);
        return template;
    }

    private String currentContent() {
        PrintTemplate template = template("old", null);
        PrintTemplateRepository repository = repositoryWithSingle(template);
        runner(repository).run(new DefaultApplicationArguments());
        return template.getTemplateHtml();
    }

    private PrintTemplateFileSyncRunner runner(PrintTemplateRepository repository) {
        return new PrintTemplateFileSyncRunner(
                repository,
                new PrintPdfFormTemplateValidator(new ObjectMapper())
        );
    }
}
