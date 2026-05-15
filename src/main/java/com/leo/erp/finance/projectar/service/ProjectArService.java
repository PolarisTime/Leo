package com.leo.erp.finance.projectar.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.finance.projectar.repository.ProjectArQueryRepository;
import com.leo.erp.finance.projectar.web.dto.ProjectArDetailRowResponse;
import com.leo.erp.finance.projectar.web.dto.ProjectArSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectArService {

    private final ProjectArQueryRepository queryRepository;

    public ProjectArService(ProjectArQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProjectArSummaryResponse> pageSummary(PageQuery query, String keyword, Long projectId) {
        return queryRepository.pageSummary(query, keyword, projectId);
    }

    @Transactional(readOnly = true)
    public Page<ProjectArDetailRowResponse> pageUnreconciled(Long projectId, PageQuery query) {
        return queryRepository.pageUnreconciled(projectId, query);
    }

    @Transactional(readOnly = true)
    public Page<ProjectArDetailRowResponse> pageReconciled(Long projectId, PageQuery query) {
        return queryRepository.pageReconciled(projectId, query);
    }
}
