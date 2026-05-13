package com.leo.erp.logistics.bill.mapper;

import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FreightBillMapper {

    @Mapping(target = "items", ignore = true)
    FreightBillResponse toResponse(FreightBill bill);
}
