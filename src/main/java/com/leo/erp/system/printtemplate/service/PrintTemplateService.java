package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import com.leo.erp.system.printtemplate.mapper.PrintTemplateMapper;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateRequest;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PrintTemplateService extends AbstractCrudService<PrintTemplate, PrintTemplateRequest, PrintTemplateResponse> {

    private static final Set<String> ALLOWED_BILL_TYPES = Set.of(
            "purchase-order", "purchase-inbound", "sales-order", "sales-outbound",
            "freight-bill", "purchase-contract", "sales-contract",
            "supplier-statement", "customer-statement", "freight-statement",
            "receipt", "payment", "invoice-receipt", "invoice-issue"
    );
    private static final List<Pattern> DANGEROUS_HTML_PATTERNS = List.of(
            Pattern.compile("<\\s*script\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\s*(iframe|object|embed|base|meta|link|form)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\son[a-z-]+\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(href|src|action)\\s*=\\s*['\"]?\\s*(javascript:|data:text/html)", Pattern.CASE_INSENSITIVE)
    );
    private static final List<Pattern> DANGEROUS_LODOP_PATTERNS = List.of(
            Pattern.compile("\\b(eval|Function)\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(window|document|localStorage|sessionStorage|location|history|navigator)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(fetch|XMLHttpRequest|WebSocket)\\b", Pattern.CASE_INSENSITIVE)
    );

    private final PrintTemplateRepository repository;
    private final PrintTemplateMapper printTemplateMapper;
    private final ModuleCatalog moduleCatalog;

    public PrintTemplateService(PrintTemplateRepository repository,
                                SnowflakeIdGenerator idGenerator,
                                PrintTemplateMapper printTemplateMapper,
                                ModuleCatalog moduleCatalog) {
        super(idGenerator);
        this.repository = repository;
        this.printTemplateMapper = printTemplateMapper;
        this.moduleCatalog = moduleCatalog;
    }

    @Transactional(readOnly = true)
    public List<PrintTemplateResponse> listByBillType(String billType) {
        return repository.findAllByBillTypeAndDeletedFlagFalseOrderByUpdatedAtDescIdDesc(normalizeBillType(billType))
                .stream()
                .map(printTemplateMapper::toResponse)
                .toList();
    }

    @Override
    protected void validateCreate(PrintTemplateRequest request) {
        String billType = normalizeBillType(request.billType());
        String templateName = normalizeTemplateName(request.templateName());
        if (repository.existsByBillTypeAndTemplateNameAndDeletedFlagFalse(billType, templateName)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一单据已存在同名打印模板");
        }
    }

    @Override
    protected void validateUpdate(PrintTemplate entity, PrintTemplateRequest request) {
        String billType = normalizeBillType(request.billType());
        String templateName = normalizeTemplateName(request.templateName());
        boolean duplicated = repository.existsByBillTypeAndTemplateNameAndDeletedFlagFalse(billType, templateName)
                && !(entity.getBillType().equals(billType) && entity.getTemplateName().equals(templateName));
        if (duplicated) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一单据已存在同名打印模板");
        }
    }

    @Override
    protected PrintTemplate newEntity() {
        return new PrintTemplate();
    }

    @Override
    protected void assignId(PrintTemplate entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<PrintTemplate> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "打印模板不存在";
    }

    @Override
    protected void apply(PrintTemplate entity, PrintTemplateRequest request) {
        entity.setBillType(normalizeBillType(request.billType()));
        entity.setTemplateName(normalizeTemplateName(request.templateName()));
        entity.setTemplateHtml(normalizeTemplateHtml(request.templateHtml(), request.templateType()));
        entity.setTemplateType(normalizeTemplateType(request.templateType()));
    }

    private String normalizeBillType(String billType) {
        if (billType == null || billType.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "适用页面不能为空");
        }
        String normalized = billType.trim();
        if (!moduleCatalog.containsModule(normalized) || !ALLOWED_BILL_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "适用页面不合法");
        }
        return normalized;
    }

    private String normalizeTemplateName(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "模板名称不能为空");
        }
        return templateName.trim();
    }

    private String normalizeTemplateHtml(String templateHtml, String templateType) {
        if (templateHtml == null || templateHtml.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "模板内容不能为空");
        }
        String normalized = templateHtml.trim();
        validateTemplateContent(normalized, templateType);
        return normalized;
    }

    private void validateTemplateContent(String templateHtml, String templateType) {
        if ("PDF_FORM".equals(normalizeTemplateType(templateType))) {
            return;
        }
        boolean isCoord = "COORD".equals(templateType);
        List<Pattern> patterns = isCoord ? DANGEROUS_LODOP_PATTERNS : DANGEROUS_HTML_PATTERNS;
        for (Pattern pattern : patterns) {
            if (pattern.matcher(templateHtml).find()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板内容包含不允许的脚本或危险标签");
            }
        }
    }

    private String normalizeTemplateType(String templateType) {
        if (templateType == null || templateType.isBlank()) {
            return "HTML";
        }
        String normalized = templateType.trim().toUpperCase();
        if (!Set.of("HTML", "COORD", "PDF_FORM").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板类型仅支持 HTML、COORD 或 PDF_FORM");
        }
        return normalized;
    }

    @Override
    protected PrintTemplate saveEntity(PrintTemplate entity) {
        return repository.save(entity);
    }

    @Override
    protected PrintTemplateResponse toResponse(PrintTemplate entity) {
        return printTemplateMapper.toResponse(entity);
    }
}
