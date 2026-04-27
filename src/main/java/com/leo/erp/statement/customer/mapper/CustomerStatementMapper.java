package com.leo.erp.statement.customer.mapper;

import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CustomerStatementMapper {

    @Mapping(target = "items", ignore = true)
    CustomerStatementResponse toResponse(CustomerStatement statement);
}
