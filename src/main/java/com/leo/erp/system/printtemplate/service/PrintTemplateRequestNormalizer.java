package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Set;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 打印模板请求规范化：字段清洗与合法性校验（单据类型、模板类型、引擎、
 * 底版路径、版本号、状态、LODOP 指令白名单与危险脚本黑名单），
 * 以及把规范化结果落到实体。
 */
@Service
public class PrintTemplateRequestNormalizer {

    public static final String TEMPLATE_TYPE_PDF_FORM = "PDF_FORM";

    private static final List<Pattern> DANGEROUS_LODOP_PATTERNS = List.of(
            Pattern.compile("\\b(eval|Function)\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(window|document|localStorage|sessionStorage|location|history|navigator)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(fetch|XMLHttpRequest|WebSocket)\\b", Pattern.CASE_INSENSITIVE)
    );

    /**
     * COORD 模板允许的 LODOP 绘制指令白名单：与前端 parseLodopScript 的
     * METHOD_ARGUMENTS 键集保持一致（前端为执行侧权威实现）。保存侧先于
     * 黑名单执行白名单校验，非白名单方法调用一律拒绝。
     */
    private static final Set<String> ALLOWED_LODOP_METHODS = Set.of(
            "PRINT_INIT", "PRINT_INITA", "SET_PRINT_PAGESIZE",
            "SET_PRINT_STYLE", "SET_PRINT_STYLEA",
            "ADD_PRINT_TEXT", "ADD_PRINT_LINE", "ADD_PRINT_BARCODE",
            "ADD_PRINT_RECT", "ADD_PRINT_ELLIPSE",
            "NEWPAGE", "NewPage", "PREVIEW", "PRINT"
    );

    /** 提取 LODOP.getObjectMethod(...) 调用中的方法名。 */
    private static final Pattern LODOP_METHOD_CALL = Pattern.compile(
            "\\bLODOP\\s*\\.\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Set<String> ALLOWED_TEMPLATE_TYPES = Set.of("COORD", "PDF_FORM");
    private static final Set<String> ALLOWED_ENGINES = Set.of("LODOP", "PDF_FORM");
    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "DISABLED");

    private final CompanySettingRepository companySettingRepository;
    private final ModuleCatalog moduleCatalog;
    private final PrintPdfFormTemplateValidator pdfFormTemplateValidator;
    private final PrintRuntimeProperties runtimeProperties;

    public PrintTemplateRequestNormalizer(CompanySettingRepository companySettingRepository,
                                          ModuleCatalog moduleCatalog,
                                          PrintPdfFormTemplateValidator pdfFormTemplateValidator,
                                          PrintRuntimeProperties runtimeProperties) {
        this.companySettingRepository = companySettingRepository;
        this.moduleCatalog = moduleCatalog;
        this.pdfFormTemplateValidator = pdfFormTemplateValidator;
        this.runtimeProperties = runtimeProperties;
    }

    public String normalizeBillType(String billType) {
        if (billType == null || billType.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "适用页面不能为空");
        }
        String normalized = billType.trim();
        if (!moduleCatalog.containsModule(normalized) || !runtimeProperties.printableModules().contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "适用页面不合法");
        }
        return normalized;
    }

    public String normalizeTemplateName(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "模板名称不能为空");
        }
        return templateName.trim();
    }

    public String normalizeTemplateCode(String templateCode) {
        if (templateCode == null || templateCode.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "模板编码不能为空");
        }
        String normalized = templateCode.trim().toUpperCase().replaceAll("[^A-Z0-9_\\-]", "_");
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板编码不合法");
        }
        return normalized;
    }

    public String normalizeTemplateType(String templateType) {
        if (templateType == null || templateType.isBlank()) {
            return "COORD";
        }
        String normalized = templateType.trim().toUpperCase();
        if (!ALLOWED_TEMPLATE_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板类型仅支持 COORD 或 PDF_FORM");
        }
        return normalized;
    }

    public SettlementCompanySnapshot normalizeSettlementCompany(Long settlementCompanyId) {
        if (settlementCompanyId == null) {
            return new SettlementCompanySnapshot(null, null);
        }
        var company = companySettingRepository.findByIdAndDeletedFlagFalse(settlementCompanyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "结算主体不存在"));
        return new SettlementCompanySnapshot(company.getId(), company.getCompanyName());
    }

    public record SettlementCompanySnapshot(Long id, String name) {
    }

    public void apply(PrintTemplate entity, PrintTemplateRequest request) {
        String templateType = normalizeTemplateType(request.templateType());
        String engine = normalizeEngine(request.engine(), templateType);
        String assetRef = normalizeAssetRef(request.assetRef(), templateType);
        String billType = normalizeBillType(request.billType());
        entity.setBillType(billType);
        entity.setTemplateName(normalizeTemplateName(request.templateName()));
        entity.setTemplateCode(normalizeTemplateCode(request.templateCode()));
        entity.setTemplateHtml(normalizeTemplateHtml(billType, request.templateHtml(), templateType, assetRef));
        entity.setTemplateType(templateType);
        entity.setEngine(engine);
        entity.setAssetRef(assetRef);
        entity.setSettlementCompanyId(request.settlementCompanyId());
        entity.setSettlementCompanyName(request.settlementCompanyName());
        entity.setVersionNo(normalizeVersionNo(request.versionNo()));
        entity.setStatus(normalizeStatus(request.status()));
    }

    private String normalizeTemplateHtml(String billType, String templateHtml, String templateType, String assetRef) {
        if (TEMPLATE_TYPE_PDF_FORM.equals(templateType)) {
            if (templateHtml != null && !templateHtml.isBlank()) {
                String normalized = templateHtml.trim();
                validateTemplateContent(normalized, templateType);
                return normalized;
            }
            return defaultPdfFormTemplate(billType);
        }
        if (templateHtml == null || templateHtml.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "模板内容不能为空");
        }
        String normalized = templateHtml.trim();
        validateTemplateContent(normalized, templateType);
        return normalized;
    }

    private void validateTemplateContent(String templateHtml, String templateType) {
        if (TEMPLATE_TYPE_PDF_FORM.equals(normalizeTemplateType(templateType))) {
            String normalized = templateHtml == null ? "" : templateHtml.trim();
            pdfFormTemplateValidator.validate(normalized);
            return;
        }
        validateLodopMethodWhitelist(templateHtml);
        for (Pattern pattern : DANGEROUS_LODOP_PATTERNS) {
            if (pattern.matcher(templateHtml).find()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板内容包含不允许的脚本或危险标签");
            }
        }
    }

    /** COORD 模板中所有 LODOP.* 调用的方法名必须命中白名单；无调用则放行（空模板/纯文本场景）。 */
    private void validateLodopMethodWhitelist(String templateHtml) {
        java.util.regex.Matcher matcher = LODOP_METHOD_CALL.matcher(templateHtml);
        while (matcher.find()) {
            String method = matcher.group(1);
            if (!ALLOWED_LODOP_METHODS.contains(method)) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "模板包含不允许的 LODOP 指令: " + method
                );
            }
        }
    }

    private String defaultPdfFormTemplate(String billType) {
        try {
            return new ClassPathResource(runtimeProperties.defaultPdfFormLayout(normalizeBillType(billType)))
                    .getContentAsString(StandardCharsets.UTF_8)
                    .trim();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取 PDF_FORM 默认布局失败");
        }
    }

    private String normalizeEngine(String engine, String templateType) {
        String normalized = engine == null || engine.isBlank() ? defaultEngine(templateType) : engine.trim().toUpperCase();
        if (!ALLOWED_ENGINES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "渲染引擎仅支持 LODOP 或 PDF_FORM");
        }
        if (TEMPLATE_TYPE_PDF_FORM.equals(templateType) && !TEMPLATE_TYPE_PDF_FORM.equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PDF_FORM 模板必须使用 PDF_FORM 引擎");
        }
        if ("COORD".equals(templateType) && !"LODOP".equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "COORD 模板必须使用 LODOP 引擎");
        }
        return normalized;
    }

    private String defaultEngine(String templateType) {
        return switch (templateType) {
            case "PDF_FORM" -> "PDF_FORM";
            default -> "LODOP";
        };
    }

    private String normalizeAssetRef(String assetRef, String templateType) {
        if (!TEMPLATE_TYPE_PDF_FORM.equals(templateType)) {
            return null;
        }
        if (assetRef == null || assetRef.isBlank()) {
            return null;
        }
        String normalized = assetRef.trim();
        if (normalized.contains("..") || normalized.startsWith("/") || !normalized.toLowerCase().endsWith(".pdf")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PDF 底版资源路径不合法");
        }
        return normalized;
    }

    private Integer normalizeVersionNo(Integer versionNo) {
        if (versionNo == null) {
            return 1;
        }
        if (versionNo < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板版本号必须大于 0");
        }
        return versionNo;
    }

    private String normalizeStatus(String status) {
        String normalized = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板状态仅支持 ACTIVE 或 DISABLED");
        }
        return normalized;
    }
}
