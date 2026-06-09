package com.leo.erp.system.printtemplate.service;

import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class PrintTemplateFileSyncRunner implements ApplicationRunner {

    static final String SYNC_MODE_FILE = "FILE";
    private static final String TEMPLATE_TYPE_PDF_FORM = "PDF_FORM";
    private static final String SOURCE_REF_PREFIX = "print-forms/";
    private static final String SOURCE_REF_SUFFIX = ".layout.json";

    private final PrintTemplateRepository repository;
    private final PrintPdfFormTemplateValidator pdfFormTemplateValidator;

    public PrintTemplateFileSyncRunner(PrintTemplateRepository repository,
                                       PrintPdfFormTemplateValidator pdfFormTemplateValidator) {
        this.repository = repository;
        this.pdfFormTemplateValidator = pdfFormTemplateValidator;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<PrintTemplate> templates = repository.findAllBySyncModeAndDeletedFlagFalse(SYNC_MODE_FILE);
        if (templates.isEmpty()) {
            return;
        }
        int updatedCount = 0;
        for (PrintTemplate template : templates) {
            if (syncTemplate(template)) {
                updatedCount++;
            }
        }
        if (updatedCount > 0) {
            log.info("Print template file sync completed: updated={}", updatedCount);
        }
    }

    private boolean syncTemplate(PrintTemplate template) {
        String sourceRef = template.getSourceRef();
        if (sourceRef == null || sourceRef.isBlank()) {
            log.warn("Skip file managed print template without sourceRef: code={}", template.getTemplateCode());
            return false;
        }

        String normalizedSourceRef = normalizeSourceRef(sourceRef);
        String content = readClasspathText(normalizedSourceRef);
        validateContent(template, content);
        String checksum = PrintTemplateChecksum.sha256(content);
        if (checksum.equals(template.getSourceChecksum()) && content.equals(template.getTemplateHtml())) {
            return false;
        }

        template.setTemplateHtml(content);
        template.setSourceRef(normalizedSourceRef);
        template.setSourceChecksum(checksum);
        template.setVersionNo(Math.max(template.getVersionNo() == null ? 1 : template.getVersionNo(), 1) + 1);
        repository.save(template);
        log.info("Synced print template from file: code={}, sourceRef={}", template.getTemplateCode(), normalizedSourceRef);
        return true;
    }

    private String normalizeSourceRef(String sourceRef) {
        String normalized = sourceRef.trim();
        if (normalized.startsWith("/") || normalized.contains("..") || normalized.contains("\\")) {
            throw new IllegalStateException("打印模板源文件路径不合法: " + sourceRef);
        }
        if (!normalized.startsWith(SOURCE_REF_PREFIX) || !normalized.endsWith(SOURCE_REF_SUFFIX)) {
            throw new IllegalStateException("打印模板源文件路径不合法: " + sourceRef);
        }
        return normalized;
    }

    private void validateContent(PrintTemplate template, String content) {
        if (TEMPLATE_TYPE_PDF_FORM.equals(template.getTemplateType())) {
            pdfFormTemplateValidator.validate(content);
        }
    }

    private String readClasspathText(String sourceRef) {
        ClassPathResource resource = new ClassPathResource(sourceRef);
        if (!resource.exists()) {
            throw new IllegalStateException("打印模板源文件不存在: " + sourceRef);
        }
        try {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new IllegalStateException("读取打印模板源文件失败: " + sourceRef, ex);
        }
    }

}
