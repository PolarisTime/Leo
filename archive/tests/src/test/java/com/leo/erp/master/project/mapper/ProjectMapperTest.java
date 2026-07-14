package com.leo.erp.master.project.mapper;

import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.web.dto.ProjectResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectMapperTest {

    private final ProjectMapper mapper = new ProjectMapperImpl();

    @Test
    void shouldMapAllFieldsToResponse() {
        Project project = new Project();
        project.setId(1L);
        project.setProjectCode("P001");
        project.setProjectName("项目A");
        project.setProjectNameAbbr("XMA");
        project.setProjectAddress("上海市浦东新区");
        project.setProjectManager("李四");
        project.setCustomerCode("C001");
        project.setStatus("正常");
        project.setRemark("测试备注");

        ProjectResponse response = mapper.toResponse(project);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.projectCode()).isEqualTo("P001");
        assertThat(response.projectName()).isEqualTo("项目A");
        assertThat(response.projectNameAbbr()).isEqualTo("XMA");
        assertThat(response.projectAddress()).isEqualTo("上海市浦东新区");
        assertThat(response.projectManager()).isEqualTo("李四");
        assertThat(response.customerCode()).isEqualTo("C001");
        assertThat(response.status()).isEqualTo("正常");
        assertThat(response.remark()).isEqualTo("测试备注");
    }

    @Test
    void shouldMapNullFieldsToNull() {
        Project project = new Project();
        project.setId(1L);
        project.setProjectCode("P001");

        ProjectResponse response = mapper.toResponse(project);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.projectCode()).isEqualTo("P001");
        assertThat(response.projectManager()).isNull();
        assertThat(response.remark()).isNull();
    }

    @Test
    void shouldReturnNull_whenEntityIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
