package com.leo.erp.contract.sales.mapper;

import com.leo.erp.contract.sales.domain.entity.SalesContract;
import com.leo.erp.contract.sales.web.dto.SalesContractResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface SalesContractMapper {

    @Mapping(target = "items", ignore = true)
    SalesContractResponse toResponse(SalesContract contract);
}
