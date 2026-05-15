package com.leo.erp.master.project.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.mapper.ProjectMapper;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.project.web.dto.ProjectRequest;
import com.leo.erp.master.project.web.dto.ProjectResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ProjectService extends AbstractCrudService<Project, ProjectRequest, ProjectResponse> {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;

    public ProjectService(SnowflakeIdGenerator snowflakeIdGenerator,
                          ProjectRepository projectRepository,
                          ProjectMapper projectMapper) {
        super(snowflakeIdGenerator);
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> page(PageQuery query, String keyword, String status) {
        Specification<Project> spec = Specs.<Project>notDeleted()
                .and(Specs.keywordLike(keyword,
                        "projectCode", "projectName", "projectNameAbbr",
                        "customerCode", "projectManager"))
                .and(Specs.equalIfPresent("status", status));
        return page(query, spec, projectRepository);
    }

    @Override
    protected void validateCreate(ProjectRequest request) {
        ensureProjectCodeUnique(request.projectCode());
    }

    @Override
    protected void validateUpdate(Project entity, ProjectRequest request) {
        if (!entity.getProjectCode().equals(request.projectCode())) {
            ensureProjectCodeUnique(request.projectCode());
        }
    }

    @Override
    protected Project newEntity() {
        return new Project();
    }

    @Override
    protected void assignId(Project entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<Project> findActiveEntity(Long id) {
        return projectRepository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "项目不存在";
    }

    @Override
    protected void apply(Project entity, ProjectRequest request) {
        entity.setProjectCode(request.projectCode());
        entity.setProjectName(request.projectName());
        entity.setProjectNameAbbr(request.projectNameAbbr());
        entity.setProjectAddress(request.projectAddress());
        entity.setProjectManager(request.projectManager());
        entity.setCustomerCode(request.customerCode());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    @Override
    protected Project saveEntity(Project entity) {
        return projectRepository.save(entity);
    }

    @Override
    protected ProjectResponse toResponse(Project entity) {
        return projectMapper.toResponse(entity);
    }

    private void ensureProjectCodeUnique(String projectCode) {
        if (projectRepository.existsByProjectCodeAndDeletedFlagFalse(projectCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "项目编码已存在");
        }
    }
}
