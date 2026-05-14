package com.leo.erp.contract.purchase.mapper;

import com.leo.erp.contract.purchase.domain.entity.PurchaseContract;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface PurchaseContractMapper {

    @Mapping(target = "items", ignore = true)
    PurchaseContractResponse toResponse(PurchaseContract contract);
}
