package com.leo.erp.master.project.mapper;

import com.leo.erp.common.mapper.StrictMapperConfig;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.web.dto.ProjectResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface ProjectMapper {

    /**
     * 允许品牌/材质在库内是逗号分隔文本, 对外统一拆成列表(空值 → 空列表)。
     * <p>
     * 表达式里使用全限定名, 避免依赖 MapStruct 对表达式类型的隐式导入猜测。
     */
    @Mapping(target = "allowedBrands",
            expression = "java(com.leo.erp.common.support.CsvValues.split(project.getAllowedBrands()))")
    @Mapping(target = "allowedMaterials",
            expression = "java(com.leo.erp.common.support.CsvValues.split(project.getAllowedMaterials()))")
    ProjectResponse toResponse(Project project);
}
