package com.leo.erp.purchase.order.mapper;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PurchaseOrderMapper {

    @Mapping(target = "items", ignore = true)
    PurchaseOrderResponse toResponse(PurchaseOrder order);
}
