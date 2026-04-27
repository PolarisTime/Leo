package com.leo.erp.contract.purchase.mapper;

import com.leo.erp.contract.purchase.domain.entity.PurchaseContract;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PurchaseContractMapper {

    @Mapping(target = "items", ignore = true)
    PurchaseContractResponse toResponse(PurchaseContract contract);
}
