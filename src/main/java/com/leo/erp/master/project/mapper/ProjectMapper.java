package com.leo.erp.master.project.mapper;

import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.web.dto.ProjectResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = StrictMapperConfig.class)
public interface ProjectMapper {

    ProjectResponse toResponse(Project project);
}
