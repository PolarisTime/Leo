package com.leo.erp.system.operationlog.mapper;

import com.leo.erp.system.operationlog.domain.entity.OperationLog;
import com.leo.erp.system.operationlog.web.dto.OperationLogResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = StrictMapperConfig.class)
public interface OperationLogMapper {

    OperationLogResponse toResponse(OperationLog entity);
}
