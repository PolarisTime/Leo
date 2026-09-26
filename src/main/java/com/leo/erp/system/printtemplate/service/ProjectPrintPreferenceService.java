package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.domain.entity.ProjectPrintPreference;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import com.leo.erp.system.printtemplate.repository.ProjectPrintPreferenceRepository;
import com.leo.erp.system.printtemplate.web.dto.ProjectPrintPreferenceRequest;
import com.leo.erp.system.printtemplate.web.dto.ProjectPrintPreferenceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 项目打印模板偏好: 按「项目 + 单据类型」记忆上次所选模板, 供下次打印默认回填。
 * <p>无项目或未记忆时由前端回退默认模板({@code is_default}/首个), 本服务不参与默认值推断。</p>
 */
@Service
public class ProjectPrintPreferenceService {

    private final ProjectPrintPreferenceRepository repository;
    private final PrintTemplateRepository printTemplateRepository;
    private final PrintTemplateRequestNormalizer requestNormalizer;
    private final SnowflakeIdGenerator idGenerator;

    public ProjectPrintPreferenceService(ProjectPrintPreferenceRepository repository,
                                         PrintTemplateRepository printTemplateRepository,
                                         PrintTemplateRequestNormalizer requestNormalizer,
                                         SnowflakeIdGenerator idGenerator) {
        this.repository = repository;
        this.printTemplateRepository = printTemplateRepository;
        this.requestNormalizer = requestNormalizer;
        this.idGenerator = idGenerator;
    }

    /** 查询项目在指定单据类型下上次所选模板; 无记忆返回空。 */
    @Transactional(readOnly = true)
    public Optional<ProjectPrintPreferenceResponse> find(Long projectId, String billType) {
        if (projectId == null) {
            return Optional.empty();
        }
        String normalizedBillType = requestNormalizer.normalizeBillType(billType);
        return repository.findByProjectIdAndBillTypeAndDeletedFlagFalse(projectId, normalizedBillType)
                .map(ProjectPrintPreferenceService::toResponse);
    }

    /** 记录(或更新)项目在指定单据类型下本次所选模板。 */
    @Transactional
    public ProjectPrintPreferenceResponse remember(ProjectPrintPreferenceRequest request) {
        Long projectId = request.projectId();
        String billType = requestNormalizer.normalizeBillType(request.billType());
        PrintTemplate template = printTemplateRepository.findByIdAndDeletedFlagFalse(request.templateId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "打印模板不存在或已删除"));

        ProjectPrintPreference preference =
                repository.findByProjectIdAndBillTypeAndDeletedFlagFalse(projectId, billType)
                        .orElseGet(() -> {
                            ProjectPrintPreference created = new ProjectPrintPreference();
                            created.setId(idGenerator.nextId());
                            created.setProjectId(projectId);
                            created.setBillType(billType);
                            return created;
                        });
        preference.setTemplateId(template.getId());
        preference.setTemplateName(template.getTemplateName());
        return toResponse(repository.save(preference));
    }

    private static ProjectPrintPreferenceResponse toResponse(ProjectPrintPreference preference) {
        return new ProjectPrintPreferenceResponse(
                preference.getProjectId(),
                preference.getBillType(),
                preference.getTemplateId(),
                preference.getTemplateName()
        );
    }
}
