package com.leo.erp.sales.order.mapper;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface SalesOrderMapper {

    @Mapping(target = "items", ignore = true)
    @Mapping(target = "chargeItems", ignore = true)
    @Mapping(target = "deliveredQuantity", ignore = true)
    @Mapping(target = "returnedQuantity", ignore = true)
    @Mapping(target = "deliveredNetQuantity", ignore = true)
    // projectNameAbbr 由查询侧 ProjectNameAbbrResolver 批量回填, 映射层不参与。
    @Mapping(target = "projectNameAbbr", ignore = true)
    SalesOrderResponse toResponse(SalesOrder order);
}
