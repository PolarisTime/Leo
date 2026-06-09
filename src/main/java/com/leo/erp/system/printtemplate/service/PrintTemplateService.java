package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import com.leo.erp.system.printtemplate.mapper.PrintTemplateMapper;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateRequest;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
    private static final List<Pattern> DANGEROUS_LODOP_PATTERNS = List.of(
            Pattern.compile("\\b(eval|Function)\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(window|document|localStorage|sessionStorage|location|history|navigator)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(fetch|XMLHttpRequest|WebSocket)\\b", Pattern.CASE_INSENSITIVE)
    );
    private static final Set<String> ALLOWED_TEMPLATE_TYPES = Set.of("COORD", "PDF_FORM");
    private static final Set<String> ALLOWED_ENGINES = Set.of("LODOP", "PDF_FORM");
    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final String TEMPLATE_TYPE_PDF_FORM = "PDF_FORM";
    private static final String SYNC_MODE_MANUAL = "MANUAL";
    private static final String SYNC_MODE_FILE = "FILE";
    private static final long MAX_UPLOAD_JSON_BYTES = 1024L * 1024L;
    private static final String DEFAULT_PDF_FORM_LAYOUT = "print-forms/yingjie-a4-remark.layout.json";
    private static final String DEFAULT_PURCHASE_LAYOUT = "print-forms/default-purchase.layout.json";
    private static final String DEFAULT_SALES_LAYOUT = "print-forms/default-sales.layout.json";
    private static final String DEFAULT_LOGISTICS_LAYOUT = "print-forms/default-logistics.layout.json";
    private static final String DEFAULT_STATEMENT_LAYOUT = "print-forms/default-statement.layout.json";

    private final PrintTemplateRepository repository;
    private final PrintTemplateMapper printTemplateMapper;
    private final ModuleCatalog moduleCatalog;
    private final PrintPdfFormTemplateValidator pdfFormTemplateValidator;

    public PrintTemplateService(PrintTemplateRepository repository,
                                SnowflakeIdGenerator idGenerator,
                                PrintTemplateMapper printTemplateMapper,
                                ModuleCatalog moduleCatalog,
                                PrintPdfFormTemplateValidator pdfFormTemplateValidator) {
        super(idGenerator);
        this.repository = repository;
        this.printTemplateMapper = printTemplateMapper;
        this.moduleCatalog = moduleCatalog;
        this.pdfFormTemplateValidator = pdfFormTemplateValidator;
    }

    @Transactional(readOnly = true)
    public List<PrintTemplateResponse> listByBillType(String billType) {
        return repository.findAllByBillTypeAndDeletedFlagFalseOrderByUpdatedAtDescIdDesc(normalizeBillType(billType))
                .stream()
                .map(printTemplateMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public String getBillType(Long id) {
        return requireEntity(id).getBillType();
    }

    @Transactional
    public PrintTemplateResponse uploadJson(Long id, MultipartFile file) {
        PrintTemplate template = requireEntity(id);
        if (!TEMPLATE_TYPE_PDF_FORM.equals(normalizeTemplateType(template.getTemplateType()))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅 PDF_FORM 模板支持上传 JSON");
        }

        String content = readUploadJson(file);
        template.setTemplateHtml(content);
        template.setVersionNo(Math.max(template.getVersionNo() == null ? 1 : template.getVersionNo(), 1) + 1);
        template.setSyncMode(SYNC_MODE_MANUAL);
        template.setSourceRef(null);
        template.setSourceChecksum(null);
        return toSavedResponse(saveEntity(template));
    }

    @Override
    protected void validateCreate(PrintTemplateRequest request) {
        String billType = normalizeBillType(request.billType());
        String templateName = normalizeTemplateName(request.templateName());
        String templateCode = normalizeTemplateCode(request.templateCode());
        if (repository.existsByBillTypeAndTemplateNameAndDeletedFlagFalse(billType, templateName)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一单据已存在同名打印模板");
        }
        if (repository.existsByBillTypeAndTemplateCodeAndDeletedFlagFalse(billType, templateCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一单据已存在同编码打印模板");
        }
    }

    @Override
    protected void validateUpdate(PrintTemplate entity, PrintTemplateRequest request) {
        String billType = normalizeBillType(request.billType());
        String templateName = normalizeTemplateName(request.templateName());
        String templateCode = normalizeTemplateCode(request.templateCode());
        boolean duplicatedName = repository.existsByBillTypeAndTemplateNameAndDeletedFlagFalse(billType, templateName)
                && !(entity.getBillType().equals(billType) && entity.getTemplateName().equals(templateName));
        if (duplicatedName) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一单据已存在同名打印模板");
        }
        boolean duplicatedCode = repository.existsByBillTypeAndTemplateCodeAndDeletedFlagFalse(billType, templateCode)
                && !(entity.getBillType().equals(billType) && Objects.equals(entity.getTemplateCode(), templateCode));
        if (duplicatedCode) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一单据已存在同编码打印模板");
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
    protected PrintTemplateRequest normalizeCreateRequest(PrintTemplateRequest request, long entityId) {
        String templateCode = request.templateCode();
        return new PrintTemplateRequest(
                request.billType(),
                request.templateName(),
                templateCode == null || templateCode.isBlank() ? "TPL_" + entityId : templateCode,
                request.templateHtml(),
                request.templateType(),
                request.engine(),
                request.assetRef(),
                request.versionNo(),
                request.status()
        );
    }

    @Override
    protected PrintTemplateRequest normalizeUpdateRequest(PrintTemplate entity, PrintTemplateRequest request) {
        if (SYNC_MODE_FILE.equals(entity.getSyncMode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "文件托管模板请通过上传 JSON 或修改源文件后重启同步");
        }
        String templateCode = request.templateCode();
        return new PrintTemplateRequest(
                request.billType(),
                request.templateName(),
                templateCode == null || templateCode.isBlank() ? entity.getTemplateCode() : templateCode,
                request.templateHtml(),
                request.templateType(),
                request.engine(),
                request.assetRef(),
                request.versionNo(),
                request.status()
        );
    }

    @Override
    protected String notFoundMessage() {
        return "打印模板不存在";
    }

    @Override
    protected void apply(PrintTemplate entity, PrintTemplateRequest request) {
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
        entity.setVersionNo(normalizeVersionNo(request.versionNo()));
        entity.setStatus(normalizeStatus(request.status()));
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

    private String normalizeTemplateCode(String templateCode) {
        if (templateCode == null || templateCode.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "模板编码不能为空");
        }
        String normalized = templateCode.trim().toUpperCase().replaceAll("[^A-Z0-9_\\-]", "_");
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板编码不合法");
        }
        return normalized;
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
        for (Pattern pattern : DANGEROUS_LODOP_PATTERNS) {
            if (pattern.matcher(templateHtml).find()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板内容包含不允许的脚本或危险标签");
            }
        }
    }

    private String defaultPdfFormTemplate(String billType) {
        try {
            return new ClassPathResource(defaultPdfFormLayout(normalizeBillType(billType)))
                    .getContentAsString(StandardCharsets.UTF_8)
                    .trim();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取 PDF_FORM 默认布局失败");
        }
    }

    private String defaultPdfFormLayout(String billType) {
        if (billType.startsWith("purchase-")) {
            return DEFAULT_PURCHASE_LAYOUT;
        }
        if (billType.startsWith("sales-")) {
            return DEFAULT_SALES_LAYOUT;
        }
        if ("freight-bill".equals(billType)) {
            return DEFAULT_LOGISTICS_LAYOUT;
        }
        if (billType.endsWith("-statement")) {
            return DEFAULT_STATEMENT_LAYOUT;
        }
        return DEFAULT_PDF_FORM_LAYOUT;
    }

    private String normalizeTemplateType(String templateType) {
        if (templateType == null || templateType.isBlank()) {
            return "COORD";
        }
        String normalized = templateType.trim().toUpperCase();
        if (!ALLOWED_TEMPLATE_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板类型仅支持 COORD 或 PDF_FORM");
        }
        return normalized;
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

    private String readUploadJson(MultipartFile file) {
        validateUploadFile(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取上传 JSON 文件失败");
        }
        if (bytes.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传 JSON 文件不能为空");
        }
        if (bytes.length > MAX_UPLOAD_JSON_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传 JSON 文件不能超过 1MB");
        }

        String content = decodeUtf8(bytes).trim();
        content = stripUtf8Bom(content).trim();
        if (content.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传 JSON 文件不能为空");
        }
        pdfFormTemplateValidator.validate(content);
        return content;
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传 JSON 文件不能为空");
        }
        if (file.getSize() > MAX_UPLOAD_JSON_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传 JSON 文件不能超过 1MB");
        }
        validateJsonFilename(file.getOriginalFilename());
    }

    private void validateJsonFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请上传 JSON 文件");
        }
        String filename = simpleFilename(originalFilename);
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请上传 JSON 文件");
        }
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传 JSON 文件必须使用 UTF-8 编码");
        }
    }

    private String stripUtf8Bom(String content) {
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            return content.substring(1);
        }
        return content;
    }

    private String simpleFilename(String originalFilename) {
        String normalized = originalFilename.replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
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
