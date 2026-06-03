package com.leo.erp.master.project.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectResponseTest {

    @Test
    void recordAccessors() {
        ProjectResponse response = new ProjectResponse(
                1L, "P001", "项目A", "PA", "北京市朝阳区", "张三", "C001", "启用", "备注"
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.projectCode()).isEqualTo("P001");
        assertThat(response.projectName()).isEqualTo("项目A");
        assertThat(response.projectNameAbbr()).isEqualTo("PA");
        assertThat(response.projectAddress()).isEqualTo("北京市朝阳区");
        assertThat(response.projectManager()).isEqualTo("张三");
        assertThat(response.customerCode()).isEqualTo("C001");
        assertThat(response.status()).isEqualTo("启用");
        assertThat(response.remark()).isEqualTo("备注");
    }

    @Test
    void recordEquality() {
        ProjectResponse a = new ProjectResponse(
                1L, "P001", "项目A", "PA", "北京市朝阳区", "张三", "C001", "启用", "备注"
        );
        ProjectResponse b = new ProjectResponse(
                1L, "P001", "项目A", "PA", "北京市朝阳区", "张三", "C001", "启用", "备注"
        );

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void recordToString() {
        ProjectResponse response = new ProjectResponse(
                1L, "P001", "项目A", "PA", "北京市朝阳区", "张三", "C001", "启用", "备注"
        );
        assertThat(response.toString()).contains("P001", "项目A");
    }
}