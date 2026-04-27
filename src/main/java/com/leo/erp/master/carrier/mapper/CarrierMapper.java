package com.leo.erp.master.carrier.mapper;

import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.web.dto.CarrierResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CarrierMapper {

    CarrierResponse toResponse(Carrier carrier);
}
