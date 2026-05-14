package com.leo.erp.statement.customer.mapper;

import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface CustomerStatementMapper {

    @Mapping(target = "items", ignore = true)
    CustomerStatementResponse toResponse(CustomerStatement statement);
}
