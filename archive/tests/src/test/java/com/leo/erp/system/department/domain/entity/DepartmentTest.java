package com.leo.erp.system.department.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DepartmentTest {

    @Test
    void shouldCreateDepartmentWithDefaultValues() {
        Department department = new Department();

        assertThat(department.getSortOrder()).isEqualTo(0);
    }

    @Test
    void shouldSetAndGetAllFields() {
        Department department = new Department();
        department.setId(1L);
        department.setDepartmentCode("DEPT-001");
        department.setDepartmentName("技术部");
        department.setParentId(0L);
        department.setManagerName("张三");
        department.setContactPhone("13800138000");
        department.setSortOrder(10);
        department.setStatus("启用");
        department.setRemark("备注");

        assertThat(department.getId()).isEqualTo(1L);
        assertThat(department.getDepartmentCode()).isEqualTo("DEPT-001");
        assertThat(department.getDepartmentName()).isEqualTo("技术部");
        assertThat(department.getParentId()).isEqualTo(0L);
        assertThat(department.getManagerName()).isEqualTo("张三");
        assertThat(department.getContactPhone()).isEqualTo("13800138000");
        assertThat(department.getSortOrder()).isEqualTo(10);
        assertThat(department.getStatus()).isEqualTo("启用");
        assertThat(department.getRemark()).isEqualTo("备注");
    }

    @Test
    void shouldHandleNullValues() {
        Department department = new Department();
        department.setId(null);
        department.setDepartmentCode(null);
        department.setDepartmentName(null);
        department.setParentId(null);
        department.setManagerName(null);
        department.setContactPhone(null);
        department.setSortOrder(null);
        department.setStatus(null);
        department.setRemark(null);

        assertThat(department.getId()).isNull();
        assertThat(department.getDepartmentCode()).isNull();
        assertThat(department.getDepartmentName()).isNull();
        assertThat(department.getParentId()).isNull();
        assertThat(department.getManagerName()).isNull();
        assertThat(department.getContactPhone()).isNull();
        assertThat(department.getSortOrder()).isNull();
        assertThat(department.getStatus()).isNull();
        assertThat(department.getRemark()).isNull();
    }
}
