package com.leo.erp.system.operationlog.mapper;

import com.leo.erp.system.operationlog.domain.entity.OperationLog;
import com.leo.erp.system.operationlog.web.dto.OperationLogResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OperationLogMapper {

    OperationLogResponse toResponse(OperationLog entity);
}
