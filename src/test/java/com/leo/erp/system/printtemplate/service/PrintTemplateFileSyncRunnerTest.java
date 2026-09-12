package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文件托管模板同步器：manifest 是 isDefault 元数据的单一事实源，trim 哈希与文件内容一致时不重复落库。
 */
class PrintTemplateFileSyncRunnerTest {

    private static final String SOURCE_REF = "print-forms/default-sales-order.layout.json";

    @Mock
    private PrintTemplateRepository repository;

    @Mock
    private PrintPdfFormTemplateValidator pdfFormTemplateValidator;

    @Mock
    private PrintTemplateManifest manifest;

    @Mock
    private CompanySettingRepository companySettingRepository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private PrintTemplateApplyService templateApplyService;

    private PrintTemplateFileSyncRunner runner;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runner = new PrintTemplateFileSyncRunner(
                repository,
                pdfFormTemplateValidator,
                manifest,
                companySettingRepository,
                idGenerator,
                templateApplyService
        );
    }

    @Test
    void run_shouldApplyIsDefaultFromManifestToExistingTemplate() {
        PrintTemplate existing = fileTemplate(false);
        when(repository.findAllBySyncModeAndDeletedFlagFalse("FILE")).thenReturn(List.of(existing));
        when(manifest.getTemplates()).thenReturn(List.of(item(true)));
        when(manifest.getSourceRefs()).thenReturn(Set.of(SOURCE_REF));

        runner.run(null);

        assertThat(existing.getIsDefault()).isTrue();
        verify(repository).save(existing);
    }

    @Test
    void run_shouldNotTouchSyncedTemplateWithMatchingMetadata() throws Exception {
        PrintTemplate existing = fileTemplate(false);
        String content = classpathContent(SOURCE_REF);
        existing.setTemplateHtml(content);
        existing.setSourceChecksum(PrintTemplateChecksum.sha256(content));
        when(repository.findAllBySyncModeAndDeletedFlagFalse("FILE")).thenReturn(List.of(existing));
        when(manifest.getTemplates()).thenReturn(List.of(item(false)));
        when(manifest.getSourceRefs()).thenReturn(Set.of(SOURCE_REF));

        runner.run(null);

        assertThat(existing.getIsDefault()).isFalse();
        verify(repository, never()).save(existing);
    }

    private PrintTemplateManifest.Item item(boolean isDefault) {
        return new PrintTemplateManifest.Item(
                SOURCE_REF,
                "sales-order",
                "默认销售订单 PDF",
                "DEFAULT_SALES_ORDER_PDF_FORM",
                isDefault,
                null
        );
    }

    private PrintTemplate fileTemplate(boolean isDefault) {
        PrintTemplate template = new PrintTemplate();
        template.setId(1L);
        template.setBillType("sales-order");
        template.setTemplateName("默认销售订单 PDF");
        template.setTemplateCode("DEFAULT_SALES_ORDER_PDF_FORM");
        template.setTemplateHtml("");
        template.setTemplateType("PDF_FORM");
        template.setEngine("PDF_FORM");
        template.setSyncMode("FILE");
        template.setSourceRef(SOURCE_REF);
        template.setVersionNo(1);
        template.setIsDefault(isDefault);
        return template;
    }

    private String classpathContent(String path) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }
}
