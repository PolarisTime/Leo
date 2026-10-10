package com.leo.erp.master.project.mapper;

import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.web.dto.ProjectResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目响应映射回归: 库内逗号分隔文本必须拆成列表, 供销售订单导入时做品牌/材质匹配预警。
 */
class ProjectMapperTest {

    private final ProjectMapper mapper = new ProjectMapperImpl();

    @Test
    void toResponse_splitsAllowedSpec() {
        Project project = new Project();
        project.setId(1L);
        project.setProjectCode("P001");
        project.setProjectName("项目A");
        project.setCustomerCode("C007");
        project.setStatus("正常");
        project.setAllowedBrands(" 沙钢 ,永钢");
        project.setAllowedMaterials("HRB400E");

        ProjectResponse response = mapper.toResponse(project);

        assertThat(response.allowedBrands()).containsExactly("沙钢", "永钢");
        assertThat(response.allowedMaterials()).containsExactly("HRB400E");
    }

    @Test
    void toResponse_absentAllowedSpec_returnsEmptyLists() {
        Project project = new Project();
        project.setId(1L);

        ProjectResponse response = mapper.toResponse(project);

        assertThat(response.allowedBrands()).isEmpty();
        assertThat(response.allowedMaterials()).isEmpty();
    }

    @Test
    void toResponse_nullProject_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
