package com.leo.erp.sales.order.mapper;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface SalesOrderMapper {

    @Mapping(target = "items", ignore = true)
    SalesOrderResponse toResponse(SalesOrder order);
}
