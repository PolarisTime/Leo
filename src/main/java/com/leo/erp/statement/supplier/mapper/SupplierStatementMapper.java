package com.leo.erp.statement.supplier.mapper;

import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SupplierStatementMapper {

    @Mapping(target = "items", ignore = true)
    SupplierStatementResponse toResponse(SupplierStatement statement);
}
