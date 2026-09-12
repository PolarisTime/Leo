package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.CrudOperationLogger;
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

@Service
public class PrintTemplateService {

    private static final String SYNC_MODE_MANUAL = "MANUAL";
    private static final String SYNC_MODE_FILE = "FILE";

    private final CrudOperationLogger operationLogger = CrudOperationLogger.forOwner(PrintTemplateService.class);
    private final SnowflakeIdGenerator idGenerator;
    private final PrintTemplateRepository repository;
    private final PrintTemplateMapper printTemplateMapper;
    private final PrintTemplateRequestNormalizer requestNormalizer;
    private final PrintTemplateJsonUploadReader jsonUploadReader;

    public PrintTemplateService(PrintTemplateRepository repository,
                                SnowflakeIdGenerator idGenerator,
                                PrintTemplateMapper printTemplateMapper,
                                PrintTemplateRequestNormalizer requestNormalizer,
                                PrintTemplateJsonUploadReader jsonUploadReader) {
        this.idGenerator = idGenerator;
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

    @Transactional
    public PrintTemplateResponse create(PrintTemplateRequest request) {
        PrintTemplate entity = new PrintTemplate();
        long entityId = idGenerator.nextId();
        entity.setId(entityId);
        PrintTemplateRequest normalized = normalizeCreateRequest(request, entityId);
        validateCreate(normalized);
        apply(entity, normalized);
        PrintTemplate saved = repository.save(entity);
        operationLogger.created(entity, entityId);
        return toResponse(saved);
    }

    @Transactional
    public PrintTemplateResponse update(Long id, PrintTemplateRequest request) {
        PrintTemplate entity = requireEntity(id);
        PrintTemplateRequest normalized = normalizeUpdateRequest(entity, request);
        validateUpdate(entity, normalized);
        apply(entity, normalized);
        PrintTemplate saved = repository.save(entity);
        operationLogger.updated(entity, id);
        return toResponse(saved);
    }

    @Transactional
    public PrintTemplateResponse uploadJson(Long id, MultipartFile file) {
        PrintTemplate template = requireEntity(id);
        if (SYNC_MODE_FILE.equals(template.getSyncMode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "文件托管模板不支持上传 JSON，请先复制为手动模板");
        }
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
        return toResponse(repository.save(template));
    }

    @Transactional
    public void delete(Long id) {
        PrintTemplate entity = requireEntity(id);
        entity.setDeletedFlag(true);
        repository.save(entity);
        operationLogger.deleted(entity, id);
    }

    private void validateCreate(PrintTemplateRequest request) {
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
        if (repository.existsByBillTypeAndTemplateCodeAndDeletedFlagFalse(billType, templateCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一单据下已存在同编码打印模板");
        }
    }

    private void validateUpdate(PrintTemplate entity, PrintTemplateRequest request) {
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
        boolean duplicatedCode = repository.existsByBillTypeAndTemplateCodeAndDeletedFlagFalse(
                billType,
                templateCode
        ) && !(
                entity.getBillType().equals(billType)
                        && Objects.equals(entity.getTemplateCode(), templateCode)
        );
        if (duplicatedCode) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一单据下已存在同编码打印模板");
        }
    }

    private PrintTemplateRequest normalizeCreateRequest(PrintTemplateRequest request, long entityId) {
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

    private PrintTemplateRequest normalizeUpdateRequest(PrintTemplate entity, PrintTemplateRequest request) {
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

    private void apply(PrintTemplate entity, PrintTemplateRequest request) {
        requestNormalizer.apply(entity, request);
    }

    private PrintTemplateResponse toResponse(PrintTemplate entity) {
        return printTemplateMapper.toResponse(entity);
    }

    private PrintTemplate requireEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "打印模板不存在"));
    }
}
