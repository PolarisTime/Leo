package com.leo.erp.purchase.inbound.mapper;

import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface PurchaseInboundMapper {

    @Mapping(target = "items", ignore = true)
    @Mapping(target = "totalWeighWeightTon", ignore = true)
    @Mapping(target = "totalWeightAdjustmentTon", ignore = true)
    PurchaseInboundResponse toResponse(PurchaseInbound inbound);
}
