package com.leo.erp.purchase.inbound.mapper;

import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PurchaseInboundMapper {

    @Mapping(target = "items", ignore = true)
    PurchaseInboundResponse toResponse(PurchaseInbound inbound);
}
