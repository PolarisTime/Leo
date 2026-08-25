package com.leo.erp.master.project.service;

import com.leo.erp.master.api.ProjectQuery.ProjectSnapshot;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
}
