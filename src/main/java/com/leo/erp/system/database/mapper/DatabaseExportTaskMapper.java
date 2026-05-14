package com.leo.erp.system.database.mapper;

import com.leo.erp.system.database.domain.entity.DatabaseExportTask;
import com.leo.erp.system.database.web.dto.DatabaseExportTaskResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface DatabaseExportTaskMapper {

    @Mapping(target = "downloadUrl", ignore = true)
    DatabaseExportTaskResponse toResponse(DatabaseExportTask task);
}
