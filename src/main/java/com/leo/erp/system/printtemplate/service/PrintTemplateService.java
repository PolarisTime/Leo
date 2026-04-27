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

    private static final String DEFAULT_FLAG = "1";
    private static final String NORMAL_FLAG = "0";
    private static final Set<String> ALLOWED_BILL_TYPES = Set.of(
            "purchase-orders", "purchase-inbounds", "sales-orders", "sales-outbounds",
            "freight-bills", "purchase-contracts", "sales-contracts",
            "supplier-statements", "customer-statements", "freight-statements",
            "receipts", "payments", "invoice-receipts", "invoice-issues"
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
        return repository.findAllByBillTypeAndDeletedFlagFalseOrderByIsDefaultDescUpdatedAtDescIdDesc(normalizeBillType(billType))
                .stream()
                .map(printTemplateMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PrintTemplateResponse getDefaultByBillType(String billType) {
        return repository.findFirstByBillTypeAndIsDefaultAndDeletedFlagFalse(normalizeBillType(billType), DEFAULT_FLAG)
                .map(printTemplateMapper::toResponse)
                .orElse(null);
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
        entity.setTemplateHtml(normalizeTemplateHtml(request.templateHtml()));
        entity.setIsDefault(normalizeDefaultFlag(request.isDefault()));
    }

    private void syncDefaultTemplate(String billType, Long currentId, String isDefault) {
        if (!DEFAULT_FLAG.equals(isDefault)) {
            return;
        }

        List<PrintTemplate> templates = repository.findAllByBillTypeAndDeletedFlagFalseOrderByIsDefaultDescUpdatedAtDescIdDesc(billType);
        for (PrintTemplate template : templates) {
            if (!template.getId().equals(currentId) && DEFAULT_FLAG.equals(template.getIsDefault())) {
                template.setIsDefault(NORMAL_FLAG);
                repository.save(template);
            }
        }
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

    private String normalizeTemplateHtml(String templateHtml) {
        if (templateHtml == null || templateHtml.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "模板内容不能为空");
        }
        String normalized = templateHtml.trim();
        validateTemplateContent(normalized);
        return normalized;
    }

    private void validateTemplateContent(String templateHtml) {
        List<Pattern> patterns = isLodopTemplate(templateHtml) ? DANGEROUS_LODOP_PATTERNS : DANGEROUS_HTML_PATTERNS;
        for (Pattern pattern : patterns) {
            if (pattern.matcher(templateHtml).find()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板内容包含不允许的脚本或危险标签");
            }
        }
    }

    private boolean isLodopTemplate(String templateHtml) {
        String[] lines = templateHtml.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.startsWith("LODOP.");
            }
        }
        return false;
    }

    private String normalizeDefaultFlag(String isDefault) {
        if (isDefault == null || isDefault.isBlank()) {
            return NORMAL_FLAG;
        }
        String normalized = isDefault.trim();
        if (!DEFAULT_FLAG.equals(normalized) && !NORMAL_FLAG.equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "默认模板标记不合法");
        }
        return normalized;
    }

    @Override
    protected PrintTemplate saveEntity(PrintTemplate entity) {
        syncDefaultTemplate(entity.getBillType(), entity.getId(), entity.getIsDefault());
        return repository.save(entity);
    }

    @Override
    protected PrintTemplateResponse toResponse(PrintTemplate entity) {
        return printTemplateMapper.toResponse(entity);
    }
}
