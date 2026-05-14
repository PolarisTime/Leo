package com.leo.erp.statement.supplier.mapper;

import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface SupplierStatementMapper {

    @Mapping(target = "items", ignore = true)
    SupplierStatementResponse toResponse(SupplierStatement statement);
}
