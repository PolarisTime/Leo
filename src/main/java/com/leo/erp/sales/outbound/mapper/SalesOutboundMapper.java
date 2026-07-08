package com.leo.erp.sales.outbound.mapper;

import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface SalesOutboundMapper {

    @Mapping(target = "items", ignore = true)
    @Mapping(target = "chargeItems", ignore = true)
    @Mapping(target = "totalChargeAmount", ignore = true)
    @Mapping(target = "receivableAmount", ignore = true)
    SalesOutboundResponse toResponse(SalesOutbound outbound);
}
