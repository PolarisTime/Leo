package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import com.leo.erp.system.printtemplate.mapper.PrintTemplateMapper;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateRequest;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class PrintTemplateService extends AbstractCrudService<PrintTemplate, PrintTemplateRequest, PrintTemplateResponse> {

    private static final String SYNC_MODE_MANUAL = "MANUAL";
    private static final String SYNC_MODE_FILE = "FILE";

    private final PrintTemplateRepository repository;
    private final PrintTemplateMapper printTemplateMapper;
    private final PrintTemplateRequestNormalizer requestNormalizer;
    private final PrintTemplateJsonUploadReader jsonUploadReader;

    public PrintTemplateService(PrintTemplateRepository repository,
                                SnowflakeIdGenerator idGenerator,
                                PrintTemplateMapper printTemplateMapper,
                                PrintTemplateRequestNormalizer requestNormalizer,
                                PrintTemplateJsonUploadReader jsonUploadReader) {
        super(idGenerator);
        this.repository = repository;
        this.printTemplateMapper = printTemplateMapper;
        this.requestNormalizer = requestNormalizer;
        this.jsonUploadReader = jsonUploadReader;
    }

    @Transactional(readOnly = true)
    public List<PrintTemplateResponse> listByBillType(String billType) {
        return repository.findAllByBillTypeAndDeletedFlagFalseOrderByUpdatedAtDescIdDesc(
                        requestNormalizer.normalizeBillType(billType))
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
        if (!PrintTemplateRequestNormalizer.TEMPLATE_TYPE_PDF_FORM.equals(
                requestNormalizer.normalizeTemplateType(template.getTemplateType()))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅 PDF_FORM 模板支持上传 JSON");
        }

        String content = jsonUploadReader.read(file);
        template.setTemplateHtml(content);
        template.setVersionNo(Math.max(template.getVersionNo() == null ? 1 : template.getVersionNo(), 1) + 1);
        template.setSyncMode(SYNC_MODE_MANUAL);
        template.setSourceRef(null);
        template.setSourceChecksum(null);
        return toSavedResponse(saveEntity(template));
    }

    @Override
    protected void validateCreate(PrintTemplateRequest request) {
        String billType = requestNormalizer.normalizeBillType(request.billType());
        String templateName = requestNormalizer.normalizeTemplateName(request.templateName());
        String templateCode = requestNormalizer.normalizeTemplateCode(request.templateCode());
        Long settlementCompanyId = request.settlementCompanyId();
        if (repository.existsByBillTypeAndSettlementCompanyIdAndTemplateNameAndDeletedFlagFalse(
                billType,
                settlementCompanyId,
                templateName
        )) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一单据和结算主体下已存在同名打印模板");
        }
        if (repository.existsByBillTypeAndSettlementCompanyIdAndTemplateCodeAndDeletedFlagFalse(
                billType,
                settlementCompanyId,
                templateCode
        )) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一单据和结算主体下已存在同编码打印模板");
        }
    }

    @Override
    protected void validateUpdate(PrintTemplate entity, PrintTemplateRequest request) {
        String billType = requestNormalizer.normalizeBillType(request.billType());
        String templateName = requestNormalizer.normalizeTemplateName(request.templateName());
        String templateCode = requestNormalizer.normalizeTemplateCode(request.templateCode());
        Long settlementCompanyId = request.settlementCompanyId();
        boolean duplicatedName = repository.existsByBillTypeAndSettlementCompanyIdAndTemplateNameAndDeletedFlagFalse(
                billType,
                settlementCompanyId,
                templateName
        ) && !(
                entity.getBillType().equals(billType)
                        && Objects.equals(entity.getSettlementCompanyId(), settlementCompanyId)
                        && entity.getTemplateName().equals(templateName)
        );
        if (duplicatedName) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一单据和结算主体下已存在同名打印模板");
        }
        boolean duplicatedCode = repository.existsByBillTypeAndSettlementCompanyIdAndTemplateCodeAndDeletedFlagFalse(
                billType,
                settlementCompanyId,
                templateCode
        ) && !(
                entity.getBillType().equals(billType)
                        && Objects.equals(entity.getSettlementCompanyId(), settlementCompanyId)
                        && Objects.equals(entity.getTemplateCode(), templateCode)
        );
        if (duplicatedCode) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一单据和结算主体下已存在同编码打印模板");
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
        PrintTemplateRequestNormalizer.SettlementCompanySnapshot settlementCompany =
                requestNormalizer.normalizeSettlementCompany(request.settlementCompanyId());
        return new PrintTemplateRequest(
                request.billType(),
                request.templateName(),
                templateCode == null || templateCode.isBlank() ? "TPL_" + entityId : templateCode,
                request.templateHtml(),
                request.templateType(),
                request.engine(),
                request.assetRef(),
                settlementCompany.id(),
                settlementCompany.name(),
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
        PrintTemplateRequestNormalizer.SettlementCompanySnapshot settlementCompany =
                requestNormalizer.normalizeSettlementCompany(request.settlementCompanyId());
        return new PrintTemplateRequest(
                request.billType(),
                request.templateName(),
                templateCode == null || templateCode.isBlank() ? entity.getTemplateCode() : templateCode,
                request.templateHtml(),
                request.templateType(),
                request.engine(),
                request.assetRef(),
                settlementCompany.id(),
                settlementCompany.name(),
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
        requestNormalizer.apply(entity, request);
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
