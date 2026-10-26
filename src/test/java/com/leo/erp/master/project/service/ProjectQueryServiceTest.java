package com.leo.erp.master.project.service;

import com.leo.erp.master.api.ProjectQuery.ProjectSnapshot;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectQueryServiceTest {

    @Mock
    private ProjectRepository repository;

    @InjectMocks
    private ProjectQueryService service;

    @Test
    void findActiveById_shouldIncludeSettlementCompanySnapshot() {
        Project project = new Project();
        project.setId(20L);
        project.setProjectName("项目A");
        project.setProjectNameAbbr("项A");
        project.setCustomerId(10L);
        project.setCustomerCode("CUST001");
        project.setSettlementCompanyId(40L);
        project.setSettlementCompanyName("项目结算公司");
        when(repository.findByIdAndDeletedFlagFalse(20L)).thenReturn(Optional.of(project));

        assertThat(service.findActiveById(20L)).contains(new ProjectSnapshot(
                20L, "项目A", "项A", 10L, "CUST001", 40L, "项目结算公司"
        ));
    }

    @Test
    void findActiveByIds_shouldReturnEmptyMapWithoutQueryingWhenNoUsableId() {
        assertThat(service.findActiveByIds(null)).isEmpty();
        assertThat(service.findActiveByIds(Collections.emptyList())).isEmpty();
        assertThat(service.findActiveByIds(Arrays.asList(null, null))).isEmpty();

        verifyNoInteractions(repository);
    }

    @Test
    void findActiveByIds_shouldQueryOnceWithDistinctIdsAndSkipDeletedProjects() {
        when(repository.findAllById(anyCollection())).thenReturn(List.of(
                project(20L, "项目A", "项A", false),
                project(21L, "项目B", "项B", true)));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> idsCaptor = ArgumentCaptor.forClass(Collection.class);

        Map<Long, ProjectSnapshot> result = service.findActiveByIds(Arrays.asList(20L, 21L, 20L, null));

        verify(repository, times(1)).findAllById(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(20L, 21L);
        assertThat(result).containsOnlyKeys(20L);
        assertThat(result.get(20L)).isEqualTo(new ProjectSnapshot(20L, "项目A", "项A", 10L, "CUST001"));
    }

    @Test
    void findActiveByIds_shouldReturnEmptyMapWhenAllProjectsDeleted() {
        when(repository.findAllById(anyCollection())).thenReturn(List.of(project(20L, "项目A", "项A", true)));

        assertThat(service.findActiveByIds(List.of(20L))).isEmpty();
    }

    private Project project(Long id, String projectName, String abbreviatedName, boolean deleted) {
        Project project = new Project();
        project.setId(id);
        project.setProjectName(projectName);
        project.setProjectNameAbbr(abbreviatedName);
        project.setCustomerId(10L);
        project.setCustomerCode("CUST001");
        project.setDeletedFlag(deleted);
        return project;
    }
}
