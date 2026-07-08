package com.leo.erp.purchase.order.mapper;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface PurchaseOrderMapper {

    @Mapping(target = "items", ignore = true)
    @Mapping(target = "chargeItems", ignore = true)
    @Mapping(target = "totalChargeAmount", ignore = true)
    @Mapping(target = "payableAmount", ignore = true)
    PurchaseOrderResponse toResponse(PurchaseOrder order);
}
