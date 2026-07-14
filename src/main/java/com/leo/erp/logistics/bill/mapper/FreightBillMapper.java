package com.leo.erp.logistics.bill.mapper;

import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface FreightBillMapper {

    @Mapping(target = "items", ignore = true)
    @Mapping(target = "sourceSalesOrderId", source = "sourceSalesOrderId")
    @Mapping(target = "sourceSalesOutboundId", ignore = true)
    @Mapping(target = "sourceSalesOutboundNo", ignore = true)
    FreightBillResponse toResponse(FreightBill bill);
}
