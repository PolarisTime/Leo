package com.leo.erp.finance.projectar.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.finance.projectar.repository.ProjectArQueryRepository;
import com.leo.erp.finance.projectar.web.dto.ProjectArDetailRowResponse;
import com.leo.erp.finance.projectar.web.dto.ProjectArSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectArServiceTest {

    @Test
    void pageSummaryShouldDelegateToRepository() {
        ProjectArQueryRepository repository = mock(ProjectArQueryRepository.class);
        Page<ProjectArSummaryResponse> expectedPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(repository.pageSummary(any(), eq("keyword"), eq(1L))).thenReturn(expectedPage);

        ProjectArService service = new ProjectArService(repository);

        Page<ProjectArSummaryResponse> result = service.pageSummary(new PageQuery(1, 10, null, null), "keyword", 1L);

        assertThat(result).isEqualTo(expectedPage);
        verify(repository).pageSummary(any(), eq("keyword"), eq(1L));
    }

    @Test
    void pageUnreconciledShouldDelegateToRepository() {
        ProjectArQueryRepository repository = mock(ProjectArQueryRepository.class);
        Page<ProjectArDetailRowResponse> expectedPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(repository.pageUnreconciled(eq(1L), any())).thenReturn(expectedPage);

        ProjectArService service = new ProjectArService(repository);

        Page<ProjectArDetailRowResponse> result = service.pageUnreconciled(1L, new PageQuery(1, 10, null, null));

        assertThat(result).isEqualTo(expectedPage);
    }

    @Test
    void pageReconciledShouldDelegateToRepository() {
        ProjectArQueryRepository repository = mock(ProjectArQueryRepository.class);
        Page<ProjectArDetailRowResponse> expectedPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(repository.pageReconciled(eq(1L), any())).thenReturn(expectedPage);

        ProjectArService service = new ProjectArService(repository);

        Page<ProjectArDetailRowResponse> result = service.pageReconciled(1L, new PageQuery(1, 10, null, null));

        assertThat(result).isEqualTo(expectedPage);
    }
}
