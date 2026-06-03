package com.leo.erp.master.project.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectTest {

    @Test
    void shouldSetAndGetAllFields() {
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

        assertThat(project.getId()).isEqualTo(1L);
        assertThat(project.getProjectCode()).isEqualTo("P001");
        assertThat(project.getProjectName()).isEqualTo("项目A");
        assertThat(project.getProjectNameAbbr()).isEqualTo("XMA");
        assertThat(project.getProjectAddress()).isEqualTo("上海市浦东新区");
        assertThat(project.getProjectManager()).isEqualTo("李四");
        assertThat(project.getCustomerCode()).isEqualTo("C001");
        assertThat(project.getStatus()).isEqualTo("正常");
        assertThat(project.getRemark()).isEqualTo("测试备注");
    }

    @Test
    void shouldReturnNullForUnsetFields() {
        Project project = new Project();
        assertThat(project.getId()).isNull();
        assertThat(project.getProjectCode()).isNull();
        assertThat(project.getProjectManager()).isNull();
    }
}
