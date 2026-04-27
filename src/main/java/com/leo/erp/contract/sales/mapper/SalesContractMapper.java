package com.leo.erp.contract.sales.mapper;

import com.leo.erp.contract.sales.domain.entity.SalesContract;
import com.leo.erp.contract.sales.web.dto.SalesContractResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SalesContractMapper {

    @Mapping(target = "items", ignore = true)
    SalesContractResponse toResponse(SalesContract contract);
}
