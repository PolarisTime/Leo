package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class PrintTemplateFileSyncRunner implements ApplicationRunner {

    static final String SYNC_MODE_FILE = "FILE";
    private static final String TEMPLATE_TYPE_COORD = "COORD";
    private static final String TEMPLATE_TYPE_PDF_FORM = "PDF_FORM";
    private static final String TEMPLATE_ENGINE_LODOP = "LODOP";
    private static final String SOURCE_REF_PREFIX = "print-forms/";
    private static final String COORD_SOURCE_REF_SUFFIX = ".lodop.txt";
    private static final String PDF_FORM_SOURCE_REF_SUFFIX = ".layout.json";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    private final PrintTemplateRepository repository;
    private final PrintPdfFormTemplateValidator pdfFormTemplateValidator;
    private final PrintTemplateManifest manifest;
    private final CompanySettingRepository companySettingRepository;
    private final SnowflakeIdGenerator idGenerator;

    public PrintTemplateFileSyncRunner(PrintTemplateRepository repository,
                                       PrintPdfFormTemplateValidator pdfFormTemplateValidator,
                                       PrintTemplateManifest manifest,
                                       CompanySettingRepository companySettingRepository,
                                       SnowflakeIdGenerator idGenerator) {
        this.repository = repository;
        this.pdfFormTemplateValidator = pdfFormTemplateValidator;
        this.manifest = manifest;
        this.companySettingRepository = companySettingRepository;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<PrintTemplate> fileTemplates = repository.findAllBySyncModeAndDeletedFlagFalse(SYNC_MODE_FILE);
        Map<String, PrintTemplate> bySourceRef = new HashMap<>();
        for (PrintTemplate template : fileTemplates) {
            String sourceRef = template.getSourceRef();
            if (sourceRef != null && !sourceRef.isBlank()) {
                bySourceRef.put(sourceRef, template);
            }
        }

        int registeredCount = 0;
        int updatedCount = 0;
        int disabledCount = 0;

        // 1) manifest 驱动：登记缺失的模板文件并同步内容，实现"新增/修改文件跟随部署"
        for (PrintTemplateManifest.Item item : manifest.getTemplates()) {
            PrintTemplate template = bySourceRef.get(item.getSourceRef());
            if (template == null) {
                template = registerTemplate(item);
                bySourceRef.put(item.getSourceRef(), template);
                registeredCount++;
            }
            if (syncTemplate(template)) {
                updatedCount++;
            }
        }

        // 2) 兜底：manifest 之外的 FILE 记录。文件仍存在视为登记遗漏（告警保留），文件已删除则自动停用
        for (PrintTemplate template : fileTemplates) {
            String sourceRef = template.getSourceRef();
            if (sourceRef == null || sourceRef.isBlank() || manifest.getSourceRefs().contains(sourceRef)) {
                continue;
            }
            if (classpathExists(sourceRef)) {
                log.warn("File managed print template missing from manifest, keep as-is: code={}, sourceRef={}",
                        template.getTemplateCode(), sourceRef);
                if (syncTemplate(template)) {
                    updatedCount++;
                }
            } else if (disableTemplate(template)) {
                disabledCount++;
            }
        }

        if (registeredCount > 0 || updatedCount > 0 || disabledCount > 0) {
            log.info("Print template file sync completed: registered={}, updated={}, disabled={}",
                    registeredCount, updatedCount, disabledCount);
        }
    }

    /** 按清单登记一个缺失的模板文件（内容在后续 syncTemplate 中写入）。 */
    private PrintTemplate registerTemplate(PrintTemplateManifest.Item item) {
        String templateType = templateTypeOf(item.getSourceRef());
        PrintTemplate template = new PrintTemplate();
        template.setId(idGenerator.nextId());
        template.setBillType(item.getBillType());
        template.setTemplateName(item.getTemplateName());
        template.setTemplateCode(item.getTemplateCode());
        template.setTemplateHtml("");
        template.setTemplateType(templateType);
        template.setEngine(TEMPLATE_TYPE_PDF_FORM.equals(templateType)
                ? TEMPLATE_TYPE_PDF_FORM
                : TEMPLATE_ENGINE_LODOP);
        template.setVersionNo(1);
        template.setStatus(STATUS_ACTIVE);
        template.setSyncMode(SYNC_MODE_FILE);
        template.setSourceRef(normalizeSourceRef(template, item.getSourceRef()));
        resolveSettlementCompany(item, template);
        repository.save(template);
        log.info("Registered print template from manifest: code={}, sourceRef={}",
                item.getTemplateCode(), item.getSourceRef());
        return template;
    }

    /** 按清单结算主体名称解析公司；解析失败则作为通用模板登记。 */
    private void resolveSettlementCompany(PrintTemplateManifest.Item item, PrintTemplate template) {
        String companyName = item.getSettlementCompanyName();
        if (companyName == null || companyName.isBlank()) {
            template.setSettlementCompanyId(null);
            template.setSettlementCompanyName(null);
            return;
        }
        Optional<CompanySetting> company =
                companySettingRepository.findFirstByCompanyNameAndDeletedFlagFalse(companyName);
        if (company.isPresent()) {
            template.setSettlementCompanyId(company.get().getId());
            template.setSettlementCompanyName(companyName);
        } else {
            log.warn("Print template manifest settlement company not found, register as generic: "
                            + "companyName={}, sourceRef={}",
                    companyName, item.getSourceRef());
            template.setSettlementCompanyId(null);
            template.setSettlementCompanyName(null);
        }
    }

    /** 源文件从仓库移除时停用对应模板（不物理删除）。 */
    private boolean disableTemplate(PrintTemplate template) {
        if (STATUS_DISABLED.equals(template.getStatus())) {
            return false;
        }
        template.setStatus(STATUS_DISABLED);
        repository.save(template);
        log.info("Disabled print template whose file was removed: code={}, sourceRef={}",
                template.getTemplateCode(), template.getSourceRef());
        return true;
    }

    private boolean classpathExists(String sourceRef) {
        return new ClassPathResource(sourceRef).exists();
    }

    private String templateTypeOf(String sourceRef) {
        if (sourceRef.endsWith(PDF_FORM_SOURCE_REF_SUFFIX)) {
            return TEMPLATE_TYPE_PDF_FORM;
        }
        if (sourceRef.endsWith(COORD_SOURCE_REF_SUFFIX)) {
            return TEMPLATE_TYPE_COORD;
        }
        throw new IllegalStateException("打印模板源文件类型不合法: " + sourceRef);
    }

    private boolean syncTemplate(PrintTemplate template) {
        String sourceRef = template.getSourceRef();
        if (sourceRef == null || sourceRef.isBlank()) {
            log.warn("Skip file managed print template without sourceRef: code={}", template.getTemplateCode());
            return false;
        }

        String normalizedSourceRef = normalizeSourceRef(template, sourceRef);
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

    private String normalizeSourceRef(PrintTemplate template, String sourceRef) {
        String normalized = sourceRef.trim();
        if (normalized.startsWith("/") || normalized.contains("..") || normalized.contains("\\")) {
            throw new IllegalStateException("打印模板源文件路径不合法: " + sourceRef);
        }
        if (!normalized.startsWith(SOURCE_REF_PREFIX) || !normalized.endsWith(requiredSourceRefSuffix(template))) {
            throw new IllegalStateException("打印模板源文件路径不合法: " + sourceRef);
        }
        return normalized;
    }

    private String requiredSourceRefSuffix(PrintTemplate template) {
        if (template != null && TEMPLATE_TYPE_PDF_FORM.equals(template.getTemplateType())) {
            return PDF_FORM_SOURCE_REF_SUFFIX;
        }
        if (template != null && TEMPLATE_TYPE_COORD.equals(template.getTemplateType())) {
            return COORD_SOURCE_REF_SUFFIX;
        }
        throw new IllegalStateException("打印模板类型不支持文件托管: "
                + (template == null ? "<new>" : template.getTemplateType()));
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
