package com.leo.erp.sales.outbound.mapper;

import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SalesOutboundMapper {

    @Mapping(target = "items", ignore = true)
    SalesOutboundResponse toResponse(SalesOutbound outbound);
}
