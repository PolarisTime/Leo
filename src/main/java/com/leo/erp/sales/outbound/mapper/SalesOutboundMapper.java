package com.leo.erp.sales.outbound.mapper;

import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface SalesOutboundMapper {

    @Mapping(target = "items", ignore = true)
    // projectNameAbbr 由查询侧 ProjectNameAbbrResolver 批量回填, 映射层不参与。
    @Mapping(target = "projectNameAbbr", ignore = true)
    SalesOutboundResponse toResponse(SalesOutbound outbound);
}
