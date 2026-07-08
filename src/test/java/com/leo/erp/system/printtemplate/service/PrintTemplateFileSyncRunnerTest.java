package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrintTemplateFileSyncRunnerTest {

    @Test
    void shouldReturnWhenNoFileManagedTemplatesExist() {
        PrintTemplateRepository repository = mock(PrintTemplateRepository.class);
        when(repository.findAllBySyncModeAndDeletedFlagFalse(PrintTemplateFileSyncRunner.SYNC_MODE_FILE))
                .thenReturn(List.of());

        runner(repository).run(new DefaultApplicationArguments());

        verify(repository, never()).save(any(PrintTemplate.class));
    }

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
        assertThat(template.getTemplateHtml()).contains("${settlementCompanyName}（供货单）");
        assertThat(template.getSourceChecksum()).hasSize(64);
        assertThat(template.getVersionNo()).isEqualTo(2);
        verify(repository).save(template);
    }

    @Test
    void shouldSyncFileManagedCoordTemplateFromClasspath() {
        PrintTemplate template = template("old", null);
        template.setTemplateType("COORD");
        template.setEngine("LODOP");
        template.setSourceRef("print-forms/sample-coord.lodop.txt");
        PrintTemplateRepository repository = repositoryWithSingle(template);

        runner(repository).run(new DefaultApplicationArguments());

        assertThat(template.getTemplateHtml()).isEqualTo("LODOP.PRINT_INIT(\"Sample\");");
        assertThat(template.getSourceChecksum()).hasSize(64);
        assertThat(template.getVersionNo()).isEqualTo(2);
        verify(repository).save(template);
    }

    @Test
    void shouldSyncSeededFileManagedTemplatesFromClasspath() {
        List<PrintTemplate> templates = List.of(
                seedTemplate(
                        322775358715723776L,
                        "freight-bill",
                        "TPL_322775358715723776",
                        "物流配送单",
                        "PDF_FORM",
                        "PDF_FORM",
                        "print-forms/freight-bill-delivery.layout.json"
                ),
                seedTemplate(
                        700540000000000024L,
                        "freight-bill",
                        "TPL_700540000000000024",
                        "物流单A版",
                        "COORD",
                        "LODOP",
                        "print-forms/freight-bill-a.lodop.txt"
                ),
                seedTemplate(
                        700540000000000026L,
                        "freight-statement",
                        "TPL_700540000000000026",
                        "物流对账单-汇总",
                        "COORD",
                        "LODOP",
                        "print-forms/freight-statement-summary.lodop.txt"
                ),
                seedTemplate(
                        700540000000000028L,
                        "customer-statement",
                        "TPL_700540000000000028",
                        "客户对账单-A4",
                        "COORD",
                        "LODOP",
                        "print-forms/customer-statement-a4.lodop.txt"
                ),
                seedTemplate(
                        700540000000000029L,
                        "sales-order",
                        "SALES_ORDER_YINGJIE_A4_REMARK_PDF",
                        "颖捷A4打印_带备注 PDF",
                        "PDF_FORM",
                        "PDF_FORM",
                        "print-forms/yingjie-a4-remark.layout.json"
                ),
                seedTemplate(
                        700540000000000030L,
                        "freight-bill",
                        "DEFAULT_LOGISTICS_PDF_FORM",
                        "默认物流 PDF",
                        "PDF_FORM",
                        "PDF_FORM",
                        "print-forms/default-logistics.layout.json"
                ),
                seedTemplate(
                        700540000000000031L,
                        "purchase-order",
                        "DEFAULT_PURCHASE_PDF_FORM",
                        "默认采购 PDF",
                        "PDF_FORM",
                        "PDF_FORM",
                        "print-forms/default-purchase.layout.json"
                ),
                seedTemplate(
                        700540000000000032L,
                        "inventory-report",
                        "DEFAULT_REPORT_PDF_FORM",
                        "默认报表 PDF",
                        "PDF_FORM",
                        "PDF_FORM",
                        "print-forms/default-report.layout.json"
                ),
                seedTemplate(
                        700540000000000033L,
                        "sales-order",
                        "DEFAULT_SALES_PDF_FORM",
                        "默认销售 PDF",
                        "PDF_FORM",
                        "PDF_FORM",
                        "print-forms/default-sales.layout.json"
                ),
                seedTemplate(
                        700540000000000034L,
                        "customer-statement",
                        "DEFAULT_STATEMENT_PDF_FORM",
                        "默认对账 PDF",
                        "PDF_FORM",
                        "PDF_FORM",
                        "print-forms/default-statement.layout.json"
                )
        );
        PrintTemplateRepository repository = mock(PrintTemplateRepository.class);
        when(repository.findAllBySyncModeAndDeletedFlagFalse(PrintTemplateFileSyncRunner.SYNC_MODE_FILE))
                .thenReturn(templates);
        when(repository.save(any(PrintTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        runner(repository).run(new DefaultApplicationArguments());

        assertThat(templates)
                .allSatisfy(seed -> {
                    assertThat(seed.getTemplateHtml()).doesNotContain("文件托管模板待同步");
                    assertThat(seed.getSourceChecksum()).hasSize(64);
                    assertThat(seed.getVersionNo()).isEqualTo(2);
                });
        assertThat(templates.get(0).getTemplateHtml()).contains("\"物流配送单\"");
        assertThat(templates.get(1).getTemplateHtml()).contains("LODOP.PRINT_INIT(\"物流单A版\")");
        assertThat(templates.get(2).getTemplateHtml()).contains("LODOP.PRINT_INIT(\"物流对账单\")");
        assertThat(templates.get(3).getTemplateHtml()).contains("LODOP.PRINT_INIT(\"客户对账单-A4\")");
        assertThat(templates.get(4).getTemplateHtml()).contains("\"page\"");
        assertThat(templates.get(5).getTemplateHtml()).contains("\"物流配送单\"");
        assertThat(templates.get(6).getTemplateHtml()).contains("\"采购单\"");
        assertThat(templates.get(7).getTemplateHtml()).contains("\"业务报表\"");
        assertThat(templates.get(8).getTemplateHtml()).contains("\"销售单\"");
        assertThat(templates.get(9).getTemplateHtml()).contains("\"对账单\"");
        verify(repository, org.mockito.Mockito.times(10)).save(any(PrintTemplate.class));
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
    void shouldSkipTemplateWithoutSourceRef() {
        PrintTemplate template = template("old", null);
        template.setSourceRef(" ");
        PrintTemplateRepository repository = repositoryWithSingle(template);

        runner(repository).run(new DefaultApplicationArguments());

        assertThat(template.getTemplateHtml()).isEqualTo("old");
        verify(repository, never()).save(any(PrintTemplate.class));
    }

    @Test
    void shouldSkipTemplateWithNullSourceRef() {
        PrintTemplate template = template("old", null);
        template.setSourceRef(null);
        PrintTemplateRepository repository = repositoryWithSingle(template);

        runner(repository).run(new DefaultApplicationArguments());

        assertThat(template.getTemplateHtml()).isEqualTo("old");
        verify(repository, never()).save(any(PrintTemplate.class));
    }

    @Test
    void shouldSyncWhenChecksumMatchesButContentDiffers() {
        String content = currentContent();
        PrintTemplate template = template("old", PrintTemplateChecksum.sha256(content));
        PrintTemplateRepository repository = repositoryWithSingle(template);

        runner(repository).run(new DefaultApplicationArguments());

        assertThat(template.getTemplateHtml()).isEqualTo(content);
        verify(repository).save(template);
    }

    @Test
    void shouldSyncWhenContentMatchesButChecksumDiffers() {
        String content = currentContent();
        PrintTemplate template = template(content, "wrong-checksum");
        PrintTemplateRepository repository = repositoryWithSingle(template);

        runner(repository).run(new DefaultApplicationArguments());

        assertThat(template.getSourceChecksum()).isEqualTo(PrintTemplateChecksum.sha256(content));
        verify(repository).save(template);
    }

    @Test
    void shouldTreatNullVersionAsInitialVersionWhenSyncing() {
        PrintTemplate template = template("old", null);
        template.setVersionNo(null);
        PrintTemplateRepository repository = repositoryWithSingle(template);

        runner(repository).run(new DefaultApplicationArguments());

        assertThat(template.getVersionNo()).isEqualTo(2);
        verify(repository).save(template);
    }

    @Test
    void shouldSkipPdfFormValidationForNonPdfFormTemplate() {
        PrintTemplate template = template("old", null);
        template.setTemplateType("COORD");
        template.setEngine("LODOP");
        template.setSourceRef("print-forms/sample-coord.lodop.txt");
        PrintTemplateRepository repository = repositoryWithSingle(template);
        PrintPdfFormTemplateValidator validator = mock(PrintPdfFormTemplateValidator.class);

        new PrintTemplateFileSyncRunner(repository, validator).run(new DefaultApplicationArguments());

        verify(validator, never()).validate(anyString());
        verify(repository).save(template);
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
    void shouldRejectUnsafeSourceRefPath() {
        PrintTemplate template = template("old", null);
        template.setSourceRef("../print-forms/yingjie-a4-remark.layout.json");
        PrintTemplateRepository repository = repositoryWithSingle(template);

        assertThatThrownBy(() -> runner(repository).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("源文件路径不合法");
        verify(repository, never()).save(any(PrintTemplate.class));
    }

    @Test
    void shouldRejectAbsoluteSourceRefPath() {
        PrintTemplate template = template("old", null);
        template.setSourceRef("/print-forms/yingjie-a4-remark.layout.json");
        PrintTemplateRepository repository = repositoryWithSingle(template);

        assertThatThrownBy(() -> runner(repository).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("源文件路径不合法");
        verify(repository, never()).save(any(PrintTemplate.class));
    }

    @Test
    void shouldRejectBackslashSourceRefPath() {
        PrintTemplate template = template("old", null);
        template.setSourceRef("print-forms\\yingjie-a4-remark.layout.json");
        PrintTemplateRepository repository = repositoryWithSingle(template);

        assertThatThrownBy(() -> runner(repository).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("源文件路径不合法");
        verify(repository, never()).save(any(PrintTemplate.class));
    }

    @Test
    void shouldRejectSourceRefWithInvalidSuffix() {
        PrintTemplate template = template("old", null);
        template.setSourceRef("print-forms/yingjie-a4-remark.json");
        PrintTemplateRepository repository = repositoryWithSingle(template);

        assertThatThrownBy(() -> runner(repository).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("源文件路径不合法");
        verify(repository, never()).save(any(PrintTemplate.class));
    }

    @Test
    void shouldRejectPdfFormSourceRefWithoutLayoutJsonSuffix() {
        PrintTemplate template = template("old", null);
        template.setSourceRef("print-forms/sample-coord.lodop.txt");
        PrintTemplateRepository repository = repositoryWithSingle(template);

        assertThatThrownBy(() -> runner(repository).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("源文件路径不合法");
        verify(repository, never()).save(any(PrintTemplate.class));
    }

    @Test
    void shouldRejectCoordSourceRefWithoutLodopSuffix() {
        PrintTemplate template = template("old", null);
        template.setTemplateType("COORD");
        template.setEngine("LODOP");
        template.setSourceRef("print-forms/yingjie-a4-remark.layout.json");
        PrintTemplateRepository repository = repositoryWithSingle(template);

        assertThatThrownBy(() -> runner(repository).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("源文件路径不合法");
        verify(repository, never()).save(any(PrintTemplate.class));
    }

    @Test
    void shouldRejectSourceRefWithInvalidPrefix() {
        PrintTemplate template = template("old", null);
        template.setSourceRef("forms/yingjie-a4-remark.layout.json");
        PrintTemplateRepository repository = repositoryWithSingle(template);

        assertThatThrownBy(() -> runner(repository).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("源文件路径不合法");
        verify(repository, never()).save(any(PrintTemplate.class));
    }

    @Test
    void shouldRejectMissingClasspathTemplateFile() {
        PrintTemplate template = template("old", null);
        template.setSourceRef("print-forms/missing.layout.json");
        PrintTemplateRepository repository = repositoryWithSingle(template);

        assertThatThrownBy(() -> runner(repository).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("源文件不存在");
        verify(repository, never()).save(any(PrintTemplate.class));
    }

    @Test
    void shouldRejectDirectoryResourceContentThatIsNotValidJson() {
        PrintTemplate template = template("old", null);
        template.setSourceRef("print-forms/unreadable.layout.json");
        PrintTemplateRepository repository = repositoryWithSingle(template);

        assertThatThrownBy(() -> runner(repository).run(new DefaultApplicationArguments()))
                .isInstanceOf(com.leo.erp.common.error.BusinessException.class)
                .hasMessageContaining("不是合法 JSON");
        verify(repository, never()).save(any(PrintTemplate.class));
    }

    @Test
    void shouldWrapClasspathReadFailure() throws Exception {
        String sourceRef = "print-forms/read-failure.layout.json";
        Path path = new ClassPathResource(sourceRef).getFile().toPath();
        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(path);
        PrintTemplate template = template("old", null);
        template.setSourceRef(sourceRef);
        PrintTemplateRepository repository = repositoryWithSingle(template);

        try {
            Files.setPosixFilePermissions(path, Set.of());

            assertThatThrownBy(() -> runner(repository).run(new DefaultApplicationArguments()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("读取打印模板源文件失败");
            verify(repository, never()).save(any(PrintTemplate.class));
        } finally {
            Files.setPosixFilePermissions(path, originalPermissions);
        }
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

    private PrintTemplate seedTemplate(long id,
                                       String billType,
                                       String templateCode,
                                       String templateName,
                                       String templateType,
                                       String engine,
                                       String sourceRef) {
        PrintTemplate template = new PrintTemplate();
        template.setId(id);
        template.setBillType(billType);
        template.setTemplateCode(templateCode);
        template.setTemplateName(templateName);
        template.setTemplateType(templateType);
        template.setEngine(engine);
        template.setTemplateHtml("LODOP.PRINT_INIT(\"文件托管模板待同步\");");
        template.setVersionNo(1);
        template.setStatus("ACTIVE");
        template.setSyncMode(PrintTemplateFileSyncRunner.SYNC_MODE_FILE);
        template.setSourceRef(sourceRef);
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
